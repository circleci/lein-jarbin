(ns leiningen.jarbin
  (:require [leiningen.core.main :as main]
            [leiningen.core.project :as project]
            [cemerick.pomegranate.aether :as aether]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io])
  (:import java.util.jar.JarFile
           (java.io PushbackReader
                    StringReader
                    File)
           (java.nio.file Files
                          Path
                          attribute.FileAttribute)))

(defn project-repo-map
  "Returns the map of all repos. Use for resolving, not deploying"
  [project]
  (into {} (map (fn [[name repo]]
                  [name (leiningen.core.user/resolve-credentials repo)]) (:repositories project))))

(defn resolve-coord
  "Resolve a single coordinate"
  [project coord]
  (aether/resolve-dependencies :coordinates [coord] :repositories (project-repo-map project)))

(defn resolve-jar-path
  "Resolve the coordinates, returns the path to the jar file locally"
  [project coord]
  (->> (resolve-coord project coord)
       keys
       (filter #(= % coord))
       first
       meta
       :file))

(def empty-file-attrs
  ;; several Files/ methods need this
  (into-array FileAttribute []))

(defn create-temp-dir [prefix]
  (str (Files/createTempDirectory "foo" empty-file-attrs)))

(defn extract-file-from-jar [jar dest-dir filename]
  (with-open [jar (JarFile. jar)]
    (let [entry (.getEntry jar filename)
          dest-name (.getName entry)
          dest-file (File. (str dest-dir File/separator dest-name))
          dest-path (.toPath dest-file)]
      (Files/createDirectories (.getParent dest-path) empty-file-attrs)
      (with-open [i (.getInputStream jar entry)]
        (io/copy i dest-file)))))

(defn parse-coord-str [coord-str]
  (let [[_ name version] (re-find #"\[([./\w]+) (.+)\]" coord-str)]
    [(symbol name) (str version)]))

(defn resolve-lein-env-var [project v]
  (let [resp (get project (keyword (name v)))]
    (if (sequential? resp)
      (str/join " " resp)
      resp)))

(defn resolve-lein-env-vars
  "Returns a map of env vars and their resolved values"
  [project script-name]
  (->> (get-in project [:jarbin :scripts script-name :env])
       (map (fn [[k v]]
              (if (and (keyword? v) (= "lein" (namespace v)))
                [(name k) (resolve-lein-env-var project v)]
                [(name k) v])))
       (into {})))

(defn parse-coord [args]
  (when-let [[_ coord rest-args] (re-find #"^(\[.+\])(.+)" (str/join " " args))]
    (let [rest-args (str/split (str/trim rest-args) #" ")]
      {:coord (parse-coord-str coord)
       :bin (first rest-args)
       :bin-args (rest rest-args)})))

(defn parse-local-jar [args]
  (when-let [[_ jar-path rest-args] (re-find #"^(.+\.jar) (.+)" (str/join " " args))]
    (println "jar-path:" jar-path)
    (let [rest-args (str/split (str/trim rest-args) #" ")]
      (println "rest-args:" rest-args)
      {:jar-path jar-path
       :bin (first rest-args)
       :bin-args (rest rest-args)})))

(defn parse-local-src [args]
  (when-let [[_ local-src rest-args] (re-find #"^(\.) (.+)" (str/join " " args))]
    (let [rest-args (str/split (str/trim rest-args) #" ")]
      {:local-src local-src
       :bin (first rest-args)
       :bin-args (rest rest-args)})))

(defn parse-args [args]
  (or (parse-coord args)
      (parse-local-jar args)
      (parse-local-src args)))

(defn bin-path-in-jar
  "Given the name of a script, return the in-jar location of the bin"
  [project bin-name]
  (let [bin-path (get-in project [:jarbin :bin-dir] "bin")]
    (str bin-path "/" bin-name)))

(defn bin-path-local [project bin-name]
  (assert false))

(defn exec [{:keys [env dir cmd] :as args}]
  (let [resp (apply sh/sh (concat cmd [:dir dir :env env]))]
    (println resp)))

(defn setup-exec [jarbin-project {:keys [coord jar-path local-src bin bin-args]}]
  (let [jar? (or coord jar-path)
        project-dir (if jar?
                  (create-temp-dir "jarbin")
                  local-src)
        jar-path (cond
                  coord (resolve-jar-path jarbin-project coord)
                  jar-path jar-path
                  :else nil)
        project-path (if jar-path
                       (do
                         (extract-file-from-jar jar-path project-dir "project.clj")
                         (str/join "/" [project-dir "project.clj"]))
                       "./project.clj")
        target-project (project/read project-path)
        bin-path (if jar-path
                   (str/join "/" [project-dir (bin-path-in-jar target-project bin)])
                   (bin-path-local target-project bin))]
    (when jar-path
      (extract-file-from-jar jar-path project-dir (bin-path-in-jar target-project bin))
      (sh/sh "chmod" "+x" bin-path))
    {:dir project-dir
     :env (resolve-lein-env-vars target-project bin)
     :cmd (concat [bin-path] bin-args)}))

(defn ^:no-project-needed ^:higher-order jarbin
  "Run a script contained in jar

  Usage:

  lein jarbin [foo/bar \"1.2.3\"] bbq
  lein jarbin foo-bar-1.2.3.jar bbq
  lein jarbin . bbq
"
  [project & args]
  (as-> args %
      (parse-args %)
      (setup-exec project %)
      (exec %)))
