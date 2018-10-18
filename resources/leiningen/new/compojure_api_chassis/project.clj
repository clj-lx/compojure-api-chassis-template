(defproject {{full-name}} "0.1.0-SNAPSHOT"
  :description "Compojure-api 2.0.0 alpha microservices chassis"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]

                 ;;server & api
                 [ring/ring-core "1.7.0"]
                 [ring/ring-jetty-adapter "1.7.0"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-devel "1.7.0"]
                 [mount "0.1.13"]
                 [metosin/compojure-api "2.0.0-alpha21"]
                 [metosin/spec-tools "0.7.1"]
                 [manifold "0.1.6"]
                 [com.grammarly/omniconf "0.3.2"]
                 [robert/hooke "1.3.0"]

                 ;;authentication/authorization
                 [buddy/buddy-auth "2.1.0"]

                 ;metrics
                 [metrics-clojure "2.10.0"]
                 [metrics-clojure-ring "2.10.0"]

                 ;misc
                 [org.clojure/tools.logging "0.4.1"]
                 [ch.qos.logback/logback-classic "1.2.3"]

                 {{#html-hook?}}
                 ;;UI
                 [selmer "1.12.1"]
                 {{/html-hook?}}

                 {{#oauth2-hook?}}
                 ;;oauth2 support for web UI
                 [clojusc/friend-oauth2 "0.2.0"]
                 {{/oauth2-hook?}}

                 {{#pgsql-hook?}}
                 ;; SQL
                 [org.postgresql/postgresql "42.2.5.jre7"]
                 [hikari-cp "2.6.0"]
                 [migratus "1.0.9"]
                 {{/pgsql-hook?}}]

  :min-lein-version "2.0.0"
  :aot [{{project-ns}}.main clojure.tools.logging.impl] ;; clojure.tools.logging.impl
  :main {{project-ns}}.main
  :repl-options {:init-ns user
                 :caught clj-stacktrace.repl/pst+}

  ;; > lein ring server
  :ring {:handler {{project-ns}}.main/app
         :init {{project-ns}}.main/init
         :async?  false
         :nrepl   {:start? true}
         :uberwar-name "{{name}}.war"}

  :aliases {"verify"     ["run" "-m" "{{project-ns}}.main/verify"]
            {{#pgsql-hook?}} "migrations" ["run" "-m" "{{project-ns}}.db/migrations"] {{/pgsql-hook?}}
            "jwt-sign"   ["run" "-m" "{{project-ns}}.auth.rules/jwt-sign"]}

  :uberjar-name "{{name}}.jar"
  :profiles {:uberjar {:aot :all
                       :main {{project-ns}}.main}

             :production {:env {:prod true}}

             :dev     {:source-paths   ["dev"]
                       :resource-paths ["resources"]
                       :dependencies   [[ring/ring-devel "1.6.3"]
                                        [ring/ring-mock "0.3.2"]
                                        [eftest "0.5.2"]
                                        [clj-stacktrace "0.2.8"]]
                       :plugins        [[lein-marginalia "0.9.1"]
                                        [lein-ring "0.12.3"]
                                        [lein-eftest "0.5.2"]]}})
