(ns {{project-ns}}.config
  (:require [omniconf.core :as cfg]
    [mount.core :refer [defstate]]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [clojure.java.io :as io])
  (:import [java.io File]
    [java.net URI]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; helper methods for jdbc parsing

(defn- create-uri [url] (URI. url))

(defn- parse-username-and-password [^URI uri]
  (if-let [userpass (.getUserInfo uri)]
    (clojure.string/split userpass #":")))

(defn- subname [^URI uri]
  (format "//%s:%s%s" (.getHost uri) (.getPort uri) (.getPath uri)))

(defn- scheme [^URI uri]
  (let [s (.getScheme uri)]
    (cond (= "postgres" s) "jdbc:postgresql"
          :default (str "jdbc:" s))))

(defn parse-jdbc-url
  "Converts a DATABASE_URL to a JDBC-friendly connection string"
  [url]
  (if (str/starts-with? url "jdbc")
    url
    (let [uri (create-uri url)
          jdbc (subname uri)
          scheme (scheme uri)
          [username password] (parse-username-and-password uri)]
      (println (and username password) scheme jdbc)
      (if (and username password)
        (format "%s:%s?user=%s&password=%s" scheme jdbc username password)
        (format "%s:%s" scheme jdbc)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; helper methods for loading .env files

(defn- read-env-file
  "read environment variable definitions from file into a map."
  ([^File file]
   (when (.isFile file)
     (try
       (with-open [rdr (io/reader file)]
         (->> (line-seq rdr)
              (map str/trim)
              (remove str/blank?)
              (map #(str/split % #"(\s*=\s*)|(:\s+)"))
              (map (fn [[k v]]
                     (case v
                       "\"\"" [k ""]
                       nil [k nil]
                       [k (str/replace v #"[\"]" "")])))
              (into {})))
       (catch Throwable e
         (.printStackTrace e)
         (throw (Error. (format "Could not load configuration file: %s" (.getCanonicalPath file)))))))))

(defn inject-system-properties-from-file!
  "helper method that reads from a `.env` style file and writes to `System/properties`
  the file should have the format `KEY=value`"
  [^String file]
  (let [kvs (read-env-file (io/as-file file))]
    (doseq [[k v] kvs]
      (System/setProperty k v))))

;;;;;;;;;;;;
;; main code

(defn init
  "Defines the app configuration keys. Keys will have a flat structure to ensure compatibility with eg. heroku,
  and because nested keys get a double underscore `PARENT__CHILD` which may be unexpected"
  [cli-args]
  (cfg/define
    {:help                  {:description      "prints this help message"
                             :help-name        "main app"
                             :help-description "available settings. see https://github.com/grammarly/omniconf#configuration-scheme-syntax for more info"}

     :dev                   {:description "indicates if we are in development mode; values '0' and 'false' are false"
                             :type        :boolean
                             :default     true}

     :conf-file             {:description "config file"
                             :type        :string
                             :default     "config.edn"}

     :api_tokens            {:description "edn map of `{<token> :role}` tokens that should be sent on the 'Authorization: Token <token>' header"
                             :type        :edn
                             :required    true}

     :cookie_name           {:description "cookie name"
                             :type        :string
                             :default     "{{project-ns}}-session"}

     :cookie_key            {:description "cookie encryption key"
                             :type        :string
                             :secret      true
                             :required    true}

     :swagger_ui_auth       {:description "a `username:password` pair basic-auth on swagger ui"
                             :type        :string
                             :default     "{{name}}:{{basic_auth_pass}}"
                             :secret      true}

     :swagger_ui_route      {:description "location of swagger ui"
                             :type        :string
                             :default "/swagger"}

     :jwt_key               {:description "jwt key for signing jwt tokens"
                             :type        :string
                             :secret      true
                             :required    :true}

     :jwt_issuer            {:description "jwt 'iss' for claim validation"
                             :type        :string
                             :default     "{{name}}"
                             :required    :true}

     :port                  {:description "port where the server will run"
                             :type        :number
                             :default     3000}
     {{#pgsql-hook?}}
     :database_url          {:description "database url"
                             :type        :string
                             :required    true
                             :parser      parse-jdbc-url
                             :default     "jdbc:postgresql://localhost:5432/{{project-ns}}"}
      {{/pgsql-hook?}}
      {{#oauth2-hook?}}
      :oauth2_client_id {:description "oauth2 client id; see https://console.developers.google.com"
                          :type        :string
                          :secret      true
                          :required    true}
      :oauth2_client_secret {:description "oauth2 client token; see https://console.developers.google.com"
                             :type        :string
                             :secret      true
                             :required    true}
      :oauth2_redirect_path {:description "oauth2 redirect path; see https://console.developers.google.com"
                             :type        :string
                             :default     "/oauth2callback"}

      :oauth2_redirect_domain {:description "oauth2 redirect domain; the port should be the same as `:port`"
                               :default     "http://localhost:3000"
                               :type        :string}
      {{/oauth2-hook?}}})


  (cfg/populate-from-cmd cli-args)
  (when-let [conf-file (cfg/get :conf-file)]
    (if (.exists (io/as-file conf-file))
      (do
        (log/info "Reading configuration file: " (.getAbsolutePath (io/as-file conf-file)))
        (cfg/populate-from-file conf-file))
      (log/warn "Configuration file does not exist: " (.getAbsolutePath (io/as-file conf-file)))))
  (cfg/populate-from-env)
  (cfg/verify))


(defstate config
          :start
          (do
            (init (mount.core/args))
            (cfg/get)))
