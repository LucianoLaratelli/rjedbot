(ns rjedbot.guilds
  (:require [ajax.core :refer [GET]]
            [cprop.core :refer [load-config]]))

(def discord-bot-token
  (str "Bot " (:token (load-config :resource "discord-credentials.edn"))))

(defn GET-guild-owner
  [guild-id]
  (let
   [url (str "https://discord.com/api/v8/guilds/" guild-id)]
    (GET url
      {:headers
       {"authorization" discord-bot-token}})))
