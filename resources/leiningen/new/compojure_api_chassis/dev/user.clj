(ns user
  (:require [clojure.pprint :refer [pprint]]
            [mount.core :as mount]
            [mount.tools.graph :refer [states-with-deps]]
            [clojure.tools.namespace.repl :as tn]
            [clojure.test :refer :all]
            [kaocha.repl]
            [{{project-ns}}.utils.logging :refer [with-logging-status]]
            [{{project-ns}}.config :refer [config]]
            {{#pgsql-hook?}}
            [{{project-ns}}.db :refer [datasource]]
            {{/pgsql-hook?}}
            [{{project-ns}}.server :refer [server]]))


(defn start []
  (with-logging-status)
  (mount/start-with-args *command-line-args*))

(defn stop []
  (mount/stop))

(defn refresh []
  (stop)
  (tn/refresh))

(defn refresh-all []
  (stop)
  (tn/refresh-all))

(defn go
  "starts all states defined by defstate"
  []
  (start)
  :ready)

(defn reset
  "stops all states defined by defstate, reloads modified source files, and restarts the states"
  []
  (stop)
  (tn/refresh :after 'user/go))

(mount/in-clj-mode)


(defn testall []
  (user/refresh)
  (clojure.test/run-all-tests #"{{project-ns}}.*"))

(defn ktestall []
  (user/refresh)
  (kaocha.repl/run :unit))