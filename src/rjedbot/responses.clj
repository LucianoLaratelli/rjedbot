(ns rjedbot.responses
  (:require  [ajax.core :refer [POST GET]]
             [cprop.core :refer [load-config]]
             [clojure.pprint :as pp]
             [rjedbot.log :refer [log]]
             [clojure.core.async :as a]))

(def application-id (:app-id (load-config :resource "discord-credentials.edn")))

(def discord-webhook-url
  (str "https://discord.com/api/v8/webhooks/" application-id "/"))

(defn POST-string
  [message-token string]
  (log (str "POSTing single string using token " message-token " at " (new java.util.Date)))
  (let
   [url (str discord-webhook-url message-token)]
    (POST url
      {:format :json
       :params {:content string}
       :handler pp/pprint
       :error-handler pp/pprint})))

(defn POST-single-URL
  [message-token url chan]
  (log (str "POSTing single URL using token " message-token " at " (new java.util.Date)))
  (let
   [discord-url (str discord-webhook-url message-token)]
    (POST discord-url
      {:format :json
       :params {:content url}
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

(defn GET-guild-owner
  [guild-id]
  (let
   [url (str "https://discord.com/api/v8/guilds/" guild-id)]
    (GET url
      {:headers
       {"authorization"
        "insert discord token here"}})))

(defn handle-rate-limited-call
  "If we make a call that can be rate limited, respect the rate limit and then retry."
  [f & args]
  (a/go
    (loop [func f
           argss args]
      (let [response (a/<! (apply func argss))]
        (pp/pprint response)
        (when (= 429 (:status response))
          (pp/pprint (get-in response [:response "retry_after"]))
          (a/timeout (* 1000 (get-in response [:response "retry_after"])))
          (recur f args))))))
