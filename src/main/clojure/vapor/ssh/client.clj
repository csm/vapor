(ns vapor.ssh.client
  (:import (org.apache.sshd SshClient ClientSession ClientChannel ClientChannel$Streaming)
           (org.apache.sshd.client.channel ChannelExec PtyCapableChannelSession)
           (vapor.ssh.futures SshFutures)
           (java.io IOException)
           (com.google.common.io ByteStreams)
           (org.apache.sshd.common.keyprovider FileKeyPairProvider)
           (org.bouncycastle.openssl PasswordFinder)
           (java.security KeyPair)
           (org.apache.sshd.common.util Buffer)
           (java.util.concurrent TimeoutException)
           (java.nio ByteBuffer)
           (org.apache.sshd.client ScpClient$Option ScpClient))
  (:require [vapor.core.futures :refer :all]
            [clojure.java.io :as jio]))

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
  "Execute a command on the remote host.

  Arguments:
  connection   A ClientSession, which should be connected and authenticated.
  command      A string, the command to run.

  Optional arguments:
  :input       Input to send to the command's stdin
  :timeout     Timeout, in milliseconds; default is no timeout
  :input-encoding If :input is a string, what encoding it is in. Default UTF-8.

  Returns a future which will resolve to a map containing :success, a boolean signaling
  if the exec was performed successfully. If :success is true, then the returned value will
  also contain:

  :exit-status    An integer, the exit status of the command.
  :stdout         A byte array, the command's standard output.
  :stderr         A byte array, the command's standard error output."
  [^ClientSession connection command & {:keys [input timeout input-encoding] :or {timeout 0 input-encoding "UTF-8"}}]
  (let [exec-channel (.createExecChannel connection command)]
    (if (not (nil? input))
      (.setIn exec-channel
              (cond
                (string? input) (jio/input-stream (.getBytes input input-encoding))
                true (jio/input-stream input))))
    (-> (.open exec-channel)
        open-future
        (>>| (fn [r]
               (if r
                 (do-async (.waitFor exec-channel
                                     (bit-or ClientChannel/EXIT_SIGNAL ClientChannel/EXIT_STATUS)
                                     timeout))
                 (failure (IOException. "failed to open exec channel")))))
        (>| (fn [r]
              (if (zero? (bit-and r (bit-or ClientChannel/EXIT_SIGNAL ClientChannel/EXIT_STATUS)))
                {:success false}
                {:success true
                 :exit-status (.getExitStatus exec-channel)
                 :stdout (ByteStreams/toByteArray (.getInvertedOut exec-channel))
                 :stderr (ByteStreams/toByteArray (.getInvertedErr exec-channel))})))
        (then! (fn [r]
              (.close exec-channel true))))))

(defprotocol IShell
  (write-shell [this contents] "Write contents to the shell.
                          Returns a future that resolves to boolean, telling if the write completed.")
  (read-shell [this len] "Attempt to read from the shell's standard output.
                    Returns a future that returns the data read, up to len bytes.")
  (read-shell-error [this len] "Attempt to read from the shell's stadard error.
                          Returns a future that returns the data read, up to len bytes")
  (close-shell! [this] "Closes the shell. Returns a future resolving to the close result."))

(defn- mkline-buffer
  [line]
  (Buffer. (.getBytes (if (.endsWith line "\n") line (str line "\n"))) false))

(defn- mkbuffer
  [contents]
  (cond
    (string? contents) (Buffer. (.getBytes contents) false)
    (instance? (Class/forName "[B") contents) (Buffer. contents 0 (alength contents) false)
    (instance? ByteBuffer contents) (let [buf (byte-array (.remaining contents))]
                                      (.get contents buf)
                                      (Buffer. buf 0 (alength buf) false))
    (instance? Buffer contents) contents))

(defn shell
  "Open a shell to the remote host."
  [^ClientSession connection]
  (let [shell-channel (doto (.createShellChannel connection)
                        (.setStreaming ClientChannel$Streaming/Async))]
    (-> (.open shell-channel)
        open-future
        (then! (fn [r] (println "open future completed, result: " r)))
        (>| (fn [r]
              (if r
                (let [stdin (.getAsyncIn shell-channel)
                      stdout (.getAsyncOut shell-channel)
                      stderr (.getAsyncErr shell-channel)]
                  (reify
                    IShell
                    (write-shell [this contents]
                      (listenable-future
                        (SshFutures/writeFuture (.write stdin (mkbuffer contents)))))
                    (read-shell [this len]
                      (let [buffer (Buffer. len)]
                        (>|
                          (listenable-future
                            (SshFutures/readFuture (.read stdout buffer)))
                          (fn [len]
                            (.getCompactData buffer)))))
                    (read-shell-error [this len]
                      (let [buffer (Buffer. len)]
                        (>|
                          (listenable-future
                            (SshFutures/readFuture (.read stderr buffer)))
                          (fn [len]
                            (.getCompactData buffer)))))
                    (close-shell! [this]
                      (.close shell-channel true))))
              (throw (IOException. "failed to open shell channel"))))))))

(defn upload
  "Upload one or more local files to a remote path.

  Arguments:
  connection       The active connection
  local-paths      Either a string, or a sequence of strings; local paths to upload
  remote-path      A string, the path to upload to

  Optional arguments:
  :recursive           If true, recursively upload local-paths if they are directories
  :target-is-directory If true, the target path is a directory, not a file to overwrite
  :preserve-attributes If true, preserve file attributes

  Returs a future that resolves when the upload completes."
  [^ClientSession connection local-paths remote-path & {:keys [recursive target-is-directory preserve-attributes]}]
  (let [scp (.createScpClient connection)]
    (do-async
      (.upload scp (into-array String (if (seq? local-paths) local-paths [local-paths])) remote-path
               (into-array ScpClient$Option
                           (flatten [(if (true? recursive) [ScpClient$Option/Recursive] [])
                                     (if (true? target-is-directory) [ScpClient$Option/TargetIsDirectory] [])
                                     (if (true? preserve-attributes) [ScpClient$Option/PreserveAttributes] [])]))))))

(defn download
  [^ClientSession connection remote-paths local-path & {:keys [recursive target-is-directory preserve-attributes]}]
  (let [scp (.createScpClient connection)]
    (do-async
      (.upload scp (into-array String (if (seq? remote-paths) remote-paths [remote-paths])) local-path
               (into-array ScpClient$Option
                           (flatten [(if (true? recursive) [ScpClient$Option/Recursive] [])
                                     (if (true? target-is-directory) [ScpClient$Option/TargetIsDirectory] [])
                                     (if (true? preserve-attributes) [ScpClient$Option/PreserveAttributes] [])]))))))