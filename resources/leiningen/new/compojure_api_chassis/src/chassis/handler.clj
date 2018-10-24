(ns {{project-ns}}.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [metrics.ring.instrument :refer [instrument instrument-by uri-prefix]]
            [muuntaja.core :as muuntaja]
            {{#cheshire-hook?}}
            [muuntaja.format.cheshire]
            {{/cheshire-hook?}}
            [{{project-ns}}.auth.rules :as auth-rules]
            [{{project-ns}}.config :refer [config]]
            [{{project-ns}}.utils.middleware :as mw]
            [{{project-ns}}.models.person :as person]
            {{#html-hook?}}
            [{{project-ns}}.handlers.web]
            {{/html-hook?}}
            [{{project-ns}}.handlers.auth]
            [{{project-ns}}.handlers.spec]))

{{#not-html-hook?}}
;; route / to swagger because we dont have html
(def home->swagger
  (GET "/" []
       (redirect "/swagger")))
{{/not-html-hook?}}

(def swagger-api
  (api
    {:formats
     {{#cheshire-hook?}}
     (assoc-in
       muuntaja/default-options
       [:formats "application/json"]
       muuntaja.format.cheshire/format)
     {{/cheshire-hook?}}
     {{#jsonista-hook?}}
     (assoc-in
       muuntaja/default-options
       [:formats "application/json" :encoder 1]
       (partial {:encoders person/encoder})) ;;check jsonista docs: https://cljdoc.xyz/d/metosin/jsonista/0.2.2/api/jsonista
     {{/jsonista-hook?}}
     :swagger
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
    {{#not-html-hook?}}
    (undocumented home->swagger)
    {{/not-html-hook?}}
    {{#html-hook?}}
    {{project-ns}}.handlers.spec/routes
    {{/html-hook?}}))

(defn- api-app
  "api routes"
  [cfg]
  (-> swagger-api
      (mw/basic-auth-middleware cfg)
      (auth-rules/wrap-auth cfg)))

{{#html-hook?}}
(defn- ui-app
  "html app routes"
  [cfg]
  (-> {{project-ns}}.handlers.web/app)){{/html-hook?}}

(defn app
  [cfg]
  (-> (compojure.core/routes (api-app cfg) {{#html-hook?}}(ui-app cfg){{/html-hook?}})
      (mw/dev-middleware cfg)
      (mw/wrap-uuid)
      ;;session is important enough to be declared here
      (wrap-session {:cookie-name (:cookie_name cfg)
                     :store       (cookie-store {:key (:cookie_key cfg)})})
      (instrument-by uri-prefix)))
