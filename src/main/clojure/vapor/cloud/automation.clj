(ns vapor.cloud.automation
  (require [vapor.ssh.client :as ssh]
           [vapor.core.futures :refer [>| >>| then!]]
           [vapor.core.futures :as futures]
           [clojure.edn :as edn]
           [clojure.string :as cljstr]
           [clojure.java.io :refer [input-stream resource]]
           [clojure.tools.logging :as log])
  (:import (org.apache.sshd.common SshException SessionListener)
           (org.apache.sshd ClientSession)
           (clojure.lang IDeref IPersistentList)
           (java.io IOException PushbackReader InputStreamReader ByteArrayInputStream)
           (com.google.common.io ByteStreams)
           (java.security MessageDigest DigestInputStream)))

(defn- get-clojure-jar
  []
  (.getPath (.getLocation (.getCodeSource (.getProtectionDomain IPersistentList)))))

(defn- tame-arch
  [os arch]
  (condp = os
    "Darwin" "amd64"
    "Linux" (condp = arch
              "x86_64" "amd64"
              arch)
    arch))

(defn- arch-to-goarch
  [arch]
  (condp = arch
    "x86_64" "amd64"
    "i386"   "368"
    arch)) ;; TODO there are more options, but what will uname say?

(defn- os-to-goos
  [os]
  (condp = os
    "Linux"  "linux"
    "Darwin" "darwin"
    os)) ;; TODO there are more options, but what will uname say?

(defn- uname-reconn
  "Try running 'uname -s -m' on the remote host, figure out OS and architecture."
  [conn]
  @(-> (ssh/exec conn "uname -s -m")
      (>| (fn [r]
            (if (:success r)
              (let [res (cljstr/split (String. (:stdout r)) #"[ \n]")]
                   {:os (os-to-goos (first res)) :arch (arch-to-goarch (second res))})
              {})))))

(defn setup-remote
  "Given an SSH connection, see if vapor's home directory (default ~/.vapor) is present,
  and if it has clojure present there."
  [conn config]
  (let [pwd-result @(ssh/exec conn "pwd")
        home-dir (if (:success pwd-result)
                   (.trim (String. (:stdout pwd-result)))
                   (throw (ex-info "failed to find home directory" pwd-result)))
        vapor-home-status @(ssh/exec conn (str "stat " home-dir "/.vapor"))
        make-vapor-home @(if (= 0 (:exit-status vapor-home-status))
                           (futures/successful {:exit-status 0})
                           (ssh/exec conn (str "mkdir " home-dir "/.vapor")))
        clojure-jar-exists @(ssh/exec conn (str "stat " home-dir "/.vapor/" (last (cljstr/split (get-clojure-jar) #"/"))))
        upload-cljar (if (= 0 (:exit-status clojure-jar-exists))
                       nil
                       (ssh/upload conn (get-clojure-jar) (str home-dir "/.vapor/")))
        uname-info (uname-reconn conn)]
    (merge {:home-dir home-dir
            :clojure-jar-path (str home-dir "/.vapor/" (last (cljstr/split (get-clojure-jar) #"/")))}
           uname-info)))

(defn memoize-connection
  "Call f to build a connection, if we don't have one, OR if the one we have is being closed/is closed."
  [f]
  (let [conn (atom nil)]
    (fn [& args]
      (if-let [c (when (and (not (nil? @conn)) (not (.isClosing @conn))) @conn)]
        c
        (let [new-conn (apply f args)]
          (swap! conn (fn [a] new-conn))
          new-conn)))))

(defmacro remote-fn
  [body]
  (quote (prn ~body)))

(defprotocol IAutomator
  (connect! [this] "Connect to the remote host, if necessary.")
  (close! [this] "Close the current connection.")
  (gather-facts [this] "Gather facts about an instance. instance-info should be a map returned by a
                        instance-infos implementation of IInventory. Returns a map of facts gathered."))

(defn- upload-module
  [conn config content]
  (let [remote-path (str (:home-dir config) "/.vapor/.tmp_vapor-" (System/currentTimeMillis) "-" (System/nanoTime) ".clj")
        digest (MessageDigest/getInstance "SHA-256")]
    @(>>|
       (ssh/exec conn (str "tee " remote-path) :input (DigestInputStream. content digest))
       (fn [r] (log/debug "upload result " r)
         (let [d (cljstr/join (map (partial format "%02x")) (.digest digest))]
           (>>|
             (ssh/exec (str "sha256sum " remote-path))))
         (log/debug "upload stdout:" (when-let [out (:stdout (:result r))] (String. out)))
         (log/debug "upload stderr:" (when-let [err (:stderr (:result r))] (String. err)))))
    remote-path))

(defrecord SshAutomator [config conn]
  IAutomator
  (gather-facts [this]
    (when-let [content (input-stream (resource "modules/system/gather_facts.clj"))]
      (let [remote-path (upload-module @conn config content)]
        @(>|
          (ssh/exec @conn (str "java -cp " (:clojure-jar-path config) " clojure.main " remote-path "; rm -f " remote-path))
          (fn [r]
            (log/debug r)
            (log/debug (String. (:stdout r)))
            (log/debug (String. (:stderr r)))
            (edn/read (PushbackReader. (InputStreamReader. (ByteArrayInputStream. (:stdout r)))))))))))

(defn ssh-automator
  "Create a SSH based automator"
  [config instance-info]
  (let [loader (memoize-connection
                 (fn [config instance-info]
                   (let [conn @(ssh/connect (or (:remote-user config) (System/getProperty "user.name"))
                                            (:private-ip-address instance-info))]
                     (when-let [private-key (:private-key config)]
                       (log/debug "adding private key " private-key)
                       (ssh/add-public-key! conn (first (ssh/load-keys private-key))))
                     (when-let [password (:password config)]
                       (ssh/add-password! conn password))
                     (if (not @(ssh/authenticate! conn))
                       (throw (SshException. "authentication failed")))
                     conn)))
        call-loader (reify IDeref
                      (deref [this] (loader config instance-info)))]
    (SshAutomator. (merge config (setup-remote @call-loader config) instance-info) call-loader)))
