(ns rjedbot.util
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

;; Writes data to an edn file
;; thanks kiran
;; https://github.com/kiranshila/oofbot/blob/master/src/oofbot/core.clj
(defn write-edn [data filename]
  (with-open [f (io/writer filename)]
    (binding [*out* f]
      (pr data))))
