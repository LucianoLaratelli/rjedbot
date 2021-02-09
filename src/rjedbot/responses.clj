(ns rjedbot.responses
  "The responses namespace encompasses the functions that send content back to
  discord to answer a user's interaction."
  (:require [ajax.core :refer [POST]]
            [clojure.pprint :as pp]
            [clojure.string :as s]
            [cprop.core :refer [load-config]]
            [rjedbot.util :as util]
            [clojure.core.async :as a]))

(def application-id (:app-id (load-config :resource "discord-credentials.edn")))

(def discord-webhook-url
  (str "https://discord.com/api/v8/webhooks/" application-id "/"))

(defn POST-string
  "Send a followup message that consists of a single string."
  [message-token string chan]
  (util/log (str "POSTing single string using token " message-token " at " (new java.util.Date)))
  (let
   [url (str discord-webhook-url message-token)]
    (POST url
      {:format :json
       :params {:content string}
       :handler #(a/>!! chan %)
       :error-handler #(a/>!! chan %)})
    chan))

(defn POST-embed
  "Send a followup message that contains an embed."
  [message-token urls chan]
  (util/log (str "POSTing embed using token " message-token " at " (new java.util.Date)))
  (let
   [url (str discord-webhook-url message-token)]
    (POST url
      {:format :json
       :params {:embeds (into []
                              (map (fn [url] {:image {:url url}}) urls))}
       :handler #(a/>!! chan %)
       :error-handler #(a/>!! chan %)})

    chan))

(defn handle-rate-limited-call
  "If we make a call that can be rate limited, respect the rate limit and then retry."
  [f & args]
  (a/go-loop [f f args args]
    (let [response (a/<! (apply f args))]
      (when (= 429 (:status response))
        ;; discord sends us a time in seconds; a/timeout expects milliseconds
        (a/timeout (* 1000 (get-in response [:response "retry_after"])))
        (recur f args)))))

(defn post-handler
  "Handle post URLs according to their extensions."
  ;; Depending on the extension of the content, some posts must be embedded while
  ;; other must be sent as raw links, yet others can not be sent at all. The goal
  ;; of rjedbot is to send messages that are displayed inline in a discord chat,
  ;; so when discord fails to properly render something inline in some context,
  ;; we must either try something different or not send the content at all. A
  ;; context here means either an embed or sending a raw link.
  ;;
  ;; One notable issue is that gifs won't play in embeds, for example.
  ;;
  ;; From simple experimentation, I've determined the following:
  ;;     no extension, gifv, gif => raw link (sent after the embed)
  ;;     png, jpg, jpeg => joined together into a single embed
  ;;     mp4 => skipped
  [posts token]
  (println "Handling these posts:")
  (pp/pprint posts)
  (let [embed-extensions #{"png" "jpg" "jpeg"}
        ;; raw-msg-extensions #{"" "gifv" "gif"}
        skipped-extensions #{"mp4"}
        labelled-posts (map (fn [post]
                              (let [extension
                                    (last (s/split (apply str (take-last 5 post)) #"\."))]
                                (cond
                                  (contains? skipped-extensions extension) {:skip post}
                                  (contains? embed-extensions extension) {:embed post}
                                  :else {:raw post})))
                            posts)
        total (count posts)
        skipped (count (util/get-inner-values-matching-key labelled-posts :skip))
        embeddable (util/get-inner-values-matching-key labelled-posts :embed)
        to-raw (util/get-inner-values-matching-key labelled-posts :raw)
        results-chan (a/chan)]
    (when (> skipped 0)
      (handle-rate-limited-call POST-string token
                                (str "Skipped " skipped " of " total " requested posts due to invalid format.")))
    (handle-rate-limited-call POST-embed token embeddable results-chan)
    (doseq [r to-raw]
      (handle-rate-limited-call POST-string token r results-chan))))
