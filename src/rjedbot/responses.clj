(ns rjedbot.responses
  (:require  [ajax.core :refer [POST]]
             [clojure.pprint :as pp]
             [cprop.core :refer [load-config]]))

(def application-id (:app-id (load-config :resource "discord-credentials.edn")))
(defn make-embed
  [url-string]
  {:image {:url url-string}})

(defn POST-string
  [message-token string]
  (println "in POST-string")
  (let
   [url (str "https://discord.com/api/v8/webhooks/" application-id "/" message-token)]
    (POST url
      {:format :json
       :params {:content string}
       :handler pp/pprint})))

(defn POST-single-URL
  [message-token url]
  (let
   [discord-url (str "https://discord.com/api/v8/webhooks/" application-id "/" message-token)]
    (POST discord-url
      {:format :json
       :params {:content url}
       :handler pp/pprint})))

(defn POST-embed
  [message-token urls]
  (let
   [url (str "https://discord.com/api/v8/webhooks/" application-id "/" message-token)]
    (POST url
      {:format :json
       :params {:embeds (into []
                              (map make-embed urls))}
       :handler pp/pprint})))
