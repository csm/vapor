(ns vapor.ssh.client
  (:import (org.apache.sshd SshClient ClientSession ClientChannel)
           (org.apache.sshd.client.channel ChannelExec PtyCapableChannelSession)
           (vapor.ssh.futures SshFutures)
           (java.io IOException)
           (com.google.common.io ByteStreams)
           (org.apache.sshd.common.keyprovider FileKeyPairProvider)
           (org.bouncycastle.openssl PasswordFinder)
           (java.security KeyPair))
  (:require [vapor.core.futures :refer :all]))

(def ssh-client
  "The default SSH client. Use this if you don't want to build your own."
  (delay
    (let [c (SshClient/setUpDefaultClient)]
      (.start c)
      c)))

(defn- connect-future [f]
  (listenable-future
    (SshFutures/connectFuture f)))

(defn- open-future [f]
  (listenable-future
    (SshFutures/openFuture f)))

(defn- auth-future [f]
  (listenable-future
    (SshFutures/authFuture f)))

(defn- close-future [f]
  (listenable-future
    (SshFutures/closeFuture f)))

(defn connect
  "Connect to a remote address.

  Has three forms

  (connect user address)             Connects with given username to address, on port 22 and using the default client.
  (connect user address port)        Connects with the given username, address and port, using the default client.
  (connect user address port client) Connects with the given username, address, port, and client.

  Returns an IFuture that resolves when the connection completes."
  ([user address] (connect user address 22))
  ([user address port] (connect user address 22 @ssh-client))
  ([user address port ssh-client]
   (connect-future
     (.connect ssh-client user address port))))

(defn close!
  ([^ClientSession session] (close! session true))
  ([^ClientSession session ^Boolean immediately]
   (close-future (.close session immediately))))

(defn load-keys
  "Load an SSH key pair from a file.

  If password-protected, then password-finder should be supplied, a function that takes no
  arguments and returns a char array with the password."
  ([file] (load-keys file nil))
  ([file password-finder]
   (.loadKeys (if (nil? password-finder)
                (FileKeyPairProvider. (into-array [file]))
                (FileKeyPairProvider. (into-array [file])
                                      (reify PasswordFinder
                                        (getPassword [this] (password-finder))))))))

(defn add-password!
  "Adds a password to an open connection."
  [^ClientSession connection password]
  (.addPasswordIdentity connection password))

(defn add-public-key!
  "Adds a public key to an open connection."
  ([^ClientSession connection public-key]
   (add-public-key! connection public-key nil))
  ([^ClientSession connection public-key password-finder]
   (cond
     (string? public-key) (for [pubkey (load-keys public-key password-finder)]
                            (.addPublicKeyIdentity connection pubkey))
     (instance? KeyPair public-key) (.addPublicKeyIdentity connection public-key))))

(defn authenticate!
  "Uses any authentication methods added to an open connection,
   and attempts to authenticate. Returns an IFuture that will resolve
   when the authentication attempt completes."
  [^ClientSession connection]
  (auth-future (.auth connection)))

(defn exec
  "Execute a command on the remote host."
  [^ClientSession connection command input]
  (let [exec-channel (.createExecChannel connection command)]
    (-> (.open exec-channel)
        open-future
        (>>| (fn [r]
               r))
        (>>| (fn [r]
               (if r
                 (do-async (.waitFor exec-channel
                                     (bit-or ClientChannel/EXIT_SIGNAL ClientChannel/EXIT_STATUS)
                                     30000))
                 (failure (IOException. "failed to open exec channel")))))
        (>| (fn [r]
              (if (zero? (bit-and r (bit-or ClientChannel/EXIT_SIGNAL ClientChannel/EXIT_STATUS)))
                {:success false}
                {:success true
                 :exit-status (.getExitStatus exec-channel)
                 :stdout (ByteStreams/toByteArray (.getInvertedOut exec-channel))
                 :stderr (ByteStreams/toByteArray (.getInvertedErr exec-channel))})))
        (>! (fn [r]
              (.close exec-channel true))))))