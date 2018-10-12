(ns {{project-ns}}.services.math
  (:require [manifold.deferred :as d]
            [clojure.core.async :as async]
            [ring.util.http-response :refer :all]))


(defn add [x y] (ok {:total (+ x y)}))

(defn divide [x y]
 (let [d (d/deferred)]
   (d/success! d (ok {:result (/ x y)}))
   d))


(defn mult [x y]
  (let [chan (async/chan)]
    (async/go
        (try
          (async/<!  (async/timeout 1000))
          (async/>! chan (ok {:result (int (* x y))}))
          (catch Throwable e
            (async/>! chan e))
          (finally
            (async/close! chan))))
    chan))
