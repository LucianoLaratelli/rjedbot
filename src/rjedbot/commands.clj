(ns rjedbot.commands
  (:require [clojure.pprint :as pp]
            [clojure.string :as s]
            [jsonista.core :as j]
            [org.httpkit.server :as h]
            [rjedbot.config :as conf]
            [rjedbot.guilds :as g]
            [rjedbot.log :refer [log]]
            [rjedbot.reddit :as reddit]
            [rjedbot.responses :as r]
            [rjedbot.util :as util]
            [clojure.core.async :as a]))

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
      (r/handle-rate-limited-call r/POST-string token
                                  (str "Skipped " skipped " of " total " requested posts due to invalid format.")))
    (r/handle-rate-limited-call r/async-POST-embed token embeddable results-chan)
    (doseq [r to-raw]
      (r/handle-rate-limited-call r/POST-string token r results-chan))))

(defn valid-post-count?
  "Do we have a valid amount of posts?"
  [c guild]
  ;; Allowing nil values as true because most of the commands do not actually
  ;; include a post count
  (if (nil? c)
    true
    (<= 1 c (:max-posts (get @conf/config guild)))))

(defn command-handler
  "Determine what response is made to incoming commands."
  [body channel]
  (h/send! channel
           {:headers {"Content-Type" "application/json"}
            :status 200
            :body (j/write-value-as-string {:type 5})}
           true)
  ;; The (= "post") mess is to deal with the fact that the commands that
  ;; actually retrieve reddit commands are implemented as subcommands:
  ;; https://discord.com/developers/docs/interactions/slash-commands#subcommands-and-subcommand-groups
  ;; when the interaction requested by the user isn't in the `post` subcommand
  ;; group, any options for that command are stored in the first level
  ;; of "options" in the request body. however, when we have a `post`, the
  ;; options rjedbot actually wants to pass to the reddit functions are stored
  ;; in a second level of options. this pops up twice; once when we go get
  ;; command options, and another time when we get the value of the first
  ;; option.
  (let [data (get body "data")
        raw-name (get data "name")
        command-options (if (= "post" raw-name) (first (get data "options")) (get data "options"))
        subcommand-options (get command-options "options")
        command-name (if (= "post" raw-name)
                       (get-in body ["data" "name"])
                       raw-name)
        token (get body "token")
        guild (get body "guild_id")
        subreddit (let [func #(util/get-value-from-ith-map % 0 "value")]
                    (if (= "post" raw-name)
                      (func subcommand-options)
                      (func command-options)))
        post-type (keyword (util/get-value-from-ith-map subcommand-options 1 "value"))
        post-time-scope (keyword (util/get-value-from-ith-map subcommand-options 2 "value"))
        post-count (util/get-value-from-ith-map subcommand-options 3 "value")
        arguments (filter #(not (nil? %)) [subreddit post-type post-time-scope post-count])
        chan (a/chan)]

    (when (not (conf/known-guild? guild))
      (reset! conf/config (assoc @conf/config guild conf/default-guild-data))
      (util/write-to-resource "config.edn" (str @conf/config)))
    (log (str "serving request at " (new java.util.Date)))
    (if (valid-post-count? post-count guild)
      (try  (case command-name
              "lofi" (r/POST-string token "p!play https://www.youtube.com/watch?v=5qap5aO4i9A" chan)
              "list-favorites" (g/send-favorites guild token chan)
              "remove-favorite" (g/remove-favorite guild subreddit token chan)
              "add-favorite" (g/add-favorite guild subreddit token chan)
              ;;for most commands, the value of the 0th map will be a subreddit
              ;;name. For update-max, subreddit is the new maximum number of
              ;;posts. For surprises, it is the number of surprises to request.
              "update-max" (conf/update-maximum guild token subreddit chan)
              "surprise" (post-handler (conf/get-surprises subreddit guild token chan) token)
              ;; handle these cases: "hot" "category" "time" "many"
              (post-handler (apply reddit/get-posts arguments) token))
            (catch Exception e
              (log "Exception in command handler:")
              (log (.getMessage e))
              (r/POST-string token (str "You asked for subreddit " subreddit ", which doesn't seem to exist.") chan)))
      (r/POST-string token (str "you asked for too many posts. max is " (:max-posts (get @conf/config guild))) chan))))
