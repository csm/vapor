(ns vapor.cloud.automation
  (require [vapor.ssh.client :as ssh]
           [vapor.cloud.module :as module]
           [vapor.core.futures :refer [>| >>| then!]]
           [vapor.core.futures :as futures]
           [vapor.core.util :as util]
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
        uname-info (uname-reconn conn)]
    (merge {:home-dir home-dir}
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
                        instance-infos implementation of IInventory. Returns a map of facts gathered.")
  (run-module [this name opts] "Run the given module."))

(defn- hash-path
  "Attempt various ways of hashing a file, returning the message digest.
   Returns a map with {hash-name hash-value}. Hashes tried are sha256, sha1.

   This really doesn't buy us any security, unfortunately, if the remote host
   is compromised. Since we send the file we want to hash to the server, an
   adversary could simply intercept that, hash it, and return that hash when
   we try computing it later."
  [conn path]
  @(>>| (ssh/exec conn (str "sha256sum " path))
       (fn [r]
         (if (zero? (:exit-status r))
           (futures/successful {:sha256 (first (cljstr/split (String. (:stdout r)) #"[ \t\n]"))})
           (>>| (ssh/exec conn (str "sha1sum " path))
                (fn [r]
                  (if (zero? (:exit-status r))
                    (futures/successful {:sha1 (first (cljstr/split (String. (:stdout r)) #"[ \t\n]"))})
                    (>| (ssh/exec conn (str "shasum " path))
                        (fn [r]
                          (if (zero? (:exit-status r))
                            {:sha1 (first (cljstr/split (String. (:stdout r)) #"[ \t\n]"))}
                            {}))))))))))

(defn- upload-module
  [conn config local-path]
  (let [remote-path (str (:home-dir config) "/.vapor/.tmp_vapor-" (System/currentTimeMillis) "-" (System/nanoTime) ".clj")
        sha256 (util/hash-file local-path "SHA-256")
        sha1 (util/hash-file local-path "SHA-1")]
    @(>|
       (ssh/upload conn local-path remote-path :preserve-attributes true)
       (fn [_]
         (println "upload complete")
         (let [remote-hash (hash-path conn remote-path)]
           (println "sha256:" sha256 "sha1:" sha1 "remote:" remote-hash)
           (if (or (= sha256 (:sha256 remote-hash)) (= sha1 (:sha1 remote-hash)))
             remote-path
             (throw (Exception. (str "Failed to upload or upload tampered with! Remote hash: " remote-hash)))))))))

(defrecord SshAutomator [config conn]
  IAutomator
  (gather-facts [this]
    (run-module this "core/gather-facts" {}))
  (run-module
    [this name opts]
    (let [local-module (module/module (merge (select-keys config [:os :arch])
                                             {:name name}
                                             (select-keys opts [:namespace])))
          remote-path (upload-module @conn config local-module)]
      @(>| (if (nil? (:args opts))
            (ssh/exec @conn (str remote-path "; rm -f " remote-path))
            (ssh/exec @conn (str remote-path "; rm -f " remote-path) :input (prn-str (:args opts))))
          (fn [r]
            (edn/read (PushbackReader. (InputStreamReader. (ByteArrayInputStream. (:stdout r))))))))))

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
