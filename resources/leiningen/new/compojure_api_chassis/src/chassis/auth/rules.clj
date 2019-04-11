(ns {{project-ns}}.auth.rules
  (:require [compojure.api.meta :refer [restructure-param]]
    [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
    [buddy.auth.accessrules :refer [wrap-access-rules error]]
    [buddy.auth.backends.session :as session]
    [buddy.auth.backends.token :as token]
    [buddy.auth.backends.httpbasic :as basic]
    [buddy.auth :refer [authenticated?]]
    [buddy.sign.jwt :as jwt]
    [clojure.tools.logging :as log]
    [clj-time.core :as time]
    [{{project-ns}}.config :refer [config]]))

;; perms
(defn authenticated [request]
  (authenticated? request))

;;example of admin validation
(defn admin [request]
  (and (authenticated request)
       (#{:admin} (:role (:identity request)))))

;; backends
(defn handle-unauthorized
  "A default response constructor for an unauthorized request."
  [request]
  (log/info "Unauthorized access. Headers:" (:headers request))
  (if (authenticated request)
    {:status 403 :headers {} :body "Permission denied"}
    {:status 401 :headers {} :body "Unauthorized"}))

(defn on-error-jwt [_req ex]
  (log/warn "JWT Validation failed" (ex-data ex)))

(defn token-authfn [_ token]
  (log/info "lookup identity for token" token)
  (get-in config [:api_tokens token]))

(defn session-backend []
  (session/session-backend {:unauthorized-handler handle-unauthorized}))

(defn token-backend []
  (token/token-backend {:authfn token-authfn :unauthorized-handler handle-unauthorized :token-name "Bearer"}))

(defn jwt-backend [jwt-key issuer]
  (token/jws-backend {:secret               jwt-key
                      :unauthorized-handler handle-unauthorized
                      :token-name           "Bearer"
                      :on-error             on-error-jwt
                      :options              {:iss issuer
                                             :aud "{{name}}"}}))

;; restructure params
(defn api-access-error [request _]
  (handle-unauthorized request))

(defn wrap-rule [handler rule]
  (-> handler
      (wrap-access-rules {:rules    [{:pattern #"^/api/.*" :handler rule}]
                          :on-error api-access-error})))

(defmethod restructure-param :auth-rules
  [_ rule acc]
  (update-in acc [:middleware] conj [wrap-rule rule]))

(defmethod restructure-param :identity
  [_ binding acc]
  (update-in acc [:letks] into [binding `(:identity ~'+compojure-api-request+)]))


;; swagger basic auth for production mode
(defn basic-authenticated-user [request]
  (if (= ::basic-auth (:identity request))
    true
    (error "Unauthorized")))

(defn basic-auth-rules [{route :swagger_ui_route}]
  (let [pattern (re-pattern (str "^" route "/.*"))]
    (log/info "Installing basic-auth for swagger for url" route)
    [{:pattern pattern :handler basic-authenticated-user}]))

(defn on-basic-auth-error [request value]
  (log/info "Unauthorized swagger access. Headers:" (:headers request))
  {:status  401
   :headers {"WWW-Authenticate" "Basic realm=\"Swagger\""}
   :body    value})

(defn valid-basic-auth? [username password]
  (log/info "comparing" (:swagger_ui_auth config) "with" (str username ":" password))
  (buddy.core.bytes/equals? (:swagger_ui_auth config)
                            (str username ":" password)))

(defn http-basic-authfn [_request {:keys [username password]}]
  (if (valid-basic-auth? username password)
    ::basic-auth
    nil))

(defn wrap-basic-auth
  "middleware to apply basic-auth, using a \"Swagger\" realm "
  [handler cfg]
  (-> handler
      (wrap-access-rules {:rules (basic-auth-rules cfg) :on-error on-basic-auth-error})
      (wrap-authentication (basic/http-basic-backend {:realm "Swagger" :authfn http-basic-authfn}))))


(defn jwt-sign
  ([data]
   (jwt-sign data (:jwt_key config)))
  ([data secret]
   (mount.core/start #'{{project-ns}}.config/config)
   (let [now    (time/now)

         claims {:iss (first (:jwt_issuer config))
                 :iat now
                 :exp (time/plus now (time/days 7))
                 :nbf now}
         obj    (merge claims data)
         token  (jwt/sign obj secret {:alg :hs512})]
     token)))


;;helper method to sign a payload from console
(defn jwt-sign-payload
  "Convenience method to sign an object from the console"
  ([^String payload]
   (jwt-sign-payload payload (:jwt_key config)))
  ([^String payload secret]
   (println "Arguments should have the following edn format: '{:claim \"value\":aud \"audience\"  }'")
   (let [obj   (clojure.edn/read-string payload)
         token (jwt-sign obj secret)]
     (println "Payload\n" (cheshire.core/generate-string obj))
     (println "Please use the following 'Authorization' header:")
     (println (str "Bearer " token))
     token)))


(defn jwt-validate-token
  "Validates a token. See `{{project-ns}}.auth.rules/jwt-backend`"
  ([token]
   (jwt-validate-token token (:jwt_key config)))
  ([token secret]
   (jwt/unsign token secret {:alg :hs512
                             :iss (:jwt_issuer config)
                             :aud "{{name}}"})))

;;;middleware
(defn wrap-auth [handler cfg]
  (let [jwt-backend     (jwt-backend (:jwt_key cfg) (:jwt_issuer cfg))
        session-backend (session-backend)
        token-backend   (token-backend)]
    (-> handler
        (wrap-authorization session-backend)
        (wrap-authentication session-backend)

        (wrap-authorization jwt-backend)
        (wrap-authentication jwt-backend)

        (wrap-authentication token-backend))))
