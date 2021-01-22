(ns rjedbot.commands
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as s]
            [jsonista.core :as j]
            [rjedbot.reddit :as reddit]
            [rjedbot.util :as u]))

(defn make-string-response
  [string]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (j/write-value-as-string {:type 4 :data {:content string}})})

(defn make-embed
  [url-string]
  {:image {:url url-string}})

(defn make-embed-from-urls
  [url-list later skipped]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (j/write-value-as-string
          {:type 4
           :data {:content (str "I was able to show you " (count url-list) " posts as embeds.\n"
                                later " posts will be in the next message I send.\n"
                                skipped " posts had to be skipped due to incompatible format.\n")
                  :embeds (into []
                                (map make-embed url-list))}})})

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
  [posts]
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
        to-raw (u/get-inner-values-matching-key labelled-posts :raw)
        sendable (count embeddable)
        ;;TODO: sendable should be embeddable + to-raw once the followup messages are implemented
        ]
    (println (str "handling posts from subreddit at " (new java.util.Date)))
    (if (> sendable 0)
      (make-embed-from-urls embeddable (count to-raw) skipped)
      (make-string-response "had to skip all of your posts; their formats aren't supported yet."))))

(def max-posts (atom (:max-posts (read-string (slurp (io/resource "config.edn"))))))

(defn valid-post-count?
  "Do we have a valid amount of posts?"
  ;; Allowing nil values as true because most of the commands do not actually
  ;; include a post count -- only the /posts command does.
  [c]
  (if (nil? c)
    true
    (<= 1 c @max-posts)))

(defn command-handler
  "Determine what response is made to incoming commands."
  [body]
  (let [data (get body "data")
        options (get data "options")
        command-name (get data "name")
        subreddit (u/get-ith-value-with-key options 0 "value")
        post-type (keyword (u/get-ith-value-with-key options 1 "value"))
        post-time-scope (keyword (u/get-ith-value-with-key options 2 "value"))
        post-count (u/get-ith-value-with-key options 3 "value")]

    (println (str "serving request for subreddit " subreddit " at " (new java.util.Date)))
    (if (valid-post-count? post-count)
      (case command-name
        "lofi" (make-string-response "p!play https://www.youtube.com/watch?v=5qap5aO4i9A")
        "post" (post-handler (reddit/get-posts subreddit))
        "post-from" (post-handler (reddit/get-posts subreddit post-type))
        "post-from-time" (post-handler (reddit/get-posts subreddit post-type post-time-scope))
        "posts" (post-handler (reddit/get-posts subreddit post-type post-time-scope post-count))
        "update-max" (do
                       ;;"subreddit" used loosely here -- for most commands, the
                       ;;value of the 0th map will be a subreddit. Here
                       ;;subreddit is the new number of maximum posts a server
                       ;;owner wants to allow at a time.
                       (swap! max-posts subreddit)
                       (u/write-edn {:max-posts @max-posts} "config.edn")))

      (make-string-response (conj "you asked for too many posts. max is " @max-posts)))))
