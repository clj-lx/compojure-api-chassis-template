(ns {{project-ns}}.handlers.spec
  (:require [compojure.api.sweet :refer [context GET POST resource]]
            [ring.util.http-response :refer [ok bad-request]]
            [ring.middleware.multipart-params :as multipart]
            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [spec-tools.core :as st]
            [metrics.ring.expose :refer [render-metrics serve-metrics]]
            [{{project-ns}}.models.person :as person]
            [{{project-ns}}.auth.rules :as auth-rules]
            [{{project-ns}}.services.math :as svc])
  (:import [java.io File]))

(s/def ::x spec/int?)
(s/def ::y spec/int?)
(s/def ::total spec/int?)
(s/def ::result spec/int?)
(s/def ::total-map (s/keys :req-un [::total]))
(s/def ::result-map (s/keys :req-un [::result]))

;;person
(s/def ::name spec/string?)
(s/def ::age spec/number?)
(s/def ::error spec/string?)
(s/def ::message spec/string?)
(s/def ::person-map (s/keys :req-un [::name ::age]))
(s/def ::bad-request (s/keys :req-un [::error ::message]))

(s/def ::any? any?)

;; file upload
(s/def ::filename spec/string?)
(s/def ::content-type spec/string?)
(s/def ::size spec/int?)
(s/def ::tempfile #(instance? File %))
(s/def ::file (st/spec {:spec (s/keys :req-un [::filename ::content-type ::size]
                                      :opt-un [::tempfile])
                        :json-schema/type "file"}))

;; change delimiters so it doesnt conflict with clj
{{=<% %>=}}

(def routes
  (context "/api" []
    (context "/spec" []
      :tags ["spec"]
      :coercion :spec

      (POST "/person" request
        :summary "puts a new person"
        :return ::person-map
        :body-params [body :- ::person-map]
        :responses {200 {:schema ::person-map :description "happy path"}
                    412 {:schema ::bad-request :description "invalidation path"}}
        (let [{name :name age :age} body]
          (if (or (nil? name) (nil? age))
            (bad-request {:error "missing required fields" :message "please fill name and age"})
            (ok (into {} (person/->Person name age)))))) ;;maps are converted to json, plain records are converted to strings!
             
      (GET "/person" request
        :summary "gets the person, using a custom encoder"
        (ok (person/->Person "dude" 21)))

      (GET "/session" request
        :summary "gets the session"
        (ok (:session request)))

      (GET "/async/:id" [id :as request]
        :summary "async handlers with parameters"
        (fn [req res raise]
          (res id)))

      (GET "/metrics" []
        :summary "Application level metrics."
        serve-metrics)


      (POST "/file" []
        :summary "post a file"
        :multipart-params [file :- ::file]
        :return ::any?
        :middleware [multipart/wrap-multipart-params]
        (ok (do (println file)
                (dissoc file :tempfile))))

      (GET "/plus" []
        :summary "plus with clojure.spec. this route is authenticated"
        :query-params [x :- ::x, {y :- ::y 0}]
        :return ::total-map
        :auth-rules auth-rules/authenticated
        (svc/add x y))

      (GET "/divide" []
        :return ::result-map
        :query-params [x :- (st/create-spec {:spec ::x :description "a number"})
                       {y :- ::y 0}]
        :summary "divide two numbers from each other using manifold deferred, timeout 1s"
        :description "result should be an integer, otherwise it cannot be coerced"
        :responses {200 {:schema ::result-map
                         :description "Divides two numbers"}
                    500 {:schema ::any?
                         :description "Internal error"}}
        (svc/divide x y))

      (GET "/multiply" []
        :return ::result-map
        :query-params [x :- ::x, {y :- ::y 0}]
        :summary "multiply two numbers together using core.async channels"
        :description "the returning core.asycn channel is also coerced according to the spec"
        (svc/mult x y))

      (context "/data-plus" []
        (resource
          {:post
           {:summary    "data-driven plus with clojure.spec"
            :parameters {:body-params (s/keys :req-un [::x ::y])}
            :responses  {200 {:schema ::total-map}}
            :handler    (fn [{{:keys [x y]} :body-params}]
                          (ok {:total (+ x y)}))}})))))
;;reset delimiter
<%={{ }}=%>
