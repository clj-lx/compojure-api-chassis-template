(ns {{project-ns}}.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [metrics.ring.instrument :refer [instrument instrument-by uri-prefix]]
            [{{project-ns}}.auth.rules :as auth-rules]
            [{{project-ns}}.config :refer [config]]
            {{#html-hook?}}
            [{{project-ns}}.handlers.web]
            {{/html-hook?}}
            [{{project-ns}}.handlers.auth]
            [{{project-ns}}.handlers.spec]))

(def swagger-api
  (api
    {:swagger
     {:ui   "/swagger"
      :spec "/swagger.json"
      :data {:info                {:title       "Microservices {{full-name}} using compojure-api 2.0"
                                   :description "Production ready microservices {{full-name}}  with authentication, instrumentation, configuration "}
             :consumes            ["application/json"]
             :produces            ["application/json"]
             :tags                [{:name "auth", :description "authorization rules"}
                                   {:name "spec", :description "math with clojure.spec coercion"}]
             :securityDefinitions {:api_key {:type "apiKey"
                                             :name "Authorization"
                                             :in   "header"}}}}}
    {{project-ns}}.handlers.auth/routes
    {{project-ns}}.handlers.spec/routes
    {{#html-hook?}}
    {{project-ns}}.handlers.spec/routes
    {{/html-hook?}}
    ))

(defn- dev-middleware
  "dev middleware for rendering a friendly error page when a parsing error occurs"
  [handler cfg]
  (if (:dev cfg)
    (-> handler (wrap-stacktrace))
    handler))

(defn- basic-auth-middleware
  [handler cfg]
  "basic-auth middleware for swagger in non-dev mode"
  (if (:dev cfg)
    handler
    (-> handler
        (auth-rules/wrap-basic-auth cfg))))

(defn- api-app
  "api routes"
  [cfg]
  (-> swagger-api
      {{#html-hook?}}(basic-auth-middleware cfg){{/html-hook?}}
      (auth-rules/wrap-auth cfg)))

{{#html-hook?}}
(defn- ui-app
  "html app routes"
  [cfg]
  (-> {{project-ns}}.handlers.web/app))
 {{/html-hook?}}

(defn app [cfg]

  (-> (compojure.core/routes (api-app cfg) {{#html-hook?}}(ui-app cfg){{/html-hook?}})
      (dev-middleware cfg)
      ;;session is important enough to be declared here
      (wrap-session {:cookie-name (:cookie_name cfg)
                     :store       (cookie-store {:key (:cookie_key cfg)})})
      (instrument-by uri-prefix)))
