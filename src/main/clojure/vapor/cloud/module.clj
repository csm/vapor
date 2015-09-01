(ns vapor.cloud.module
  (require [clojure.java.shell :as shell]
           [vapor.core.util :as util])
  (import [java.io File]))

(def default-namespace "github.com/csm/vapor-modules")

(defn get-work-dir
  []
  (condp = (System/getProperty "os.name")
    "Mac OS X" (str (System/getProperty "user.home") "/Library/Caches/vapor-modules")
    (str (System/getProperty "user.home") "/.vapor/module-cache"))) ;; todo, maybe others; use a config library?

(defn- success?!
  [result]
  (if (not (= 0 (:exit result)))
    (throw (Exception. (:err result)))))

(def gopath "/usr/local/go/bin/go") ;; FIXME

(def load-namespace
  (memoize (fn [namespace]
             (shell/sh gopath "get" "-u" namespace))))

(defn module
  "Find, compile, and return the path of a module."
  [opts]
  
(let [{:keys [os arch name namespace] :or {namespace default-namespace}} opts]
    (shell/with-sh-env {"GOOS" os "GOARCH" arch "GOPATH" (get-work-dir)
                        "PATH" (str "/usr/local/go/bin:" (System/getenv "PATH"))}
      (load-namespace "github.com/csm/go-edn")
      (load-namespace namespace)
      (let [module-source-path (str (get-work-dir) "/src/" namespace "/" name ".go")
            src-hash-tag (.substring (util/hash-file module-source-path) 0 8)
            bin-dest (str (get-work-dir) "/pkg/" os "_" arch "/" (.replace name "/" "_") "@" src-hash-tag)]
        (if (not (.exists (File. bin-dest)))
          (success?!
           (shell/sh gopath "build" "-buildmode" "exe" "-ldflags" "-s" "-o" bin-dest module-source-path)))
        bin-dest))))
