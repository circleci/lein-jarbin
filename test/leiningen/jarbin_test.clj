(ns leiningen.jarbin-test
  (:require [clojure.test :refer :all]
            [leiningen.jarbin :as jarbin]))

(deftest parse-coord-works
  (is (= '[foo/bar "1.2.3"] (jarbin/parse-coord-str "[foo/bar 1.2.3]")))
  (is (= '[foo/bar-bar "1.2.3"] (jarbin/parse-coord-str "[foo/bar-bar 1.2.3]"))))

(deftest parse-args-works
  (testing "coord"
    (is (= {:coord '[foo/bar "1.2.3"]
            :bin "bbq"
            :bin-args ["baz" "1"]}) (jarbin/parse-args ["[foo/bar" "1.2.3]" "bbq" "baz" "1"]))))

(def test-project {:name "foo"
                   :group "bar"
                   :version "1.2.3"
                   :jvm-opts ["-server"
                              "-Xmx1024m"]
                   :jarbin {:scripts {:bbq {:env {:FOO "bar"
                                                  :NAME :lein/name
                                                  :VERSION :lein/version
                                                  :JVM_OPTS :lein/jvm-opts
                                                  :JAR_PATH :jarbin/jar-path
                                                  :COORD :jarbin/coord}}}}})

(deftest resolve-single-lein-env-var
  (is (= "foo" (jarbin/resolve-lein-env-var test-project :lein/name)))
  (is (= "-server -Xmx1024m" (jarbin/resolve-lein-env-var test-project :lein/jvm-opts))))

(deftest env-map-works
  (let [resp (jarbin/resolve-lein-env-vars test-project {} :bbq)]
    (is (= "bar" (get resp "FOO")))
    (is (= "1.2.3" (get resp "VERSION")))))

(deftest env-map-exposes-jarbin
  (let [resp (jarbin/resolve-lein-env-vars test-project {"jar-path" "/foo/bar"} :bbq)]
    (is (= "/foo/bar" (get resp "JAR_PATH")))))

(deftest parse-args-doesnt-throw
  (jarbin/parse-args []))
