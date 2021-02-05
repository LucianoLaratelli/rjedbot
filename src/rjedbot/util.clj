(ns rjedbot.util
  "Utility functions for rjedbot."
  (:require [clojure.java.io :as io]))

(defn write-to-resource
  "Write content to resources/filename"
  [filename content]
  (with-open [w (io/writer (io/resource filename))]
    (.write w content)))

(defn contains-key?
  "Returns if map m contains key k."
  [m k]
  (if (get m k)
    true
    false))

(defn get-value-from-ith-map
  "Return the value for the key k in the ith map in a vec of maps."
  ;; When m is
  ;; [{"name" "subreddit", "value" "cats"} {"name" "section", "value" "top"}],
  ;; calling this function with m, 0, and "value" returns "cats"
  [m i k]
  (get-in m [i k]))

(defn get-inner-values-matching-key
  "Get any values that match k from s, a list of maps."
  ;; When s is
  ;; ({:a 'foo'} {:b 'bar'} {:c 'baz'} {:a 'quux'})
  ;; and we call this function with :a, we return 'foo' and 'quux'
  [s k]
  (map k (filter #(contains-key? % k) s)))
