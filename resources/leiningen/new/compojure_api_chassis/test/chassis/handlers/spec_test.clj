(ns {{project-ns}}.handlers.spec-test
  (:require [clojure.test :refer :all]
            [cheshire.core :as cheshire]
            [ring.mock.request :as mock]
            [mount.core :as mount]
            [{{project-ns}}.handler :as handler]
            [{{project-ns}}.auth.rules :as rules]
            [{{project-ns}}.config :refer [config]]))


(defn parse-body [body]
  (cheshire/parse-string (slurp body) true))

(defn  create-request [url & {:keys [method auth accept]
                              :or {method :get
                                   auth (str "Bearer " (first (keys (:api_tokens config))))
                                   accept "application/json"}}]
  (-> (mock/request method url)
      (mock/header "accept" accept)
      (mock/header "authorization" auth)))

;; make sure to start config, because auth backend depends on it
(defn config-fixture [f]
  (mount/start #'{{project-ns}}.config/config)
  (f)
  (mount/stop))

(use-fixtures :once config-fixture)

(deftest spec_plus
  (let [app       (handler/app config)
        token     (first (keys (:api_tokens config)))
        jwt_token (buddy.sign.jwt/sign {:user "test" :role :jwt} (:jwt_key config))]

    (testing "should 200 OK when submitting a hardcoded token"
      (let [req  (create-request "/api/spec/plus?x=1&y=2" :auth (str "Bearer " token))
            res  (app req)
            body (parse-body (:body res))]
        (is (= 200 (:status res)))
        (is (= {:total 3} body))
        (is (clojure.string/starts-with? (get-in res [:headers "Content-Type"]) "application/json"))))

    (testing "should 200 OK when submitting a jwt token"
      (let [req  (create-request "/api/spec/plus?x=1&y=2" :auth (str "Bearer " jwt_token))
            res  (app req)
            body (parse-body (:body res))]
        (is (= 200 (:status res)))
        (is (= {:total 3} body))
        (is (clojure.string/starts-with? (get-in res [:headers "Content-Type"]) "application/json"))))

    (testing "should fail with 400 when arguments don't conform"
      (let [req (create-request "/api/spec/plus?x=1&y=xxx")
            res (app req)]
        (is (= 400 (:status res)))))

    (testing "should fail with 401 when token is invalid"
      (let [req (create-request "/api/spec/plus?x=1&y=2" :auth "invalid")
            res (app req)]
        (is (= 401 (:status res)))))))
