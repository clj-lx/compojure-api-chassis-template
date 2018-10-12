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


(defn migrations
  "Convenience method for using migratus from lein tasks. Because we have a dependency on mount for configuration reading,
   we need to load mount beforehand"
  [cmd & args]
  (init)
  (log/info cmd args)
  (case cmd
    "init"
    (migratus/init @migrations-config)

    "migrate"
    (migratus/migrate @migrations-config)

    "up"
    (apply migratus/up @migrations-config args)

    "down"
    (apply migratus/down @migrations-config args)

    "create"
    (apply migratus/create @migrations-config args)

    "reset"
    (migratus/reset @migrations-config)

    "pending"
    (apply migratus/pending-list @migrations-config args)

    "rollback"
    (migratus/rollback @migrations-config)

    (log/info "No command supplied. Please provide one of: init, migrate, up, down, create, reset, pending, rollback ")))



