(ns rjedbot.server
  (:require [caesium.crypto.sign :as sign]
            [caesium.util :as cutil]
            [cprop.core :refer [load-config]]
            [jsonista.core :as j]
            [ring.adapter.jetty :as jetty]
            [rjedbot.commands :as commands]))

(def discord-pubkey
  (cutil/unhexify (:key (load-config :resource "discord-pubkey.edn"))))

(def server (atom nil))

(defn app
  [thing]
  (let [body-str (slurp (:body thing))
        body (j/read-value body-str)
        timestamp (get (:headers thing) "x-signature-timestamp")
        signature (cutil/unhexify (get (:headers thing) "x-signature-ed25519"))
        to-verify (str timestamp body-str)]
    (try
      (sign/verify signature to-verify discord-pubkey)
      (case (get body "type")
        1 {:status 200
           :headers {"Content-Type" "application/json"}
           :body (j/write-value-as-string {:type 1})}
        2 (commands/command-handler body))
      (catch RuntimeException e
        {:status 401
         :body "invalid request signature"}))))

(defn start []
  (reset! server (jetty/run-jetty #'app {:port 44227, :join? false}))
  (println "server running in port 44227"))

(defn stop []
  (.stop @server))
