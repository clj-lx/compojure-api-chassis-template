(ns {{project-ns}}.main
  (:require [{{project-ns}}.config :refer [config]]
            [{{project-ns}}.server :refer [server]]
            {{#pgsql-hook?}}
            [{{project-ns}}.db :refer [datasource]]
            {{/pgsql-hook?}}
            [{{project-ns}}.utils.logging :as ulogging]
            [mount.core :as mount])
  (:gen-class))

(set! *warn-on-reflection* 1)

(declare app)

(defn verify [& args]
  ({{project-ns}}.config/init args))

(defn init []
  (ulogging/with-logging-status)
  (mount/start-with-args (mount/args))
  ;;dynamic def!
  (def app ({{project-ns}}.handler/app config)))

(defn -main [& args]
  (ulogging/with-logging-status)
  (mount/start-with-args args))
