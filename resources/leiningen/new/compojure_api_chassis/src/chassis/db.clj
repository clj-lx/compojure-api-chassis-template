(ns {{project-ns}}.db
  (:require [{{project-ns}}.config :refer [config]]
            [mount.core :refer [defstate]]
            [hikari-cp.core :refer :all]
    ;; requiring this namespace will coerce sql Timestamps onto joda time objs and vice versa
    ;; http://clj-time.github.io/clj-time/doc/clj-time.jdbc.html
            [clj-time.jdbc]
            [clojure.java.jdbc :as jdbc]
            [migratus.core :as migratus]
            [clojure.tools.logging :as log]))

;; TODO move these settings to config when required
(def datasource-opts
  (delay
    {:auto-commit        true
     :read-only          false
     :connection-timeout 30000
     :validation-timeout 5000
     :idle-timeout       600000
     :max-lifetime       1800000
     :minimum-idle       10
     :maximum-pool-size  10
     :jdbc-url           (:database_url config)}))

(def migrations-config
  (delay
    {:store         :database
     :init          "init.sql"
     :migration-dir "migrations"
     :db            (:database_url config)}))

(defn- init
  "Main function that starts the config loading, and checks if datetime coercion is enabled"
  []
  (mount.core/start #'{{project-ns}}.config/config)
  (if (find-ns 'clj-time.jdbc)
    (do
      (log/warn "'clj-time.jdbc' ns is loaded. This will coerce java.sql.Timestamps to org.joda.time.DateTime")
      (log/warn "See http://clj-time.github.io/clj-time/doc/clj-time.jdbc.html for more info"))))

(defstate datasource
          :start (do
                   (init)
                   (make-datasource @datasource-opts))
          :stop (close-datasource datasource))

(defmacro with-advisory-lock
  "Runs a body withing a advisory lock."
  [^String key uri & body]
  `(jdbc/with-db-connection [conn# {:connection-uri ~uri}]
                            (log/info "Trying to acquire advisory lock" ~key)
                            (let [[{locked?# :pg_try_advisory_lock}] (jdbc/query conn# [(str "SELECT pg_try_advisory_lock(" (hash ~key) ");")])]
                              (when locked?#
                                ~@body)
                              (if-not locked?#
                                (log/info "Lock" ~key "was NOT acquired, bailing")
                                (do
                                  (log/info "Releasing lock" ~key)
                                  (jdbc/query conn# [(str "SELECT pg_advisory_unlock(" (hash ~key) ");")])
                                  (log/info "Lock" ~key "released"))))))

(defn migrations
  "Convenience method for using migratus from lein tasks. Because we have a dependency on mount for configuration reading,
   we need to load mount beforehand"
  [cmd & args]
  (init)
  (log/info cmd args)
  (let [cfg @migrations-config
        uri (:db cfg)]
    (case cmd
      "init"
      (with-advisory-lock "dashboard" uri
                          (migratus/init cfg))

      "migrate"
      (with-advisory-lock "dashboard" uri
                          (migratus/migrate cfg))

      "up"
      (with-advisory-lock "dashboard" uri
                          (apply migratus/up cfg args))

      "down"
      (with-advisory-lock "dashboard" uri
                          (apply migratus/down cfg args))

      "create"
      (with-advisory-lock "dashboard" uri
                          (apply migratus/create cfg args))

      "reset"
      (with-advisory-lock "dashboard" uri
                          (migratus/reset cfg))

      "pending"
      (with-advisory-lock "dashboard" uri
                          (apply migratus/pending-list cfg args))

      "rollback"
      (with-advisory-lock "dashboard" uri
                          (migratus/rollback cfg))

      (log/info "No command supplied. Please provide one of: init, migrate, up, down, create, reset, pending, rollback "))))


