(ns rjedbot.server
  "The rjedbot server and related state."
  (:gen-class)
  (:require [caesium.crypto.sign :as sign]
            [caesium.util :as cutil]
            [cprop.core :refer [load-config]]
            [jsonista.core :as j]
            [org.httpkit.server :as h]
            [rjedbot.commands :as commands]))

(def discord-pubkey
  (cutil/unhexify (:key (load-config :resource "discord-credentials.edn"))))

(def server (atom nil))

(def clients_ (atom #{}))

(defn app
  [request]
  (h/as-channel request
                {:on-open (fn [ch] (swap! clients_ conj ch))})
  (let [body-str (slurp (:body request))
        body (j/read-value body-str)
        timestamp (get (:headers request) "x-signature-timestamp")
        signature (cutil/unhexify (get (:headers request) "x-signature-ed25519"))
        to-verify (str timestamp body-str)]
    (doseq [ch @clients_]
      (swap! clients_ disj ch)
      (try
        (sign/verify signature to-verify discord-pubkey)
        (case (get body "type")
          1 (h/send! ch
                     {:status 200
                      :headers {"Content-Type" "application/json"}
                      :body (j/write-value-as-string {:type 1})})
          2 (commands/command-handler body ch))
        (catch RuntimeException e
          (println (.getMessage e))
          (h/send! ch
                   {:status 401
                    :body "invalid request signature"}))))))

(defn start []
  (reset! server (h/run-server #'app {:port 44227}))
  (println "Started rjedbot server running on port 44227 at" (str (new java.util.Date))))

(defn -main []
  (start))
