(ns vapor.core.futures
    (:import
      (com.google.common.util.concurrent Futures ListenableFuture AsyncFunction SettableFuture AbstractFuture FutureCallback)
      (clojure.lang IDeref IPending IBlockingDeref IObj)
      (java.util.concurrent TimeUnit TimeoutException)
      (com.google.common.base Function)))

(defprotocol IFuture
  (target [f] "Get the underlying ListenableFuture")
  (>| [f fun] "Apply the function, passing in the result of this future.")
  (>>| [f fun] "Apply the function, passing in the result of this future, and returning a new future.")
  (>! [f fun] "Add a callback to the given future; returns this."))

(defprotocol ISettableFuture
  (succeed [f result] "Succeed and set the future's value")
  (fail [f ^Throwable exception] "Fail and set the future's exceeption"))

(defn- pipe
       [^ListenableFuture f fun]
       (Futures/transform f
                          (proxy [Function] []
                                 (apply [result]
                                        (fun result)))))

(defn- pipe-async
       [^ListenableFuture f fun]
       (Futures/transform f
                          (proxy [AsyncFunction] []
                                 (apply [result]
                                        (let [r (fun result)]
                                             (println "pipe-async result " r)
                                             (cond
                                              (satisfies? IFuture r) (target r)
                                              (instance? ListenableFuture r) r
                                              true (Futures/immediateFuture r)))))))

(defn- add-listener
  [f fun]
  (Futures/addCallback (target f)
                       (reify FutureCallback
                         (onSuccess [this v]
                           (apply fun {:success true :result v}))
                         (onFailure [this ex]
                           (apply fun {:success false :exception ex})))))

(defn listenable-future
      "Wrap up a Guava listenable future in a IFuture, which is also an IDeref, IBlockingDeref, IPending."
      [^ListenableFuture f]

      (reify
        IDeref
        (deref [this] (.get f))

        IBlockingDeref
        (deref [this timeout-ms timeout-value]
                (try
                  (.get f timeout-ms TimeUnit/MILLISECONDS)
                  (catch TimeoutException _ timeout-value)))

        IPending
        (isRealized [this] (.isDone f))

        IFuture
        (target [this] f)
        (>| [this fun]
          (listenable-future (pipe f fun)))
        (>>| [this fun]
          (listenable-future (pipe-async f fun)))
        (>! [this fun]
          (add-listener f fun)
          f)))

(defn settable-future
  "Return a promise, which is also an IFuture, IDeref, IBlockingDeref, and IPending"
  []
  (let [promise (SettableFuture/create)]
    (reify
      IDeref
      (deref [this] (.get promise))

      IBlockingDeref
      (deref [this timeout-ms timeout-value]
        (try
          (.get promise timeout-ms TimeUnit/MILLISECONDS)
          (catch TimeoutException _ timeout-value)))

      IPending
      (isRealized [this] (.isDone promise))

      IFuture
      (target [this] promise)

      (>| [this fun]
        (listenable-future (pipe promise fun)))

      (>>| [this fun]
        (listenable-future (pipe-async promise fun)))

      (>! [this fun]
        (add-listener f fun)
        f)

      ISettableFuture
      (succeed [_ result] (.set promise result))
      (fail [_ exception] (.setException promise exception)))))

(defn successful
  "Return a future that is already successfully completed with the given value."
  [value]
  (listenable-future (Futures/immediateFuture value)))

(defn failure
  "Return a future that is already unsuccessfully completed with the given exception."
  [^Throwable exception]
  (listenable-future (Futures/immediateFailedFuture exception)))

(defmacro do-async
  "Executes body in a different thread, returning a listenable future."
  [& body]
  `(let [p# (settable-future)]
     (future-call
       (^{:once true} fn* []
        (try
          (succeed p# ~@body)
          (catch Exception x# (fail p# x#)))))
     p#))