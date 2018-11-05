(ns {{project-ns}}.utils.middleware
  (:require
    [ring.middleware.reload :refer [wrap-reload]]
    [ring.middleware.stacktrace :refer [wrap-stacktrace]]
    [{{project-ns}}.auth.rules :as auth-rules]
    [{{project-ns}}.config :refer [config]])
  (:import [java.util UUID]
           [org.slf4j MDC]))

(defn dev-middleware
  "dev middleware for rendering a friendly error page when a parsing error occurs"
  [handler cfg]
  (if (:dev cfg)
    (-> handler (wrap-stacktrace))
    handler))

(defn basic-auth-middleware
  "basic-auth middleware for swagger in non-dev mode"
  [handler cfg]
  (if (:dev cfg)
    handler
    (-> handler
        (auth-rules/wrap-basic-auth cfg))))

(defn wrap-uuid
  "adds a `:uuid` field in the request and in the MDC context"
  [handler]
  (fn
    ([request]
     (let [uuid (.toString (UUID/randomUUID))]
       (try
         (MDC/put "uuid" uuid)
         (handler (assoc request :uuid uuid))
         (finally
           (MDC/clear)))))
    ([request respond raise]
     (let [uuid (.toString (UUID/randomUUID))]
       (try
         (MDC/put "uuid" uuid)
         (handler (assoc request :uuid uuid) respond raise)
         (catch Exception e (raise e))
         (finally
           (MDC/clear)))))))