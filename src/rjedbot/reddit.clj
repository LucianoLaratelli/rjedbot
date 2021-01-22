(ns rjedbot.reddit
  "Namespace for dealing with Reddit via the creddit API."
  (:require [creddit.core :as creddit]
            [cprop.core :refer [load-config]]))

(def credentials
  (load-config :resource "reddit-config.edn"))

(def creddit-client (creddit/init credentials))

(def creddit-funcs
  {:hot creddit/subreddit
   :controversial creddit/subreddit-controversial
   :new creddit/subreddit-new
   :rising creddit/subreddit-rising
   :top creddit/subreddit-top})

(def creddit-times
  #{:hour :day :week :month :year :all})

(defn get-posts
  "Get a post from a subreddit.
  A post-type must be any one of the keys in `creddit-funcs`. A `time` must be
  any one of the keys in `creddit-times`."
  ([subreddit]
   (get-posts subreddit :hot :all 1))
  ([subreddit post-type]
   (get-posts subreddit post-type :all 1))
  ([subreddit post-type time]
   (get-posts subreddit post-type time 1))
  ([subreddit post-type time amount-posts-requested]
   (let [posts (cond
                 (> amount-posts-requested 10) (throw (Exception. "Can't ask for more than 10 posts at a time."))
                 (< amount-posts-requested 1) (throw (Exception. "Can't ask for less than 1 post."))
                 (not (contains? creddit-times time)) (throw (Exception. "You asked for an invalid time. Must be any one of :hour, :day, :week, :month, :year, :all."))
                 :else (let
                        [posts (map
                                #(:url %)
                                (try
                                  ((get creddit-funcs post-type) creddit-client subreddit amount-posts-requested time)
                                  (catch Exception e
                                    (println "Exception in redbot.reddit/get-posts: " (.getMessage e)))))]
             ;; the creddit API returns 1 + amount-posts-requested if a
             ;; subreddit has a pinned post. I *think* this is because of a bug
             ;; in the reddit API, where pinned posts have a `false` :pinned
             ;; field, and this is a workaround. This isn't explicitly
             ;; documented anywhere on creddit's github page, so who knows if
             ;; it's true or just a coincidence, but it is behavior I have
             ;; observed.
                         (if (> (count posts) amount-posts-requested)
                           (rest posts)
                           posts)))]
     (if (empty? posts)
       (throw "No posts")
       posts))))
