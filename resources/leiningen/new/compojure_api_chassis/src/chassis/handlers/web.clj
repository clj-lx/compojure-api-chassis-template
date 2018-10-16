(ns {{project-ns}}.handlers.web
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            {{#oauth2-hook?}}
            [cemerick.friend :as friend]
            [friend-oauth2.workflow :as oauth2]
            [friend-oauth2.util :refer [format-config-uri]]
            {{/oauth2-hook?}}
            [selmer.parser :as parser]
            [ring.util.response :as response]
            [ring.middleware.defaults :as ring-defaults]
            [{{project-ns}}.config :refer [config]])
  (:import [clojure.lang ExceptionInfo]))

(defn- handle-exception [ex]
  (let [{:keys [type error-template] :as data} (ex-data ex)]
    (if (= :selmer-validation-error type)
      {:status  500
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body    (parser/render error-template data)}
      (throw ex))))

(defn- wrap-error-page
  "development middleware for rendering a friendly error page when a parsing error occurs"
  [handler]
  (fn
    ([request]
     (try
       (handler request)
       (catch ExceptionInfo ex (handle-exception ex))))
    ([req res raise]
     (try
       (handler req res raise)
       (catch ExceptionInfo ex (handle-exception ex))))))

(defn html
  [content]
  (-> content
      (response/response)
      (response/header "Content-Type" "text/html")))

(defn get-status-content
  [count session]
  (parser/render-file "templates/session.html" {:count count :session session}))

(defn render-index-page
  [req]
  (html (parser/render-file "templates/index.html" {})))

(defn render-status-page
  [req]
  (let [count (:count (:session req) 0)
        session (assoc (:session req) :count (inc count))]
    (-> (get-status-content count session)
        (html)
        (assoc :session session))))


{{#oauth2-hook?}}
(defn client-config [cfg]
  {:client-id     (:oauth2_client_id cfg)
   :client-secret (:oauth2_client_secret cfg)
   :callback      {:domain (or (:oauth2_redirect_domain cfg)
                               (str "http://localhost:" (:port cfg)))
                   :path   (:oauth2_redirect_path cfg)}})

(defn uri-config [client-config]
  {:authentication-uri {:url   "https://accounts.google.com/o/oauth2/auth"
                        :query {:client_id     (:client-id client-config)
                                :response_type "code"
                                :redirect_uri  (format-config-uri client-config)
                                :scope         "email"}}

   :access-token-uri   {:url   "https://accounts.google.com/o/oauth2/token"
                        :query {:client_id     (:client-id client-config)
                                :client_secret (:client-secret client-config)
                                :grant_type    "authorization_code"
                                :redirect_uri  (format-config-uri client-config)}}})
(defn credential-fn
  [token]
  {:identity token
   :roles    #{::oauth2-user}})

 {{/oauth2-hook?}}

(defroutes app-routes
           (GET "/" req
             (render-index-page req))
           (GET "/session" req
             (render-status-page req))

           {{#oauth2-hook?}}
           (GET "/authlink" request
             (friend/authorize #{::oauth2-user} (html "Authorized page.")))
           (GET "/authlink2" req
             (friend/authorize
               #{::oauth2-user}
               (html (cheshire.core/generate-string (:session req)))))
           (GET "/admin" req
             (friend/authorize
               #{::administrator}
               (html "Only admins can see this page.")))
           (friend/logout (ANY "/logout" req (response/redirect "/")))
           {{/oauth2-hook?}}

           (route/resources "/public")
           (route/not-found "<h1>Page not found</h1>"))

{{#oauth2-hook?}}
(defn unauthorized-handler
  [_]
  (html "Unauthorized."))

(defn generate-workflow-config []
  (let [cli-config (client-config config)
        uri-config (uri-config cli-config)
        cred-fn credential-fn]
    {:client-config cli-config
     :uri-config    uri-config
     :credential-fn cred-fn
     }))

(defn workflow-middleware
  "Wraps the oauth2/workflow fn because we only have oauth client id and secret
  at runtime (because of mount)"
  []
  (let [config (delay (generate-workflow-config))
        middleware (delay (oauth2/workflow @config))]
    (fn oauth2-handler [request]
      (@middleware request))))

(def auth-opts
  {:allow-anon?          true
   :unauthorized-handler unauthorized-handler
   :workflows            [(workflow-middleware)]})

;; optionally
;;   (friend/wrap-authorize admin-routes #{::administrator})
{{/oauth2-hook?}}

(defn- dev-middleware
  "development middleware for rendering a friendly error page when a parsing error occurs"
  [handler]
  (let [dev? (delay (:dev config))]
    (if @dev?
      (do
        (selmer.parser/cache-off!)
        (-> handler
            (wrap-error-page)))
      handler)))

(def app
  (-> app-routes
      (dev-middleware)
      {{#oauth2-hook?}}(friend/authenticate auth-opts){{/oauth2-hook?}}
      (ring-defaults/wrap-defaults
        ;; cookie same-site attr should be "lax" because of Oauth2 redirect
        ;; otherwise, when the provider redirects to our callback endpoint we won't have the cookie
        (-> ring-defaults/site-defaults (assoc-in [:session :cookie-attrs :same-site] :lax)))))
