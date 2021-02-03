(ns rjedbot.log
  (:require [clojure.core.async :refer [chan thread <!! close! >!!]]))

;;;;; Logging Handler ;;;;;

(def log-chan (chan))

(thread
  (loop []
    (when-let [v (<!! log-chan)]
      (println v)
      (recur)))
  (println "Log Closed"))

(defn log [msg]
  (>!! log-chan msg))
