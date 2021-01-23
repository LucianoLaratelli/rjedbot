(ns rjedbot.config
  (:require
   [cprop.core :refer [load-config]]
   [clojure.java.io :as io]))

(def config (atom (read-string (slurp (io/resource "config.edn")))))

(def default-max-posts 6)

(def default-guild-data
  {:max-posts 6})

(defn known-guild?
  [guild-id]
  (contains? @config guild-id))
