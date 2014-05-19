(defproject skream "0.0.1-SNAPSHOT"
  :description "Skream: Sketching & Streaming Library"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.json "0.2.1"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [colt "1.2.0"]
                 [ring "1.2.2"]
                 [ring/ring-json "0.3.1"]
                 [compojure "1.1.8"]]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :aot [skream.core]
  :main skream.core)

