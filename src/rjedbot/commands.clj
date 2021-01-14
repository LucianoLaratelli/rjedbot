(ns rjedbot.commands
  (:require [jsonista.core :as j]
            [rjedbot.reddit :as reddit]))

(defn make-string-response
  [string]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (j/write-value-as-string {:type 4 :data {:content string}})})

(defn command-handler
  [body]
  (case (get-in body ["data" "name"])
    "lofi" (make-string-response "p!play https://www.youtube.com/watch?v=5qap5aO4i9A")
    "blep" (make-string-response "hello")
    "post" (make-string-response (first (reddit/get-posts (get-in body ["data" "options" 0 "value"]))))))
