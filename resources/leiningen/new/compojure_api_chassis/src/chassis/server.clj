(ns {{project-ns}}.server
  (:require [{{project-ns}}.config :refer [config]]
            [{{project-ns}}.handler :as handler]
            [mount.core :refer [defstate]]
            [ring.adapter.jetty :refer [run-jetty]]))

(defstate server
  :start (run-jetty (handler/app config) {:port   (:port config)
                                          ;;friend doesn't support async handlers
                                          :async? false 
                                          :join?  false})
  :stop (.stop server))                  
  
