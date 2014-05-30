;
; (C) Copyright 2014 Ben Gimpert (ben@somethingmodern.com)
;
; All rights reserved. This program and the accompanying materials
; are made available under the terms of the Eclipse Public License v1.0
; which accompanies this distribution, and is available at
; http://www.eclipse.org/legal/epl-v10.html
;
(ns skream.core-srv-rest
  (:use (ring.adapter jetty)
        (compojure core))
  (:require [clojure.data.json :refer [json-str]])
  (:require [ring.middleware.params :refer [wrap-params]])
  (:require [ring.middleware.session :refer [wrap-session]])
  (:require [ring.middleware.keyword-params :refer [wrap-keyword-params]])
  (:require [ring.middleware.json :refer [wrap-json-response]])
  (:require [skream.core :refer :all])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Web Service REST API
;;
(defn get-session-skream [session sk-id]
  (let [sk-map (:sk-map session)]
    ((keyword sk-id) sk-map)))

(defn get-response-skream-session [prev-session sk-id sk]
  (let [prev-sk-map (or (:sk-map prev-session) {})
        new-sk-map (assoc prev-sk-map (keyword sk-id) sk)]
    (assoc prev-session :sk-map new-sk-map)))

(defn get-jsonable-skream [sk]
  (loop [current-keys (keys sk)
         current-jsonable-sk (with-meta {} (meta sk))]
    (if (empty? current-keys)
      current-jsonable-sk
      (let [key1 (first current-keys)
            key-el-fn (fn [key-el]
                        (if (keyword? key-el)
                          (clojure.string/replace (subs (str key-el) 1) "-" "_")
                          (str key-el)))
            jsonable-key1 (if (not (vector? key1)) key1
                            (clojure.string/join "_" (map key-el-fn key1)))]
        (recur (rest current-keys)
               (assoc current-jsonable-sk jsonable-key1 (get sk key1)))))))

(defn get-json-response [body new-session]
  {
   :content-type "application/json"
   :body body
   :session new-session
  })

(defroutes skream-routes
  (  POST "/skream" { :keys [params session] :as request }
          (let [new-sk-id (get-uuid)
                new-sk (track-default (create-skream))]
            (get-json-response new-sk-id (get-response-skream-session session new-sk-id new-sk))))
  
  (  POST "/track/:sk-id" { :keys [params session] :as request }
          (let [sk-id (:sk-id params)
                sk (get-session-skream session sk-id)
                stat (:stat params)
                stat-args (loop [i 1 current-stat-args []]
                            (let [stat-arg-param (get params (keyword (str "arg" i)))]
                              (if stat-arg-param
                                (let [stat-arg (read-string stat-arg-param)]
                                  (recur (inc i) (conj current-stat-args stat-arg)))
                                current-stat-args)))
                track-fn-symbol (symbol (str "skream.core/track-" (clojure.string/replace stat "_" "-")))
                track-fn (resolve track-fn-symbol)
                new-sk (if (nil? track-fn) sk (apply track-fn (cons sk stat-args)))]
            (get-json-response (get-jsonable-skream new-sk) (get-response-skream-session session sk-id new-sk))))
  
  (   GET "/skream" { :keys [params session] :as request }
          (let [sk-map (:sk-map session)
                sk-ids (keys sk-map)]
            (get-json-response sk-ids session)))
  
  (   GET "/skream/:sk-id" { :keys [params session] :as request }
          (let [sk-id (:sk-id params)
                sk (get-session-skream session sk-id)]
            (get-json-response (get-jsonable-skream sk) (get-response-skream-session session sk-id sk))))
  
  (   PUT "/skream/:sk-id" { :keys [params session] :as request }
          (let [sk-id (:sk-id params)
                sk (get-session-skream session sk-id)
                x (read-string (:x params))
                new-sk (add-num sk x)]
            (get-json-response (str x) (get-response-skream-session session sk-id new-sk))))
  
  (DELETE "/skream/:sk-id" { :keys [params session] :as request }
          (let [sk-id (:sk-id params)
                prev-sk-map (or (:sk-map session) {})
                new-sk-map (dissoc prev-sk-map (keyword sk-id))
                new-session (assoc session :sk-map new-sk-map)]
            (get-json-response sk-id new-session)))
  )

(defn start-web-server []
  (run-jetty (-> #'skream-routes
               wrap-json-response
               wrap-session
               wrap-keyword-params
               wrap-params) { :port 8080 }))

