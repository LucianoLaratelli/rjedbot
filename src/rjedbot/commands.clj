(ns rjedbot.commands
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as s]
            [jsonista.core :as j]
            [rjedbot.reddit :as reddit]
            [rjedbot.util :as util]))

(defn make-string-response
  [string]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (j/write-value-as-string {:type 4 :data {:content string}})})

(defn make-embed
  [url-string]
  {:image {:url url-string}})

(defn make-embed-from-urls
  [url-list]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (j/write-value-as-string
          {:type 4
           :data {:embeds
                  (into []
                        (map make-embed url-list))}})})

(defn get-value
  [m i]
  (get-in m [i "value"]))

(defn contains-key?
  [key m]
  (if (get m key)
    true
    false))

(defn post-handler
  "Handle post URLs according to their types."
  ;;depending on the type of content, some posts must be embedded while other
  ;;must be sent as raw links, yet others can not be sent at all. The
  ;;restriction here is that certain content types do not view properly in
  ;;discord. gifs won't play in embeds, for example. From simple
  ;;experimentation, I've determined the following:
  ;;no extension, gifv, gif => raw link (sent as follow-up messages after embed is sent)
  ;;allowed extensions (png, jpg, jpeg) => embed (all in the same response)
  ;;mp4 => skip (maybe a message in the response like "skipped n invalid files")
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
        skipped (count (map :skip (filter #(contains-key? :skip %) labelled-posts)))
        to-embed (map :embed (filter #(contains-key? :embed %) labelled-posts))
        to-raw (map :raw (filter #(contains-key? :raw %) labelled-posts))
        sendable (count to-embed)]
    (if (> sendable 0)
      (make-embed-from-urls to-embed)
      (make-string-response "had to skip all of your posts; they aren't supported yet."))))

(def max-posts (atom (:max-posts (read-string (slurp (io/resource "config.edn"))))))

(defn valid-post-count?
  [the-count]
  (if (nil? the-count)
    true
    (<= 1 the-count @max-posts)))

(defn command-handler
  [body]
  (let [data (get body "data")
        options (get data "options")
        command-name (get data "name")
        subreddit (get-value options 0)
        post-type (keyword (get-value options 1))
        post-time-scope (keyword (get-value options 2))
        post-count (get-value options 3)]

    (println (str "serving request for subreddit " subreddit " at " (new java.util.Date)))
    (if (valid-post-count? post-count)
      (case command-name
        "lofi" (make-string-response "p!play https://www.youtube.com/watch?v=5qap5aO4i9A")
        "post" (post-handler (reddit/get-posts subreddit))
        "post-from" (post-handler (reddit/get-posts subreddit post-type))
        "post-from-time" (post-handler (reddit/get-posts subreddit post-type post-time-scope))
        "posts" (post-handler (reddit/get-posts subreddit post-type post-time-scope post-count))
        "update-max" (do
                       (swap! max-posts subreddit)
                       (util/write-edn {:max-posts @max-posts} "config.edn")))

      (make-string-response (conj "you asked for too many posts. max is " @max-posts)))))
