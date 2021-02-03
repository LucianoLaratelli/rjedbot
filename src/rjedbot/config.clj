(ns rjedbot.config
  "This namespace deals with reading from and writing to rjedbot's internal configuration file."
  (:require [clojure.java.io :as io]))

(def config (atom (read-string (slurp (io/resource "config.edn")))))

(def default-max-posts 6)

(def default-guild-data
  {:max-posts 6
   :favorites []})

(defn known-guild?
  [guild-id]
  (contains? @config guild-id))

(defn get-favorite-subreddit
  [guild-id]
  (rand-nth (:favorites (get @config guild-id))))
