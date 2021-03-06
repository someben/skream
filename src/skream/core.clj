 ;
; (C) Copyright 2014 Ben Gimpert (ben@somethingmodern.com)
;
; All rights reserved. This program and the accompanying materials
; are made available under the terms of the Eclipse Public License v1.0
; which accompanies this distribution, and is available at
; http://www.eclipse.org/legal/epl-v10.html
;
(ns skream.core
  (:use clojure.math.numeric-tower)
  (:import (java.security MessageDigest))
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Math Routines 
;;
(defn get-mean [xs]
  (/ (apply + xs) (count xs)))

(defn get-factorial [x]
  (loop [current-x (bigint x) current-prod 1]
    (if (= 1 current-x)
      current-prod
      (recur (dec current-x) (* current-x current-prod)))))

(defn get-combinations [n k]
  (/ (get-factorial n) (* (get-factorial k) (get-factorial (- n k)))))

(defn get-logarithm-e [x]
  (java.lang.Math/log x))

(defn get-logarithm-2 [x]
  (/ (java.lang.Math/log x) (java.lang.Math/log 2)))

(defn get-power [x pow]
  (if (= pow 1/2)
    (sqrt x)
    (expt x pow)))

(defn get-binary-str
  ([x] (get-binary-str x nil))
  ([x width]
    (let [bin-str (Long/toBinaryString x)]
      (if (nil? width) bin-str
        (let [width-diff (max (- width (count bin-str)) 0)]
          (str (apply str (repeat width-diff "0")) bin-str))))))

(defn get-inverse-normal-distribution [p]
  (if (or (<= p 0) (>= p 1)) nil
    (cern.jet.stat.Probability/normalInverse p)))

(defn get-rand-normal []
  (get-inverse-normal-distribution (rand)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions (e.g. hashing)
;;
(defn get-pid []
  (let [mx-name (.getName (java.lang.management.ManagementFactory/getRuntimeMXBean))
        pid-s (first (clojure.string/split mx-name #"@"))]
    (read-string pid-s)))
  
(defn println-stderr [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn meta-get-in
  ([m ks] (meta-get-in m ks nil))
  ([m ks not-found] (get-in (meta m) ks not-found)))
  
(defn merge-with-meta [& maps]
  (if (nil? maps) nil
    (let [meta-maps (map meta maps)]
      (with-meta (apply merge maps) (apply merge meta-maps)))))

(defn get-time [] (/ (System/currentTimeMillis) 1000))

(defn get-uuid [] (.toString (java.util.UUID/randomUUID)))

(defn get-vector-of-n-fns [x-fn n]
  (loop [current-n n current-vector []]
         (if (zero? current-n)
           current-vector
           (recur (dec current-n) (conj current-vector (x-fn))))))

(defn get-vector-of-n [x n]
  (get-vector-of-n-fns (fn [] x) n))

(defn get-sha1-hash-bytes
  ([o] (get-sha1-hash-bytes o 0))
  ([o salt]
    (let [o-bytes (.getBytes (with-out-str (pr [salt o])))
          signed-digest (.digest (MessageDigest/getInstance "SHA1") o-bytes)
          digest (map (fn [x] (bit-and x 0xFF)) signed-digest)]
      (apply vector digest))))

(defn get-sha1-hash-string [& args]
  (let [hash-bytes (apply get-sha1-hash-bytes args)]
    (apply str (map char hash-bytes))))

(defn get-32bit-sha1-hash [& args]
  (let [hash-bytes (apply get-sha1-hash-bytes args)]
    (bit-or
      (nth hash-bytes 0)
      (bit-shift-left (nth hash-bytes 1) 8)
      (bit-shift-left (nth hash-bytes 2) 16)
      (bit-shift-left (nth hash-bytes 3) 24))))

(defn get-jenkins-hash [x]
  (let [str-x (str x)
        count-str-x (count str-x)
        to-32bit-fn (fn [x] (bit-and 0xFFFFFFFF x))
        loop-hash1 (loop [i 0 hash 0]
                     (if (>= i count-str-x) hash
                       (recur (inc i)
                              (let [ch-code (int (nth str-x i))
                                    next-hash1 (to-32bit-fn (+ hash ch-code))
                                    next-hash2 (to-32bit-fn (+ next-hash1 (bit-shift-left next-hash1 10)))
                                    next-hash3 (to-32bit-fn (bit-xor next-hash2 (unsigned-bit-shift-right next-hash2 6)))]
                                next-hash3))))
        loop-hash2 (to-32bit-fn (+ loop-hash1 (bit-shift-left loop-hash1 3)))
        loop-hash3 (to-32bit-fn (bit-xor loop-hash2 (unsigned-bit-shift-right loop-hash2 11)))
        loop-hash4 (to-32bit-fn (+ loop-hash3 (bit-shift-left loop-hash3 15)))]
    loop-hash4))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Foundations
;;
(defn create-skream [] {})

(defn track-stat
  ([sk stat add-fn] (track-stat sk stat add-fn nil :add-fn-map))
  ([sk stat add-fn init-val] (track-stat sk stat add-fn init-val :add-fn-map))
  ([sk stat add-fn init-val add-fn-map-key]
    (let [new-sk (assoc sk stat init-val)
          prev-meta-add-fn-map (add-fn-map-key (meta sk))
          meta-add-fn-map (assoc prev-meta-add-fn-map stat add-fn)]
      (vary-meta new-sk assoc add-fn-map-key meta-add-fn-map))))

(defn alias-nested-stat [sk target-stat src-stats]
  (let [prev-meta-alias-map (:alias-map (meta sk))
        meta-alias-map (assoc prev-meta-alias-map target-stat src-stats)]
    (vary-meta sk assoc :alias-map meta-alias-map)))
  
(defn alias-stat [sk target-stat src-stat]
  (alias-nested-stat sk target-stat [src-stat]))

(defn add-aliases [sk alias-map]
  (apply merge (pmap (fn [alias-target-stat]
                       (let [alias-src-stats (get alias-map alias-target-stat)
                             alias-src-stats-val (get-in sk alias-src-stats)]
                         { alias-target-stat alias-src-stats-val }))
                     (keys alias-map))))

(defn add-num
  ([sk] sk)
  ([sk x]
    (let [added-sk (apply merge-with-meta (pmap (fn [add-fn] (add-fn sk x))
                                                (vals (:add-fn-map (meta sk)))))
          aliased-sk (add-aliases added-sk (:alias-map (meta sk)))]
      (merge (merge-with-meta sk added-sk) aliased-sk)))
  ([sk x & xs]
    (loop [current-xs xs
           current-sk (add-num sk x)]
      (if (empty? current-xs) current-sk
        (recur (rest current-xs) (add-num current-sk (first current-xs)))))))

(defn get-changed-keys [old-sk new-sk]
  (loop [current-keys (keys old-sk)
         current-changed-keys []]
    (if (empty? current-keys)
      current-changed-keys
      (let [current-key (first current-keys)]
        (recur (rest current-keys)
               (let [old-val (get old-sk current-key)
                     new-val (get new-sk current-key)]
                 (if (= old-val new-val)
                   current-changed-keys
                   (conj current-changed-keys current-key))))))))
  
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic Stats
;;
(defn track-count [sk]
  (let [add-fn (fn [prev-sk x]
                 { :count (inc (:count prev-sk)) })]
    (track-stat sk :count add-fn 0)))

(defn track-last [sk]
  (let [add-fn (fn [prev-sk x]
                 { :last x })]
    (track-stat sk :last add-fn nil)))

(defn track-min [sk]
  (let [add-fn (fn [prev-sk x]
                 { :min (apply min (remove nil? [(:min prev-sk) x])) })]
    (track-stat sk :min add-fn)))

(defn track-max [sk]
  (let [add-fn (fn [prev-sk x]
                 { :max (apply max (remove nil? [(:max prev-sk) x])) })]
    (track-stat sk :max add-fn)))

(defn track-sum [sk]
  (let [add-fn (fn [prev-sk x]
                 { :sum (+ (:sum prev-sk) x) })]
    (track-stat sk :sum add-fn 0)))

(defn get-new-mean [prev-sk x]
  (let [prev-mean (:mean prev-sk)
        prev-count (:count prev-sk)]
    (+ prev-mean (/ (- x prev-mean) (inc prev-count)))))

(defn track-mean [sk]
  (let [dep-sk (-> sk track-count)
        add-fn (fn [prev-sk x]
                 { :mean (if (zero? (:count prev-sk)) x
                           (get-new-mean prev-sk x)) })]
    (track-stat dep-sk :mean add-fn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Moments
;;
(defn get-new-unstandardized-moment [prev-sk p x]  ; http://prod.sandia.gov/techlib/access-control.cgi/2008/086212.pdf
  (let [prev-count (:count prev-sk)
        prev-mean (:mean prev-sk)
        delta (- x prev-mean)
        n (inc prev-count)
        prev-moment-p (or (get prev-sk [:unstd-moment p]) 0)
        prev-lo-moment (loop [k 1 current-sum 0]
                         (if (> k (- p 2))
                           current-sum
                           (let [comb-k-p (get-combinations p k)
                                 prev-moment-p-minus-k (get prev-sk [:unstd-moment (- p k)])
                                 frac-prev (get-power (/ (- delta) n) k)]
                             (recur (inc k)
                                    (+ current-sum (* comb-k-p prev-moment-p-minus-k frac-prev))))))
        frac-p (get-power (* (/ (dec n) n) delta) p)
        frac-p1 (- 1 (get-power (/ -1 (dec n)) (dec p)))
        frac-prod (* frac-p frac-p1)
        new-unstd-moment-p (+ prev-moment-p prev-lo-moment frac-prod)]
    new-unstd-moment-p))

(defn track-unstandardized-moment [sk p]
  (if (< p 2) sk
    (let [dep-sk (-> sk track-count track-mean)
          add-fn (fn [prev-sk x]
                   { [:unstd-moment p] (if (zero? (:count prev-sk)) 0
                                         (get-new-unstandardized-moment prev-sk p x)) })]
      (track-stat (track-unstandardized-moment dep-sk (dec p)) [:unstd-moment p] add-fn))))

(defn track-unstandardized-moment-2 [sk] (track-unstandardized-moment sk 2))
(defn track-unstandardized-moment-3 [sk] (track-unstandardized-moment sk 3))
(defn track-unstandardized-moment-4 [sk] (track-unstandardized-moment sk 4))

(defn get-new-moment [prev-sk p x]
  (let [new-unstd-moment-p (get-new-unstandardized-moment prev-sk p x)
        new-unstd-moment-2 (get-new-unstandardized-moment prev-sk 2 x)
        new-count (:count prev-sk)]
    (if (zero? new-unstd-moment-2) 0
      (let [std-scale (get-power (/ new-unstd-moment-2 new-count) (* p 1/2))]
        (/ new-unstd-moment-p std-scale)))))

(defn track-moment [sk p]
  (if (< p 2) sk
    (let [dep-sk (track-unstandardized-moment (-> sk track-unstandardized-moment-2 track-count) p)
          add-fn (fn [prev-sk x]
                   { [:moment p] (if (zero? (:count prev-sk)) 0
                                   (get-new-moment prev-sk p x)) })]
      (track-stat (track-moment dep-sk (dec p)) [:moment p] add-fn))))

(defn track-moment-2 [sk] (track-moment sk 2))
(defn track-moment-3 [sk] (track-moment sk 3))
(defn track-moment-4 [sk] (track-moment sk 4))

(defn track-sum-sq-diffs [sk]
  (let [dep-sk (track-moment-2 sk)]
    (alias-stat dep-sk :sum-sq-diffs [:unstd-moment 2])))

(defn track-skew [sk]
  (let [dep-sk (-> sk track-moment-3 track-count track-sum-sq-diffs)
        add-fn (fn [prev-sk x]
                 (let [prev-count (:count prev-sk)]
                   { :skew (if (< prev-count 2) 0
                             (let [new-moment-3 (get-new-moment prev-sk 3 x)
                                   n (inc prev-count)
                                   skew-scale (/ (inc prev-count) (* (- n 1) (- n 2)))]
                               (* new-moment-3 skew-scale))) }))]
    (track-stat dep-sk :skew add-fn)))

(defn track-kurtosis [sk]
  (let [dep-sk (-> sk track-moment-4 track-count track-sum-sq-diffs)
        add-fn (fn [prev-sk x]
                 (let [prev-count (:count prev-sk)]
                   { :kurt (if (< prev-count 3) 0
                             (let [new-moment-4 (get-new-moment prev-sk 4 x)
                                   n (inc prev-count)
                                   kurt-scale (/ (* n (inc n)) (* (- n 1) (- n 2) (- n 3)))
                                   kurt-adj (/ (* 3 (* (- n 1) (- n 1))) (* (- n 2) (- n 3)))]
                               (- (* new-moment-4 kurt-scale) kurt-adj))) }))]
    (track-stat dep-sk :kurt add-fn)))

(defn track-variance-helper [sk stat scale-fn pow]
  (let [dep-sk (-> sk track-unstandardized-moment-2 track-count)
        add-fn (fn [prev-sk x]
                 { stat (if (zero? (:count prev-sk)) 0
                          (let [unstd-moment-2 (get-new-unstandardized-moment prev-sk 2 x)
                                scale (scale-fn (:count prev-sk))]
                            (get-power (/ unstd-moment-2 scale) pow))) })]
    (track-stat dep-sk stat add-fn)))

(defn track-variance [sk]
  (track-variance-helper sk :var identity 1))

(defn track-population-variance [sk]
  (track-variance-helper sk :pop-var inc 1))

(defn track-standard-deviation [sk]
  (track-variance-helper sk :stdev identity 1/2))

(defn track-population-standard-deviation [sk]
  (track-variance-helper sk :pop-stdev inc 1/2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Averages
;;
(defn track-exponential-moving-average
  ([sk] (track-exponential-moving-average sk 0.125))
  ([sk alpha]
    (let [stat [:ema alpha]
          dep-sk (-> sk track-count)
          add-fn (fn [prev-sk x]
                   { stat (if (zero? (:count prev-sk)) x
                            (+ (* alpha x) (* (- 1 alpha) (get prev-sk stat)))) })]
      (track-stat dep-sk stat add-fn))))

(defn get-new-window [prev-sk n x]
  (let [prev-win (get prev-sk [:win n])
        added-win (conj prev-win x)
        new-win (if (> (count added-win) n)
                  (subvec added-win 1) added-win)]
    new-win))

(defn track-window
  ([sk] (track-window sk 10))
  ([sk n]
    (let [stat [:win n]
          add-fn (fn [prev-sk x]
                   { stat (get-new-window prev-sk n x) })]
      (track-stat sk stat add-fn []))))

(defn track-moving-average
  ([sk] (track-moving-average sk 10))
  ([sk n]
    (let [stat [:ma n]
          win-stat [:win n]
          dep-sk (track-window sk n)
          add-fn (fn [prev-sk x]
                   (let [new-win (get-new-window prev-sk n x)]
                     { stat (get-mean new-win) }))]
      (track-stat dep-sk stat add-fn))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sketch Utility Functions
;;
(defn create-sketch [num-rows num-cols]
  (get-vector-of-n (get-vector-of-n 0 num-cols) num-rows))

(defn modify-sketch [sketch row-i col-i mod-fn]
  (let [sketch-row (nth sketch row-i)
        current-val (nth sketch-row col-i)
        new-val (mod-fn current-val)
        new-sketch-row (assoc sketch-row col-i new-val)
        new-sketch (assoc sketch row-i new-sketch-row)]
    new-sketch))
  
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Membership Estimate
;;
(defn track-member-ish?
  ([sk] (track-member-ish? sk 6 8))
  ([sk b k]
    (let [stat [:bloom b k]
          b-mask (dec (get-power 2 b))
          empty-sketch (create-sketch 1 k)
          add-fn (fn [prev-sk x]
                   (let [prev-sketch (get prev-sk stat)
                         new-sketch (loop [i 0 current-sketch prev-sketch]
                                      (if (>= i k) current-sketch
                                        (let [x-hash (get-32bit-sha1-hash x i)
                                              col-i (bit-and x-hash b-mask)]
                                          (recur (inc i)
                                                 (modify-sketch current-sketch 0 i
                                                                (fn [val] (bit-set val col-i)))))))]
                     { stat new-sketch }))]
      (track-stat sk stat add-fn empty-sketch))))

(defn member-ish?
  ([sk x] (member-ish? sk 6 8 x))
  ([sk b k x]
    (let [stat [:bloom b k]
          b-mask (dec (get-power 2 b))
          sketch-row (nth (get sk stat) 0)]
      (loop [i 0]
        (if (>= i k) true
          (let [x-hash (get-32bit-sha1-hash x i)
                col-i (bit-and x-hash b-mask)]
            (if (not (bit-test (nth sketch-row i) col-i))
              false
              (recur (inc i)))))))))
  
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Online Probability Distribution Estimates
;;
(defn track-element-counts-ish
  ([sk] (track-element-counts-ish sk 256 128))
  ([sk d w]
    (let [empty-sketch (create-sketch d w)
          add-fn (fn [prev-sk x]
                   (let [prev-sketch (get prev-sk [:cm-sketch d w])
                         new-sketch (loop [current-row-i 0
                                           current-sketch prev-sketch]
                                      (if (>= current-row-i d)
                                        current-sketch
                                        (let [col-i (mod (get-32bit-sha1-hash x current-row-i) (dec w))]
                                          (recur (inc current-row-i)
                                                 (modify-sketch current-sketch current-row-i col-i inc)))))]
                     { [:cm-sketch d w] new-sketch }))]
      (track-stat sk [:cm-sketch d w] add-fn empty-sketch))))

(defn get-element-count-ish
  ([sk x] (get-element-count-ish sk 256 128 x))
  ([sk d w x]
    (let [sketch (get sk [:cm-sketch d w])]
      (loop [current-row-i 0
             current-min-count nil]
        (if (>= current-row-i (dec d))
          current-min-count
          (let [col-i (mod (get-32bit-sha1-hash x current-row-i) (dec w))
                row-col-val (nth (nth sketch current-row-i) col-i)]
            (recur (inc current-row-i)
                   (if (nil? current-min-count) row-col-val
                     (min current-min-count row-col-val)))))))))

(defn track-element-range-count [sk range-min range-max]
  (let [stat [:range-count range-min range-max]
        add-fn (fn [prev-sk x]
                 (let [prev-range-count (get prev-sk stat)
                       is-match (and (or (nil? range-min) (>= x range-min))
                                     (or (nil? range-max) (< x range-max)))]
                   { stat (if is-match (inc prev-range-count) prev-range-count) }))]
    (track-stat sk stat add-fn 0)))

(defn track-element-normal-range-count [sk range-min-stdevs range-max-stdevs]
  (let [dep-sk (-> sk track-unstandardized-moment-2 track-mean track-count)
        stat [:normal-range-count range-min-stdevs range-max-stdevs]
        add-fn (fn [prev-sk x]
                 { stat (if (zero? (:count prev-sk)) 0
                          (let [prev-range-count (get prev-sk stat)
                                new-mean (get-new-mean prev-sk x)
                                new-unstd-moment-2 (get-new-unstandardized-moment prev-sk 2 x)
                                new-stdev (get-power (/ new-unstd-moment-2 (:count prev-sk)) 1/2)
                                x-stdevs (/ (- x new-mean) new-stdev) 
                                is-match (and (or (nil? range-min-stdevs) (>= x-stdevs range-min-stdevs))
                                              (or (nil? range-max-stdevs) (< x-stdevs range-max-stdevs)))]
                            (if is-match (inc prev-range-count) prev-range-count))) })]
    (track-stat dep-sk stat add-fn 0)))

(defn range-count-key? [key hist-stat-prefix]
  (and (vector? key)
       (= (first key) hist-stat-prefix)))

(defn get-changed-range-count-keys [old-sk new-sk hist-stat-prefix]
  (let [changed-keys (get-changed-keys old-sk new-sk)]
    (filter (fn [key] (range-count-key? key hist-stat-prefix)) changed-keys)))

(defn track-histogram [sk min-x max-x num-buckets]
  (let [range-inc (/ (- max-x min-x) num-buckets)]
    (loop [range-min min-x current-sk sk]
      (if (>= range-min max-x)
        current-sk
        (recur (+ range-min range-inc)
               (track-element-range-count current-sk range-min (+ range-min range-inc)))))))

(defn track-normal-histogram [sk num-buckets]
  (let [prob-inc (/ 1 num-buckets)  ; cover 100% of the probability distribution
        stdev-steps (loop [p 0 current-stdev-steps []]
                      (if (>= p 1)
                        (conj current-stdev-steps nil)
                        (let [inv-norm (get-inverse-normal-distribution p)]  ; p = 0% -> nil
                          (recur (+ p prob-inc)
                                 (conj current-stdev-steps inv-norm)))))]
    (loop [i 0 current-sk sk]
      (if (>= i (dec (count stdev-steps)))
        current-sk
        (recur (inc i)
               (track-element-normal-range-count current-sk (nth stdev-steps i) (nth stdev-steps (inc i))))))))

(defn track-distinct-value-count-ish [sk b]  ; http://research.neustar.biz/2012/10/25/sketch-of-the-day-hyperloglog-cornerstone-of-a-big-data-infrastructure/
  (let [stat [:hll b]
        dep-sk (track-count sk)
        m (get-power 2 b)
        empty-sketch (create-sketch 1 m)
        alpha (cond
                (= m 16) 0.673
                (= m 32) 0.697
                (= m 64) 0.709
                :else (/ 0.7213 (+ 1 (/ 1.079 m))))
        str-count-sorter-fn (fn [a b] (compare (count a) (count b)))
        add-fn (fn [prev-sk x]
                 (let [current-sketch (:sketch (get prev-sk stat))
                       x-hash (get-32bit-sha1-hash x)
                       sketch-i (bit-and x-hash (dec m))
                       x-hash-bin-str (get-binary-str x-hash 32)
                       suffix-len (- 32 b)
                       x-hash-bin-str-suffix (subs x-hash-bin-str 0 suffix-len)
                       binary0-run-count (loop [i (dec suffix-len)
                                                current-count 0]
                                           (if (or (= -1 i) (= \1 (nth x-hash-bin-str-suffix i))) current-count
                                             (recur (dec i) (inc current-count))))
                       modify-sketch-fn (fn [prev-val] (max prev-val (inc binary0-run-count)))  ; add one to the number of leading zeros, like the HLL paper
                       new-sketch (modify-sketch current-sketch 0 sketch-i modify-sketch-fn)

                       new-sketch-row (nth new-sketch 0)
                       reg-sum (loop [i 0 current-sum 0]
                                 (if (>= i m) current-sum
                                   (recur (inc i)
                                          (+ current-sum (get-power 2 (- (nth new-sketch-row i)))))))
                       dv-est (* alpha (get-power m 2) (/ 1 reg-sum))
                       dv (cond
                            (< dv-est (* 5/2 m))
                            (let [num-zero (count (filter zero? new-sketch-row))]
                              (if (zero? num-zero)
                                dv-est
                                (* m (get-logarithm-e (/ m num-zero)))))
                            (<= dv-est (* 1/30 (get-power 2 32)))
                            dv-est
                            :else
                            (* (- (get-power 2 32)) (get-logarithm-e (- 1 (/ dv-est (get-power 2 32))))))
                       dv (round dv)]
                   { stat { :dv dv :sketch new-sketch } }))]
    (track-stat dep-sk stat add-fn { :dv 0 :sketch empty-sketch })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; P2 Quantile Estimation
;;
(defn track-quantile-ish [sk p]  ; http://www.cs.wustl.edu/~jain/papers/ftp/psqr.pdf & https://github.com/absmall/p2
  (let [marker-count 5
        quantile-stat [:quantile p]
        dep-sk (track-count (track-window sk marker-count))
        
        sign-fn (fn [x] (if (>= x 0) +1 -1))
        parabolic-fn (fn [ns qs i d]
                       (let [frac-1-scale (+ (nth ns i) (- (nth ns (dec i))) d)
                             frac-1 (/ (- (nth qs (inc i)) (nth qs i))
                                       (- (nth ns (inc i)) (nth ns i)))
                             frac-2-scale (+ (nth ns (inc i)) (- (nth ns i)) (- d))
                             frac-2 (/ (- (nth qs i) (nth qs (dec i)))
                                       (- (nth ns i) (nth ns (dec i))))
                             frac-scale (/ d (- (nth ns (inc i)) (nth ns (dec i))))
                             frac (+ (* frac-1-scale frac-1)
                                     (* frac-2-scale frac-2))]
                         (+ (nth qs i) (* frac-scale frac))))
        linear-fn (fn [ns qs i d]
                    (let [frac (/ (- (nth qs (+ i d)) (nth qs i))
                                  (- (nth ns (+ i d)) (nth ns i)))]
                      (+ (nth qs i) (* d frac))))

        add-init-fn (fn [prev-sk x]
                      (let [new-win (conj (get prev-sk [:win marker-count]) x)
                            sorted-new-win (apply vector (sort compare new-win))
                            init-ns (apply vector (rest (take (inc marker-count) (range))))
                            init-dns (apply vector (map (fn [init-dn] (/ init-dn (dec marker-count))) (take marker-count (range))))
                            init-nps (loop [i 0 res []]
                                       (if (>= i marker-count) res
                                         (recur (inc i)
                                                (conj res (inc (* (dec marker-count) (nth init-dns i)))))))]
                        { quantile-stat { :quantile nil :qs sorted-new-win :ns init-ns :dns init-dns :nps init-nps } }))
        
        add-update-fn (fn [prev-sk x]
                        (let [prev-sk-stat (get prev-sk quantile-stat)
                              qs (:qs prev-sk-stat)
                              ns (:ns prev-sk-stat)
                              dns (:dns prev-sk-stat)
                              nps (:nps prev-sk-stat)]
                          (let [b1 (if (< x (nth qs 0))
                                     { :qs (assoc qs 0 x) :k 1 }
                                     (if (>= x (nth qs (dec marker-count)))
                                       { :qs (assoc qs (dec marker-count) x) :k (dec marker-count) }
                                       { :qs qs :k (loop [i 1]
                                                     (if (< x (nth qs i)) i (recur (inc i)))) }))
                                qs (:qs b1) k (:k b1)
                                b2-a (loop [i k res { :ns ns :nps nps }]
                                       (if (>= i marker-count) res
                                         (let [current-ns (:ns res)
                                               current-nps (:nps res)]
                                           (recur (inc i)
                                                  { :ns (assoc current-ns i (inc (nth current-ns i))) :nps (assoc current-nps i (+ (nth current-nps i) (nth dns i))) }))))
                                ns (:ns b2-a) nps (:nps b2-a)
                                b2-b (loop [i 0 res { :nps nps }]
                                       (if (>= i k) res
                                         (let [current-nps (:nps res)]
                                           (recur (inc i)
                                                  { :nps (assoc current-nps i (+ (nth current-nps i) (nth dns i))) }))))
                                nps (:nps b2-b)
                                b3 (loop [i 1 res { :qs qs :ns ns }]
                                     (if (>= i (dec marker-count)) res
                                       (let [current-qs (:qs res)
                                             current-ns (:ns res)
                                             d (- (nth nps i) (nth current-ns i))]
                                         (if (or (and (>= d +1) (> (- (nth current-ns (inc i)) (nth current-ns i)) +1))
                                                 (and (<= d -1) (< (- (nth current-ns (dec i)) (nth current-ns i)) -1)))
                                           (let [new-para-q (float (parabolic-fn current-ns current-qs i (sign-fn d)))
                                                 new-n (+ (nth current-ns i) (sign-fn d))]
                                             (if (and (< (nth current-qs (dec i)) new-para-q)
                                                      (< new-para-q (nth current-qs (inc i))))
                                               (recur (inc i)
                                                      { :qs (assoc current-qs i new-para-q) :ns (assoc current-ns i new-n) })
                                               (let [new-lin-q (float (linear-fn ns qs i (sign-fn d)))]
                                                 (recur (inc i)
                                                        { :qs (assoc current-qs i new-lin-q) :ns (assoc current-ns i new-n) }))))
                                           (recur (inc i)
                                                  { :qs current-qs :ns current-ns })))))
                                qs (:qs b3) ns (:ns b3)
                                quantile (nth qs (loop [i 2 closest-i 1]
                                                   (if (>= i (dec marker-count)) closest-i
                                                     (recur (inc i)
                                                            (if (< (abs (- (nth dns i) p)) (abs (- (nth dns closest-i) p)))
                                                              i closest-i)))))]
                            { quantile-stat { :quantile quantile :qs qs :ns ns :dns dns :nps nps } })))

        add-fn (fn [prev-sk x]
                 (let [new-count (inc (:count prev-sk))]
                   (cond
                     (< new-count marker-count) { quantile-stat { :quantile nil } }
                     (= new-count marker-count) (add-init-fn prev-sk x)
                     :else (add-update-fn prev-sk x))))]
    (track-stat dep-sk quantile-stat add-fn)))

(defn track-median-ish [sk]
  (alias-nested-stat (track-quantile-ish sk 0.5) :median [[:quantile 0.5] :quantile]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Multi Support (bundle of Skreams)
;;
(defn create-multi-skream [] {})

(defn assoc-multi-skream
  ([msk sk-key sk]
    (let [prev-meta-msk (meta msk)
          sub-sk-map (or (:sub-sk-map prev-meta-msk) {})
          new-meta-msk (assoc prev-meta-msk :sub-sk-map (assoc sub-sk-map sk-key sk))]
      (with-meta msk new-meta-msk)))
  ([msk sk-key sk & sk-key-sks]
    (loop [current-sk-key-sks sk-key-sks
           current-msk (assoc-multi-skream msk sk-key sk)]
      (if (empty? current-sk-key-sks) current-msk
        (let [current-sk-key (first current-sk-key-sks)
              current-sk (first (rest current-sk-key-sks))]
          (recur (rest (rest current-sk-key-sks))
                 (assoc-multi-skream current-msk current-sk-key current-sk)))))))

(defn add-multi-num
  ([msk sk-key] msk)
  ([msk sk-key x]
    (let [meta-msk (meta msk)
          prev-sub-sk-map (:sub-sk-map meta-msk)]
      (if (or (nil? prev-sub-sk-map) (not (contains? prev-sub-sk-map sk-key))) msk
        (let [prev-sk (get prev-sub-sk-map sk-key)
              new-sub-sk-map (assoc prev-sub-sk-map sk-key (add-num prev-sk x))
              middle-msk (vary-meta msk assoc :sub-sk-map new-sub-sk-map)
              added-msk (apply merge-with-meta (pmap (fn [add-fn] (add-fn msk sk-key x))
                                                     (vals (:add-fn-map meta-msk))))
              aliased-msk (add-aliases added-msk (:alias-map meta-msk))]
          (merge (merge-with-meta middle-msk added-msk) aliased-msk)))))
  ([msk sk-key x & xs]
    (loop [current-xs xs
           current-msk (add-multi-num msk sk-key x)]
      (if (empty? current-xs) current-msk
        (recur (rest current-xs)
               (add-multi-num current-msk sk-key (first current-xs)))))))

(defn add-co-multi-num
  ([msk] msk)
  ([msk & key-xs]
    (let [meta-msk (meta msk)
          middle-msk (loop [current-key-xs key-xs
                            current-msk msk]
                       (if (empty? current-key-xs) current-msk
                         (let [sk-key (first current-key-xs)
                               x (first (rest current-key-xs))]
                           (recur (rest (rest current-key-xs))
                                  (add-multi-num current-msk sk-key x)))))
          added-msk (apply merge-with-meta (pmap (fn [add-co-fn]
                                                   (let [add-co-fn-args [msk middle-msk]
                                                         add-co-fn-args (if (nil? key-xs) add-co-fn-args
                                                                          (apply conj add-co-fn-args key-xs))]
                                                     (apply add-co-fn add-co-fn-args)))
                                                 (vals (:add-co-fn-map meta-msk))))
          aliased-msk (add-aliases added-msk (:alias-map meta-msk))]
      (merge (merge-with-meta middle-msk added-msk) aliased-msk))))

(defn track-multi-stat [msk sk-key track-fn & track-args]
  (let [meta-msk (meta msk)
        prev-sk (get-in meta-msk [:sub-sk-map sk-key])
        new-sk (apply track-fn (cons prev-sk track-args))
        new-sub-sk-map (assoc (get meta-msk :sub-sk-map) sk-key new-sk)
        new-msk-meta (assoc meta-msk :sub-sk-map new-sub-sk-map)]
    (with-meta msk new-msk-meta)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Multi Stats
;;
(defn track-multi-count [msk]
  (let [add-fn (fn [prev-msk sk-key x]
                 { :mcount (inc (:mcount prev-msk)) })]
    (track-stat msk :mcount add-fn 0)))

(defn get-new-co-histogram [prev-msk middle-msk sk-key1 sk-key2 co-hist-key hist-stat-prefix]
  (let [prev-co-hist (get prev-msk co-hist-key)
        prev-sk1 (meta-get-in prev-msk [:sub-sk-map sk-key1])
        prev-sk2 (meta-get-in prev-msk [:sub-sk-map sk-key2])
        middle-sk1 (meta-get-in middle-msk [:sub-sk-map sk-key1])
        middle-sk2 (meta-get-in middle-msk [:sub-sk-map sk-key2])
        stat1 (first (get-changed-range-count-keys prev-sk1 middle-sk1 hist-stat-prefix))
        stat2 (first (get-changed-range-count-keys prev-sk2 middle-sk2 hist-stat-prefix))]
    (if (or (nil? stat1) (nil? stat2)) prev-co-hist
      (let [co-key [stat1 stat2]
            prev-co-count (or (get prev-co-hist co-key) 0)]
        (assoc prev-co-hist co-key (inc prev-co-count))))))
  
(defn track-multi-co-histogram-helper [msk sk-key1 sk-key2 co-hist-key hist-stat-prefix]
  (let [add-co-fn (fn [prev-msk middle-msk & key-xs]
                    (let [prev-co-hist (get prev-msk co-hist-key)
                          key-xs-hash-map (apply hash-map key-xs)
                          match-x1 (get key-xs-hash-map sk-key1)
                          match-x2 (get key-xs-hash-map sk-key2)]
                      (if (not (and match-x1 match-x2)) { co-hist-key prev-co-hist }
                        { co-hist-key (get-new-co-histogram prev-msk middle-msk sk-key1 sk-key2 co-hist-key hist-stat-prefix) })))]
    (track-stat msk co-hist-key add-co-fn {} :add-co-fn-map)))

(defn track-multi-co-histogram [msk sk-key1 sk-key2 min-x max-x num-buckets]
  (let [stat [:co-hist sk-key1 sk-key2]
        dep-msk msk
        dep-msk (track-multi-stat dep-msk sk-key1 track-histogram min-x max-x num-buckets)
        dep-msk (track-multi-stat dep-msk sk-key2 track-histogram min-x max-x num-buckets)]
    (track-multi-co-histogram-helper dep-msk sk-key1 sk-key2 stat :range-count)))

(defn track-multi-co-normal-histogram [msk sk-key1 sk-key2 num-buckets]
  (let [stat [:co-norm-hist sk-key1 sk-key2]
        dep-msk msk
        dep-msk (track-multi-stat dep-msk sk-key1 track-normal-histogram num-buckets)
        dep-msk (track-multi-stat dep-msk sk-key2 track-normal-histogram num-buckets)]
    (track-multi-co-histogram-helper dep-msk sk-key1 sk-key2 stat :normal-range-count)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Multi Mutual Information
;;
(defn track-multi-mutual-information [msk sk-key1 sk-key2 min-x max-x num-buckets]
  (let [stat [:mutinf sk-key1 sk-key2]
        dep-msk msk
        dep-msk (track-multi-co-histogram dep-msk sk-key1 sk-key2 min-x max-x num-buckets)
        dep-msk (track-multi-stat dep-msk sk-key1 track-count)
        dep-msk (track-multi-stat dep-msk sk-key2 track-count)
        add-co-fn
        (fn [prev-msk middle-msk & key-xs]
          (let [prev-mi (get prev-msk stat)
                prev-co-hist (get prev-msk [:co-hist sk-key1 sk-key2])
                key-xs-hash-map (apply hash-map key-xs)
                match-x1 (get key-xs-hash-map sk-key1)
                match-x2 (get key-xs-hash-map sk-key2)]
            (if (not (and match-x1 match-x2)) { stat prev-mi }
              (let [new-co-hist
                    (get-new-co-histogram prev-msk middle-msk sk-key1 sk-key2 [:co-hist sk-key1 sk-key2] :range-count)
                    sk1 (meta-get-in middle-msk [:sub-sk-map sk-key1])
                    sk2 (meta-get-in middle-msk [:sub-sk-map sk-key2])
                    co-hist-count (apply + (vals new-co-hist))
                    new-mi
                    (let [range-count-keys1 (filter (fn [key] (range-count-key? key :range-count)) (keys sk1))
                          range-count-keys2 (filter (fn [key] (range-count-key? key :range-count)) (keys sk2))]
                      (loop [current-range-count-keys1 range-count-keys1
                             current-ent1 0]
                        (if (empty? current-range-count-keys1) current-ent1
                          (let [current-ent-inc1
                                (loop [current-range-count-keys2 range-count-keys2
                                       current-ent2 0]
                                  (if (empty? current-range-count-keys2) current-ent2
                                    (let [current-range-count-key1 (first current-range-count-keys1)
                                          current-range-count-key2 (first current-range-count-keys2)
                                          co-hist-key [current-range-count-key1 current-range-count-key2]
                                          p1 (/ (get sk1 current-range-count-key1) (:count sk1))
                                          p2 (/ (get sk2 current-range-count-key2) (:count sk2))
                                          p12 (/ (or (get new-co-hist co-hist-key) 0) co-hist-count)
                                          current-ent-inc2 (if (zero? p12) 0
                                                             (* p12 (get-logarithm-2 (/ p12 (* p1 p2)))))]
                                      (recur (rest current-range-count-keys2)
                                             (+ current-ent2 current-ent-inc2)))))]
                            (recur (rest current-range-count-keys1)
                                   (+ current-ent1 current-ent-inc1))))))]
                { stat new-mi }))))]
    (track-stat dep-msk stat add-co-fn nil :add-co-fn-map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Default Stat Combinations
;;
(defn track-min-max [sk]
  (-> sk
    track-min
    track-max))

(defn track-default [sk]
  (-> sk
    track-median-ish
    track-kurtosis
    track-skew
    track-standard-deviation
    track-mean
    track-sum
    track-min-max
    track-last
    track-count))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; REPL Execution Path
;;
(defn -main [& args]
  (def ^:dynamic *sk* (create-skream))
  (println-stderr ">>> SKREAM" (get-pid))
  (in-ns 'skream.core)
  (loop [lines (repeatedly read-line)]
    (let [s (first lines)]
      (if (nil? s) nil
        (let [expr (read-string s)]
          (if (number? expr)
            (do
              (def ^:dynamic *sk* (add-num *sk* expr))
              (println *sk*)
              (recur (next lines)))
            (let [new-sk (try (eval expr)
                           (catch Exception ex
                             (println-stderr ">>> ERROR" (.getMessage ex))
                             nil))]
              (if (not (nil? new-sk))
                (do
                  (def ^:dynamic *sk* new-sk)
                  (println *sk*)))
              (recur (next lines))))))))
  (System/exit 0))
