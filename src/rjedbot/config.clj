(ns rjedbot.config
  "This namespace deals with reading from and writing to rjedbot's internal configuration file."
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [rjedbot.config :as conf]
            [rjedbot.responses :as r]
            [rjedbot.util :as u]
            [rjedbot.reddit :as reddit]))

(def config (atom (read-string (slurp (io/resource "config.edn")))))

(def default-max-posts 6)

(def default-guild-data
  {:max-posts 6
   :favorites []})

(defn known-guild?
  [guild-id]
  (contains? @config guild-id))

(defn count-favorites
  [guild]
  (count
   (get-in @config [guild :favorites])))

(defn update-maximum
  [guild token new-max chan]
  (swap! conf/config #(assoc-in % [guild :max-posts] new-max))
  (u/write-to-resource "config.edn" (.toString @conf/config))
  (r/handle-rate-limited-call r/POST-string token "Updated server maximum to " new-max " posts. Enjoy!" chan))

(defn get-favorite-subreddit
  [guild-id]
  (rand-nth (seq (:favorites (get @config guild-id)))))

(defn get-surprises
  "Get a set of reddit posts by grabbing from the server's favorites list."
  [amount guild-id token chan]
  (u/log (str "handling " amount " surprises at " (new java.util.Date)))
  (let [fave-count  (conf/count-favorites guild-id)
        amount (if (nil? amount) 1 amount)]
    (if (= fave-count 0)
      (r/handle-rate-limited-call r/POST-string token "There are no server favorites. Use `/add-favorite` to add some!" chan)
      (do
        (when (< fave-count amount)
          (let [message (str "You asked for " amount " favorites, but this server only has " fave-count
                             " favorites, so you're going to receive fewer posts than you requested.")]
            (r/handle-rate-limited-call r/POST-string token message chan)))
        (flatten (map #(reddit/get-posts %) (set (repeatedly amount #(conf/get-favorite-subreddit guild-id)))))))))

(defn send-favorites
  "Read the guild favorites and send a message with them."
  [guild token chan]
  ;; TODO: eventually we should send a nice embed, kind of how kiran sends the
  ;; ooflist in oofbot.
  (let [favorites (:favorites (get @conf/config guild))
        num_favs (count favorites)]
    (if (> num_favs 0)
      (let
       [message "These are the favorite subreddits for  your server:\n"
        message (str message (s/join "\n" favorites))]
        (r/handle-rate-limited-call r/POST-string token message chan))
      (r/handle-rate-limited-call r/POST-string token "There are no server favorites. Use `/add-favorite` to add some!" chan))))

(defn remove-member
  "Remove a member from a set."
  [s member]
  (into #{} (filterv #(not (= member %)) s)))

(defn remove-favorite
  "Remove a favorite from a guild's list of favorites."
  [guild favorite token chan]
  (let [favorites (:favorites (get @conf/config guild))
        num_favs (count favorites)]
    (if (> num_favs 0)
      (let [in [guild :favorites]
            favorite (s/lower-case (s/trim favorite))
            favs (get-in @conf/config in)]
        (if (contains? favs favorite)
          (do
            (swap! conf/config #(assoc-in % in (remove-member (get-in % in) favorite)))
            (u/write-to-resource "config.edn" (.toString @conf/config))
            (r/handle-rate-limited-call r/POST-string token
                                        (str "Removed " favorite " from server favorites list.") chan))
          (r/handle-rate-limited-call r/POST-string token "That subreddit is not in the favorites list." chan)))
      (r/handle-rate-limited-call r/POST-string token "There are no server favorites. Use `/add-favorite` to add some!" chan))))

(defn add-favorite
  "Add a subreddit to the guild's favorites list."
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
