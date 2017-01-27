(set-env!
 :resource-paths #{"src" "boot-sass/src" "lein-sass4clj/src"}
 :source-paths #{"test" "test-resources"}
 :dependencies   '[[org.clojure/clojure "1.7.0" :scope "provided"]
                   [boot/core "2.5.2" :scope "provided"]
                   [adzerk/boot-test "1.0.7" :scope "test"]
                   [adzerk/bootlaces "0.1.13" :scope "test"]
                   [io.bit3/jsass "5.2.0"]
                   ;; Webjars-locator uses logging
                   [org.slf4j/slf4j-nop "1.7.12" :scope "test"]

                   [org.webjars/webjars-locator "0.29"]

                   ;; For testing the webjars asset locator implementation
                   [org.webjars.bower/bootstrap "4.0.0-alpha" :scope "test"]])

(require '[adzerk.boot-test :refer [test]]
         '[adzerk.bootlaces :refer :all])

(def +version+ "0.3.0")
(bootlaces! +version+)

(task-options!
  pom {:project     'flyingmachine/sass4clj
       :version     +version+
       :url         "https://github.com/flyingmachine/sass4clj"
       :scm         {:url "https://github.com/flyingmachine/sass4clj"}
       :license     {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}})

(defn with-files
  "Runs middleware with filtered fileset and merges the result back into complete fileset."
  [p middleware]
  (fn [next-handler]
    (fn [fileset]
      (let [merge-fileset-handler (fn [fileset']
                                    (next-handler (commit! (assoc fileset :tree (merge (:tree fileset) (:tree fileset'))))))
            handler (middleware merge-fileset-handler)
            fileset (assoc fileset :tree (reduce-kv
                                          (fn [tree path x]
                                            (if (p x)
                                              (assoc tree path x)
                                              tree))
                                          (empty (:tree fileset))
                                          (:tree fileset)))]
        (handler fileset)))))

(deftask write-version-file
  [n namespace NAMESPACE sym "Namespace"]
  (let [d (tmp-dir!)]
    (fn [next-handler]
      (fn [fileset]
        (let [f (clojure.java.io/file d (-> (name namespace)
                                            (clojure.string/replace #"\." "/")
                                            (clojure.string/replace #"-" "_")
                                            (str ".clj")))]
          (clojure.java.io/make-parents f)
          (spit f (format "(ns %s)\n\n(def +version+ \"%s\")" (name namespace) +version+)))
        (next-handler (-> fileset (add-resource d) commit!))))))

(deftask build []
  (comp
   (with-files
    (fn [x] (and (re-find #"sass4clj" (tmp-path x))
                 (not (re-find #"leiningen" (tmp-path x)))))
    (comp
     (pom
      :project 'flyingmachine/sass4clj
      :description "Clojure wrapper for jsass")
     (jar)
     (install)))

   (with-files
    (fn [x] (re-find #"boot_sass" (tmp-path x)))
    (comp
     (pom
      :project 'flyingmachine/boot-sass
      :description "Boot task to compile SASS"
      :dependencies [])
     (write-version-file :namespace 'flyingmachine.boot-sass.version)
     (jar)
     (install)))

   (with-files
    (fn [x] (re-find #"leiningen" (tmp-path x)))
    (comp
     (pom
      :project 'flyingmachine/lein-sass4clj
      :description "Leinigen task to compile SASS"
      :dependencies [])
     (write-version-file :namespace 'leiningen.sass4clj.version)
     (jar)
     (install)))))

(deftask dev []
  (comp
   (watch)
   (repl :server true)
   (build)
   (target)))

(deftask deploy []
  (comp
   (build)
   (push :repo "clojars" :gpg-sign (not (.endsWith +version+ "-SNAPSHOT")))))

(deftask run-tests []
  (comp
   (test :namespaces #{'sass4clj.core-test 'sass4clj.webjars-test})))

(deftask prebuild
  "Remove directories that shouldn't go into the final jar"
  []
  (set-env! :source-paths #(into #{} (remove #{"test-resources" "test"} %)))
  identity)
