(ns rjedbot.guilds
  (:require [ajax.core :refer [GET]]
            [clojure.pprint :as pp]
            [clojure.string :as s]
            [cprop.core :refer [load-config]]
            [rjedbot.config :as conf]
            [rjedbot.responses :as r]
            [rjedbot.util :as u]))

(def discord-bot-token
  (str "Bot " (:token (load-config :resource "discord-credentials.edn"))))

(defn GET-guild-owner
  [guild-id]
  (let
   [url (str "https://discord.com/api/v8/guilds/" guild-id)]
    (GET url
      {:headers
       {"authorization" discord-bot-token}})))

(defn send-favorites
  [guild token chan]
  (let [favorites (:favorites (get @conf/config guild))
        num_favs (count favorites)]
    (if (> num_favs 0)
      (let
       [message "These are the favorite subreddits for  your server:\n"
        message (str message (s/join "\n" favorites))]
        (r/handle-rate-limited-call r/POST-string token message chan))
      (r/handle-rate-limited-call r/POST-string token "There are no server favorites. Use `/add-favorite` to add some!" chan))))

(defn remove-member
  [s member]
  (into #{} (filterv #(not (= member %)) s)))

(defn remove-favorite
  [guild favorite token chan]
  (let [favorites (:favorites (get @conf/config guild))
        num_favs (count favorites)]
    (if (> num_favs 0)
      (let [in [guild :favorites]
            favorite (s/lower-case (s/trim favorite))
            favs (get-in @conf/config in)]
        (if (contains? favs favorite)
          (do
            pp/pprint favorites
            (swap! conf/config #(assoc-in % in (remove-member (get-in % in) favorite)))
            (u/write-to-resource "config.edn" (.toString @conf/config))
            pp/pprint favorites
            (r/handle-rate-limited-call r/POST-string token
                                        (str "Removed " favorite " from server favorites list.") chan))
          (r/handle-rate-limited-call r/POST-string token "That subreddit is not in the favorites list." chan)))
      (r/handle-rate-limited-call r/POST-string token "There are no server favorites. Use `/add-favorite` to add some!" chan))))

(defn add-favorite
  "Add a subreddit to the favorites list."
  [guild favorite token chan]
  (let [in [guild :favorites]
        favs (get-in @conf/config in)]
    (if (contains? favs favorite)
      (r/handle-rate-limited-call r/POST-string token "That subreddit is already in the favorites list." chan)
      (do
        (swap! conf/config #(assoc-in % in (conj (get-in % in) (s/lower-case favorite))))
        (u/write-to-resource "config.edn" (.toString @conf/config))
        (r/handle-rate-limited-call r/POST-string token
                                    (str "Added " favorite " to server favorites list. Favorites list is now:\n"
                                         (s/join "\n" (:favorites (get @conf/config guild)))) chan)))))
