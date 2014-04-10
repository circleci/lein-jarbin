(ns leiningen.jarbin
  (:require [leiningen.core.main :as main]
            [leiningen.core.project :as project]
            [cemerick.pomegranate.aether :as aether]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.java.io :as io]
            [me.raynes.conch.low-level :as sh])
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
  (str (Files/createTempDirectory prefix empty-file-attrs)))

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
  [project jarbin-env-vars script-name]
  (->> (get-in project [:jarbin :scripts script-name :env])
       (map (fn [[k v]]
              (cond
               (and (keyword? v) (= "lein" (namespace v))) [(name k) (resolve-lein-env-var project v)]
               (and (keyword? v) (= "jarbin" (namespace v))) [(name k) (get jarbin-env-vars (name v))]
               :else [(name k) v])))
       (into {})))

(defn parse-coord [args]
  (when-let [[_ coord rest-args] (re-find #"^(\[.+\])(.+)" (str/join " " args))]
    (let [rest-args (str/split (str/trim rest-args) #" ")]
      {:coord (parse-coord-str coord)
       :bin (first rest-args)
       :bin-args (rest rest-args)})))


(defn parse-local-src [args]
  (when-let [[_ local-src rest-args] (re-find #"^(\.) (.+)" (str/join " " args))]
    (let [rest-args (str/split (str/trim rest-args) #" ")]
      {:local-src local-src
       :bin (first rest-args)
       :bin-args (rest rest-args)})))

(defn parse-args [args]
  (parse-coord args))

(defn bin-path-in-jar
  "Given the name of a script, return the in-jar location of the bin"
  [project bin-name]
  (let [bin-path (get-in project [:jarbin :bin-dir] "bin")]
    (str bin-path "/" bin-name)))

(defn exec [{:keys [env dir cmd] :as args}]
  (let [proc (apply sh/proc (concat cmd [:dir (str dir) :env env]))]
    (future (sh/stream-to-out proc :out))
    (future (sh/stream-to-out proc :err))
    (let [exit (future (sh/exit-code proc))]
      (println "exit:" @exit))))

(defn setup-exec [jarbin-project {:keys [coord bin bin-args]}]
  (let [project-dir (create-temp-dir "jarbin")
        jar-path (resolve-jar-path jarbin-project coord)
        _ (assert jar-path)
        _ (extract-file-from-jar jar-path project-dir "project.clj")
        project-path (str/join "/" [project-dir "project.clj"])
        target-project (project/read project-path)
        bin-path (str/join "/" [project-dir (bin-path-in-jar target-project bin)])
        jarbin-env-vars {"jar-path" (str jar-path)
                         "coord" (str coord)}]
    (extract-file-from-jar jar-path project-dir (bin-path-in-jar target-project bin))
    (sh/proc "chmod" "+x" (str bin-path))
    {:dir project-dir
     :env (merge (into {} (System/getenv)) (resolve-lein-env-vars target-project jarbin-env-vars bin))
     :cmd (concat [bin-path] bin-args)}))

(defn ^:no-project-needed ^:higher-order jarbin
  "Run a script contained in jar

  Usage:

  lein jarbin [foo/bar \"1.2.3\"] bbq
"
  [project & args]
  (as-> args %
      (parse-args %)
      (setup-exec project %)
      (exec %)))
