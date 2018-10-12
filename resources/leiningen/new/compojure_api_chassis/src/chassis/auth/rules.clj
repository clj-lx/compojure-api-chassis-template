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
            [{{project-ns}}.config :refer [config]]))
;; perms
(defn authenticated [request]
  (authenticated? request))

(defn admin [request]
  (and (authenticated request)
       (#{:admin} (:role (:identity request)))))

;; backends
(defn session-backend [_cfg]
  ;; By default responds with 401 or 403 if unauthorized
  (session/session-backend))

(defn token-authfn [_ token]
  ;;TODO db lookup or whatever
  (get-in config [:api_tokens token]))

(defn token-backend [_cfg]
  (token/token-backend {:authfn token-authfn}))

(defn jwt-backend [cfg]
  (token/jws-backend {:secret (:jwt_key cfg)}))

;; restructure params
(defn api-access-error [request _]
  (log/info "Unauthorized api access. Headers:" (:headers request))
  {:status 403 :headers {} :body "Unauthorized"})

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

(def basic-auth-rules
  [{:pattern #"^/swagger/.*" :handler basic-authenticated-user}])

(defn on-basic-auth-error [request value]
  (log/info "Unauthorized swagger access. Headers:" (:headers request))
  {:status  401
   :headers {"WWW-Authenticate" "Basic realm=\"Swagger\""}
   :body    value})

(defn valid-basic-auth? [username password]
  (buddy.core.bytes/equals? (:swagger_ui_auth config)
                            (str username ":" password)))

(defn http-basic-authfn [_request {:keys [username password]}]
  (if (valid-basic-auth? username password)
    ::basic-auth
    nil))

(defn wrap-basic-auth [handler _]
  (-> handler
      (wrap-access-rules {:rules basic-auth-rules :on-error on-basic-auth-error})
      (wrap-authentication (basic/http-basic-backend {:realm "Swagger" :authfn http-basic-authfn}))))


;;helper method to sign a payload from console
(defn jwt-sign
  "Convenience method to sign an object from the console"
  [payload]
  (println "Arguments should have the following edn format: '{:user \"user\" :role :jwt-user}'")
  (mount.core/start #'{{project-ns}}.config/config)
  (let [data (clojure.edn/read-string payload)
        token (jwt/sign data (:jwt_key config))]
    (println "Please use the following 'Authorization' header:")
    (println (str "Token " token))
    token))


;;;middleware
(defn wrap-auth [handler cfg]
  (let [session-backend (session-backend cfg)
        jwt-backend (jwt-backend cfg)
        token-backend (token-backend cfg)]
    (-> handler
        (wrap-authorization session-backend)
        (wrap-authentication session-backend)

        (wrap-authorization jwt-backend)
        (wrap-authentication jwt-backend)

        (wrap-authentication token-backend))))
