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
  [message-token url]
  (log (str "POSTing single URL using token " message-token " at " (new java.util.Date)))
  (let
   [discord-url (str discord-webhook-url message-token)]
    (POST discord-url
      {:format :json
       :params {:content url}
       :handler pp/pprint
       :error-handler pp/pprint})))

(defn make-embed
  [url-string]
  {:image {:url url-string}})

(defn POST-embed
  [message-token urls]
  (log (str "POSTing embed using token " message-token " at " (new java.util.Date)))
  (let
   [url (str discord-webhook-url message-token)]
    (POST url
      {:format :json
       :params {:embeds (into []
                              (map make-embed urls))}
       :handler pp/pprint
       :error-handler pp/pprint})))

(defn GET-guild-owner
  [guild-id]
  (let
   [url (str "https://discord.com/api/v8/guilds/" guild-id)]
    (GET url
      {:headers
       {"authorization"
        "Bot Nzk5MTIxOTU1NjQ2MjEwMDc4.X_--UQ.62jv5RML4QZxZ4DFLHczza5PRow"}})))

