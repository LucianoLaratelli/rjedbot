(ns rjedbot.config
  "This namespace deals with reading from and writing to rjedbot's internal configuration file."
  (:require [clojure.java.io :as io]
            [rjedbot.config :as conf]
            [rjedbot.log :refer [log]]
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
  (log (str "handling " amount " surprises at " (new java.util.Date)))
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
