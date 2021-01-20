(ns rjedbot.commands
  (:require [jsonista.core :as j]
            [rjedbot.reddit :as reddit]
            [clojure.pprint :as pp]
            [clojure.string :as s]))

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
   :body (j/write-value-as-string {:type 4
                                   :data {:embeds
                                          (into []
                                                (map make-embed url-list))}})})

(defn get-value
  [m i]
  (get-in m [i "value"]))

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
        skipped (count (filter #(:skip %) labelled-posts))
        to-embed (filter #(:embed % labelled-posts))
        to-raw (filter #(:raw % labelled-posts))]
    (make-embed-from-urls (map #(:embed %) (filter #(:embed %) labelled-posts)))))

(def post-max 6)

(defn valid-post-count?
  [the-count]
  (if (nil? the-count)
    true
    (<= 1 the-count post-max)))

(defn command-handler
  [body]
  (let [data (get body "data")
        options (get data "options")
        command-name (get data "name")
        subreddit (get-value options 0)
        post-type (keyword (get-value options 1))
        post-time-scope (keyword (get-value options 2))
        post-count (get-value options 3)]

    (if (valid-post-count? post-count)
      (case command-name
        "lofi" (make-string-response "p!play https://www.youtube.com/watch?v=5qap5aO4i9A")
        "post" (post-handler (reddit/get-posts subreddit))
        "post-from" (post-handler (reddit/get-posts subreddit post-type))
        "post-from-time" (post-handler (reddit/get-posts subreddit post-type post-time-scope))
        "posts" (post-handler (reddit/get-posts subreddit post-type post-time-scope post-count)))
      (make-string-response (conj "you asked for too many posts. max is " post-max)))))
