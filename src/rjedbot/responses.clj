(ns rjedbot.responses
  (:require [clojure.core.async :as a]
            [cprop.core :refer [load-config]]
            [rjedbot.log :refer [log]]
            [ajax.core :refer [POST]]))

(def application-id (:app-id (load-config :resource "discord-credentials.edn")))

(def discord-webhook-url
  (str "https://discord.com/api/v8/webhooks/" application-id "/"))

(defn POST-string
  [message-token string chan]
  (log (str "POSTing single string using token " message-token " at " (new java.util.Date)))
  (let
   [url (str discord-webhook-url message-token)]
    (POST url
      {:format :json
       :params {:content string}
       :handler #(a/>!! chan %)
       :error-handler #(a/>!! chan %)})
    chan))

(defn make-embed
  [url-string]
  {:image {:url url-string}})

(defn async-POST-embed
  [message-token urls chan]
  (log (str "POSTing embed using token " message-token " at " (new java.util.Date)))
  (let
   [url (str discord-webhook-url message-token)]
    (POST url
      {:format :json
       :params {:embeds (into []
                              (map make-embed urls))}
       :handler #(a/>!! chan %)
       :error-handler #(a/>!! chan %)})

    chan))

(defn handle-rate-limited-call
  "If we make a call that can be rate limited, respect the rate limit and then retry."
  [f & args]
  (a/go
    (loop [func f
           argss args]
      (let [response (a/<! (apply func argss))]
        (when (= 429 (:status response))
          ;; discord sends us a time in seconds; a/timeout expects milliseconds
          (a/timeout (* 1000 (get-in response [:response "retry_after"])))
          (recur f args))))))
