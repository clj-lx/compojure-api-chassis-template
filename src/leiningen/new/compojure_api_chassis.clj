(ns leiningen.new.compojure-api-chassis
  (:require [leiningen.new.templates :refer [renderer raw-resourcer name-to-path ->files
                                             sanitize sanitize-ns project-name]]
            [leiningen.core.main :as main]
            [clojure.string :refer [join]]))

(def render (renderer "compojure-api-chassis"))
(def raw (raw-resourcer "compojure-api-chassis"))


(defn wrap-indent [wrap n list]
  (fn []
    (->> list
         (map #(str "\n" (apply str (repeat n " ")) (wrap %)))
         (join ""))))

(defn indent [n list]
  (wrap-indent identity n list))

(def valid-opts ["+pgsql" "+html" "+oauth2"])

(defn pgsql?  [opts] (some #{"+pgsql"}  opts))
(defn html?   [opts] (some #{"+html"} opts))
(defn oauth2? [opts] (some #{"+oauth2"} opts))

(defn validate-opts [opts]
  (let [invalid-opts (remove (set valid-opts) opts)]
    (cond
      (and (some #{"+oauth2"} opts) (not (some #{"+html"} opts)))
      ("you can't use oauth2 without html")

      (seq invalid-opts)
      (str "invalid options supplied: " (clojure.string/join " " invalid-opts)
           "\nvalid options are: " (join " " valid-opts)))))


(defn template-data [name opts]
  {:full-name name
   :name (project-name name)
   :project-ns (sanitize-ns name)
   :sanitized (name-to-path name)

   :pgsql-hook?  (fn [block] (if (pgsql? opts)  (str block "") ""))
   :html-hook? (fn [block] (if (html? opts) (str block "") ""))
   :oauth2-hook? (fn [block] (if (oauth2? opts) (str block "") ""))
   })


(defn format-files-args [name opts]
  (let [data (template-data name opts)
        args [data
              ["project.clj" (render "project.clj" data)]
              ["Procfile" (render "Procfile" data)]
              ["README.md"  (render "README.md" data)]
              [".gitignore"  (render ".gitignore" data)]
              ["config.edn"  (render "config.edn" data)]
              ["resources/logback.xml" (render "resources/logback.xml" data)]

              ["dev/user.clj" (render "dev/user.clj" data)]
              ["src/{{sanitized}}/main.clj" (render "src/chassis/main.clj" data)]

              ["src/{{sanitized}}/server.clj" (render "src/chassis/server.clj" data)]
              ["src/{{sanitized}}/handler.clj" (render "src/chassis/handler.clj" data)]
              ["src/{{sanitized}}/config.clj" (render "src/chassis/config.clj" data)]

              ["src/{{sanitized}}/utils/logging.clj" (render "src/chassis/utils/logging.clj" data)]
              ["src/{{sanitized}}/auth/rules.clj" (render "src/chassis/auth/rules.clj" data)]
              ["src/{{sanitized}}/services/math.clj" (render "src/chassis/services/math.clj" data)]

              ["src/{{sanitized}}/handlers/auth.clj" (render "src/chassis/handlers/auth.clj" data)]
              ["src/{{sanitized}}/handlers/spec.clj" (render "src/chassis/handlers/spec.clj" data)]

              ["test/{{sanitized}}/handlers/spec_test.clj" (render "test/chassis/handlers/spec_test.clj" data)]
              ]

        ;;psql
        args (if (pgsql? opts)
               (conj args
                     ["src/{{sanitized}}/db.clj" (render "src/chassis/db.clj" data)]
                     ["resources/migrations/init.sql" (render "resources/migrations/init.sql" data)]
                     )
               args)

        ;;html/html
        args (if (html? opts)
               (conj args
                     ["src/{{sanitized}}/handlers/web.clj" (render "src/chassis/handlers/web.clj" data)]
                     ["resources/public/lisplogo_256.png" (raw "resources/public/lisplogo_256.png")]
                     ["resources/templates/index.html" (render "resources/templates/index.html" data)]
                     ["resources/templates/session.html" (render "resources/templates/session.html" data)]
                     )
               args)
        ]
    args))

;;docstring is for using > lein new :show compojure-api-chassis
(defn compojure-api-chassis
  "Usage:
    > lein new compojure-api-chassis <opts>
  Options are:
  +pgsql:  use postgres
  +html:   use html templating
  +oauth2: use oauth2 for html templating
  "
  [name & opts]
  (main/info "Generating fresh 'lein new' compojure-api-chassis project.")
  (if-let [error (validate-opts opts)]
    (println error)
    (apply ->files (format-files-args name opts))))
