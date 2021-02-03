(ns rjedbot.commands
  (:require [clojure.core.async :as a]
            [clojure.pprint :as pp]
            [clojure.string :as s]
            [jsonista.core :as j]
            [org.httpkit.server :as h]
            [rjedbot.config :as config]
            [rjedbot.log :refer [log]]
            [rjedbot.reddit :as reddit]
            [rjedbot.responses :as r]
            [rjedbot.util :as u]))

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
        skipped (count (u/get-inner-values-matching-key labelled-posts :skip))
        embeddable (u/get-inner-values-matching-key labelled-posts :embed)
        to-raw (u/get-inner-values-matching-key labelled-posts :raw)
        results-chan (a/chan)]
    (when (> skipped 0)
      (r/handle-rate-limited-call r/POST-string token
                                  (str "Skipped " skipped " of " total " requested posts due to invalid format.")))
    (r/handle-rate-limited-call r/async-POST-embed token embeddable results-chan)
    (doseq [r to-raw]
      (r/handle-rate-limited-call r/POST-single-URL token r results-chan))))

(defn valid-post-count?
  "Do we have a valid amount of posts?"
  [c guild]
  ;; Allowing nil values as true because most of the commands do not actually
  ;; include a post count -- only the /posts command does. [c guild]
  (if (nil? c)
    true
    (<= 1 c (:max-posts (get @config/config guild)))))

(defn get-surprises
  [amount guild-id]
  (set (repeatedly amount #(config/get-favorite-subreddit guild-id))))

(defn command-handler
  "Determine what response is made to incoming commands."
  [body channel]
  (h/send! channel
           {:headers {"Content-Type" "application/json"}
            :status 200
            :body (j/write-value-as-string {:type 5})}
           true)
  (let [data (get body "data")
        command-options (first (get data "options"))
        subcommand-options (get command-options "options")
        command-name (get command-options "name")
        token (get body "token")
        guild (get body "guild_id")
        subreddit (u/get-value-from-ith-map subcommand-options 0 "value")
        post-type (keyword (u/get-value-from-ith-map subcommand-options 1 "value"))
        post-time-scope (keyword (u/get-value-from-ith-map subcommand-options 2 "value"))
        post-count (u/get-value-from-ith-map subcommand-options 3 "value")
        arguments (filter #(not (nil? %)) [subreddit post-type post-time-scope post-count])]

    (when (not (config/known-guild? guild))
      (reset! config/config (assoc @config/config guild config/default-guild-data))
      (u/write-to-resource "config.edn" (str @config/config)))
    (log (str "serving request for subreddit " subreddit " at " (new java.util.Date)))
    (if (valid-post-count? post-count guild)
      (try  (case command-name
              "lofi" (r/POST-string token "p!play https://www.youtube.com/watch?v=5qap5aO4i9A")
              ;;note that `subreddit` is used as the number of posts here -- the first argument to the 'surprise' command
              "surprise" (do
                           (log (str "handling " subreddit " surprises at " (new java.util.Date)))
                           (post-handler (flatten (map #(reddit/get-posts %) (get-surprises (if (nil? subreddit) 1 subreddit) guild))) token))
              ;; "update-max" (do
              ;;           ;;"subreddit" used loosely here -- for most commands, the
              ;;           ;;value of the 0th map will be a subreddit. Here
              ;;           ;;subreddit is the new number of maximum posts a server
              ;;           ;;owner wants to allow at a time.
              ;;                (swap! max-posts subreddit)
              ;;                (u/write-edn {:max-posts @max-posts} "config.edn"))
              ;; handle these cases: "hot" "category" "time" "many"
              (post-handler (apply reddit/get-posts arguments) token))
            (catch Exception e
              (log "Exception in command handler:")
              (log (.getMessage e))
              (r/POST-string token (str "You asked for subreddit " subreddit ", which doesn't seem to exist."))))
      (r/POST-string token (str "you asked for too many posts. max is " (:max-posts (get @config/config guild)))))))
