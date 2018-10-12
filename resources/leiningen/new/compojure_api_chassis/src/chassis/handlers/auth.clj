(ns {{project-ns}}.handlers.auth
  (:require [compojure.api.sweet :refer [context GET POST resource]]
            [ring.util.http-response :refer [ok found bad-request]]            
            [buddy.auth :refer [authenticated?]]
            [{{project-ns}}.auth.rules :as auth-rules]))

(def routes
  (context "/api" []
    (context "/auth" []
      :tags ["auth"]
      :coercion :spec

      (GET "/session" request
        :summary "gets the current user's session.
        Token authentication stores the identity in the session because it is stateful;
        JWT identity is signed on the header"
        (ok (:session request)))


      (POST "/login" []
        ;;TODO session identity would be set up by oauth or any other way
        (assoc-in (ok) [:session :identity] {:_id 1, :username "{{project-ns}}", :role :admin}))
      (POST "/logout" []
        (assoc (ok) :session :identity))

      (GET "/user" []
        :summary "gets the current authenticated user, using any available backend that
        was succesfully authenticated"
        :auth-rules auth-rules/authenticated
        ; :auth-rules {:or [access/authenticated access/other-predicate]}
        ; :auth-rules {:and [access/authenticated access/other-predicate]}
        :identity identity
        (ok identity)))))
