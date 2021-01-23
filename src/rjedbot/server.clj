(ns rjedbot.server
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

(defn app
  [request]
  (let [body-str (slurp (:body request))
        body (j/read-value body-str)
        timestamp (get (:headers request) "x-signature-timestamp")
        signature (cutil/unhexify (get (:headers request) "x-signature-ed25519"))
        to-verify (str timestamp body-str)]
    (h/with-channel request channel
      (try
        (sign/verify signature to-verify discord-pubkey)
        (case (get body "type")
          1 (h/send! channel
                     {:status 200
                      :headers {"Content-Type" "application/json"}
                      :body (j/write-value-as-string {:type 1})})
          2 (commands/command-handler body channel))
        (catch RuntimeException e
          (h/send! channel
                   {:status 401
                    :body "invalid request signature"}))))))

(defn start []
  (reset! server (h/run-server #'app {:port 44227}))
  (println "server running in port 44227"))

;; (defn stop []
;;   (.stop @server))

(defn -main []
  (start))
