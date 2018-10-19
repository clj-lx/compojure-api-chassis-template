(ns {{project-ns}}.models.person
  (:require {{#jsonista-hook?}}[jsonista.core :as json]{{/jsonista-hook?}}
            {{#cheshire-hook?}}[cheshire.generate :as json]{{/cheshire-hook?}}))
;; this is an example of how to use a custom encoder with compojure-api
(defrecord Person [name age])

{{#jsonista-hook?}}
;; use jsonista
(defn encode [person gen]
  (.writeString gen (.writeValueAsString (json/object-mapper) {:root person})))

(def encoder {Person (fn [person gen] (encode person gen))})
{{/jsonista-hook?}}


{{#cheshire-hook?}}
;; use cheshire
(extend-protocol json/JSONable
  Person
  (to-json [this jg]
    (.writeString jg "xxx")))
{{/cheshire-hook?}}
