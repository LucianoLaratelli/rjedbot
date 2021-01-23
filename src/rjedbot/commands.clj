(ns rjedbot.commands
  (:require [clojure.core.async :as a]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as s]
            [jsonista.core :as j]
            [org.httpkit.server :as h]
            [rjedbot.config :as config]
            [rjedbot.reddit :as reddit]
            [rjedbot.responses :as responses]
            [rjedbot.util :as u]
            [clojure.edn :as edn]
            [cprop.core :refer [load-config]]))

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
  (let [embed-extensions #{"png" "jpg" "jpeg"}
        raw-msg-extensions #{"" "gifv" "gif"}
        skipped-extensions #{"mp4"}
        labelled-posts (map (fn [post]
                              (let [extension
                                    (last (s/split (apply str (take-last 5 post)) #"\."))]
                                (cond
                                  (contains? skipped-extensions extension) {:skip post}
                                  (contains? embed-extensions extension) {:embed post}
                                  :default {:raw post})))
                            posts)
        skipped (count (u/get-inner-values-matching-key labelled-posts :skip))
        embeddable (u/get-inner-values-matching-key labelled-posts :embed)
        to-raw (u/get-inner-values-matching-key labelled-posts :raw)]
    (when (> skipped 0)
      (responses/POST-string token (str "Skipped " skipped " of " (count posts) " requested posts due to invalid format.")))
    (when (> (count embeddable) 0) (responses/POST-embed token embeddable))
    (when (> (count to-raw) 0)
      (for [r to-raw]
        (responses/POST-single-URL token r)))))

(defn valid-post-count?
  "Do we have a valid amount of posts?"
  ;; Allowing nil values as true because most of the commands do not actually
  ;; include a post count -- only the /posts command does. [c guild]
  (if (nil? c)
    true
    (<= 1 c (:max-posts (get @config/config guild)))))

(defn command-handler
  "Determine what response is made to incoming commands."
  [body channel]
  (h/send! channel
           {:status 200
            :headers {"Content-Type" "application/json"}
            :body (j/write-value-as-string {:type 5})})
  ;; http-kit appears to not be properly flushing the channel until we return
  ;; here. Wrapping the `let` in an async thread resolves this issue.
  (a/thread
    (let [data (get body "data")
          options (get data "options")
          command-name (get data "name")
          token (get body "token")
          guild (get body "guild_id")
          subreddit (u/get-value-from-ith-map options 0 "value")
          post-type (keyword (u/get-value-from-ith-map options 1 "value"))
          post-time-scope (keyword (u/get-value-from-ith-map options 2 "value"))
          post-count (u/get-value-from-ith-map options 3 "value")]

      (when (not (config/known-guild? guild))
        (do
          (reset! config/config (assoc @config/config guild config/default-guild-data))
          (u/write-to-resource "config.edn" (str @config/config))))

      (println (str "serving request for subreddit " subreddit " at " (new java.util.Date)))
      (if (valid-post-count? post-count guild)
        (try (case command-name
               "lofi" (responses/POST-string token "p!play https://www.youtube.com/watch?v=5qap5aO4i9A")
               "post" (post-handler (reddit/get-posts subreddit) token)
               "post-from" (post-handler (reddit/get-posts subreddit post-type) token)
               "post-from-time" (post-handler (reddit/get-posts subreddit post-type post-time-scope) token)
               "posts" (post-handler (reddit/get-posts subreddit post-type post-time-scope post-count) token)
             ;; "update-max" (do
             ;;           ;;"subreddit" used loosely here -- for most commands, the
             ;;           ;;value of the 0th map will be a subreddit. Here
             ;;           ;;subreddit is the new number of maximum posts a server
             ;;           ;;owner wants to allow at a time.
             ;;                (swap! max-posts subreddit)
             ;;                (u/write-edn {:max-posts @max-posts} "config.edn"))
               )
             (catch Exception e
               (responses/POST-string token (str "You asked for subreddit " subreddit ", which doesn't seem to exist."))))

        (responses/POST-string token (str "you asked for too many posts. max is " (:max-posts (get @config/config guild))))))))
