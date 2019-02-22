(ns leiningen.new.compojure-api-chassis
  (:require [leiningen.new.templates :refer [renderer raw-resourcer name-to-path ->files
                                             sanitize sanitize-ns project-name]]
            [leiningen.core.main :as main]
            [clojure.string :refer [join]])
  (:import (org.apache.commons.lang RandomStringUtils)))

(def render (renderer "compojure-api-chassis"))
(def raw (raw-resourcer "compojure-api-chassis"))


(defn wrap-indent [wrap n list]
  (fn []
    (->> list
         (map #(str "\n" (apply str (repeat n " ")) (wrap %)))
         (join ""))))

(defn indent [n list]
  (wrap-indent identity n list))

(def valid-opts ["+pgsql" "+html" "+oauth2" "+cheshire" "+heroku" "+async"])

(defn pgsql? [opts] (some #{"+pgsql"} opts))
(defn html? [opts] (some #{"+html"} opts))
(defn oauth2? [opts] (some #{"+oauth2"} opts))
(defn cheshire? [opts] (some #{"+cheshire"} opts))
(defn heroku? [opts] (some #{"+heroku"} opts))
(defn async? [opts] (some #{"+async"} opts))

(defn validate-opts [opts]
  (let [invalid-opts (remove (set valid-opts) opts)]
    (cond
      (and (some #{"+oauth2"} opts) (not (some #{"+html"} opts)))
      (str "you can't use oauth2 without html")

      (seq invalid-opts)
      (str "invalid options supplied: " (clojure.string/join " " invalid-opts)
           "\nvalid options are: " (join " " valid-opts)))))

(defn rand-hex [n]
  (RandomStringUtils/randomAlphanumeric n))

(defn template-data [name opts]
  {:full-name       name
   :name            (project-name name)
   :project-ns      (sanitize-ns name)
   :sanitized       (name-to-path name)

   :jwt_key         (rand-hex 10)
   :cookie_key      (rand-hex 16)
   :api_token       (rand-hex 16)
   :basic_auth_pass (rand-hex 10)
   :async-support?  (not (nil? (async? opts)))

   :pgsql-hook?     (fn [block] (if (pgsql? opts) (str block "") ""))
   :html-hook?      (fn [block] (if (html? opts) (str block "") ""))
   :not-html-hook?  (fn [block] (if (not (html? opts)) (str block "") ""))
   :oauth2-hook?    (fn [block] (if (oauth2? opts) (str block "") ""))
   :cheshire-hook?  (fn [block] (if (cheshire? opts) (str block "") ""))
   :jsonista-hook?  (fn [block] (if (not (cheshire? opts)) (str block "") ""))
   :heroku-hook?    (fn [block] (if (heroku? opts) (str block "") ""))})

(defn local_repo_files
  "generates a [file, (raw file)] pair over a list of files to be passed to the project generation"
  [path]
  (let [all-files (file-seq (clojure.java.io/file path))
        repo-dir  (subs path (inc (clojure.string/last-index-of path "/")))]
    (->> all-files
         (filter #(.isFile %))
         (map #(.getPath %))
         (map #(clojure.string/replace-first % path repo-dir))
         (map (fn [p] [p (raw p)])))))


(defn format-files-args [name opts]
  (main/info "template opts:" opts)
  (let [data (template-data name opts)
        args [data
              ["project.clj" (render "project.clj" data)]
              ["README.md" (render "README.md" data)]
              [".gitignore" (render ".gitignore" data)]
              ["config.edn" (render "config.edn" data)]
              ["resources/logback.xml" (render "resources/logback.xml" data)]

              ["dev/user.clj" (render "dev/user.clj" data)]
              ["src/{{sanitized}}/main.clj" (render "src/chassis/main.clj" data)]

              ["src/{{sanitized}}/server.clj" (render "src/chassis/server.clj" data)]
              ["src/{{sanitized}}/handler.clj" (render "src/chassis/handler.clj" data)]
              ["src/{{sanitized}}/config.clj" (render "src/chassis/config.clj" data)]

              ["src/{{sanitized}}/utils/logging.clj" (render "src/chassis/utils/logging.clj" data)]
              ["src/{{sanitized}}/utils/middleware.clj" (render "src/chassis/utils/middleware.clj" data)]
              ["src/{{sanitized}}/auth/rules.clj" (render "src/chassis/auth/rules.clj" data)]
              ["src/{{sanitized}}/services/math.clj" (render "src/chassis/services/math.clj" data)]

              ["src/{{sanitized}}/handlers/auth.clj" (render "src/chassis/handlers/auth.clj" data)]
              ["src/{{sanitized}}/handlers/spec.clj" (render "src/chassis/handlers/spec.clj" data)]
              ["src/{{sanitized}}/models/person.clj" (render "src/chassis/models/person.clj" data)]

              ["test/{{sanitized}}/handlers/spec_test.clj" (render "test/chassis/handlers/spec_test.clj" data)]]

        ;;async
        args (if (async? opts)
               (apply conj args (local_repo_files "resources/leiningen/new/compojure_api_chassis/local_repo"))
               args)

        ;;heroku
        args (if (heroku? opts)
               (conj args
                     ["Procfile" (render "Procfile" data)]
                     ["app.json" (render "app.json" data)])
               args)

        ;;psql
        args (if (pgsql? opts)
               (conj args
                     ["src/{{sanitized}}/db.clj" (render "src/chassis/db.clj" data)]
                     ["resources/migrations/init.sql" (render "resources/migrations/init.sql" data)])

               args)

        ;;html/html
        args (if (html? opts)
               (conj args
                     ["src/{{sanitized}}/handlers/web.clj" (render "src/chassis/handlers/web.clj" data)]
                     ["resources/public/lisplogo_256.png" (raw "resources/public/lisplogo_256.png")]
                     ["resources/templates/index.html" (render "resources/templates/index.html" data)]
                     ["resources/templates/session.html" (render "resources/templates/session.html" data)])

               args)]
    args))

;;docstring is for using > lein new :show compojure-api-chassis
(defn compojure-api-chassis
  "Usage:
    > lein new compojure-api-chassis <opts>
  Options are:
  +pgsql    use postgres
  +html     use html templating
  +oauth2   use oauth2 for html templating
  +cheshire use cheshire for json
  +heroku   generate Procfile, app.json for heroku deployment
  +async    use async handlers & patched jars to support 3-arity handlers
  "
  [name & opts]
  (main/info "Generating fresh 'lein new' compojure-api-chassis project.")
  (if-let [error (validate-opts opts)]
    (println error)
    (apply ->files (format-files-args name opts))))
