(ns vapor.core.futures
  "A small library providing richer future support, via Guava ListenableFuture."
  (:import
    (com.google.common.util.concurrent Futures ListenableFuture AsyncFunction SettableFuture AbstractFuture FutureCallback)
    (clojure.lang IDeref IPending IBlockingDeref IObj)
    (java.util.concurrent TimeUnit TimeoutException)
    (com.google.common.base Function)))

(defprotocol IFuture
  "An asynchronous execution, backed by a Guava ListenableFuture."
  (target [f] "Get the underlying ListenableFuture")
  (transform [f fun] "Apply the function fun, passing in the result of the future f.

                      Returns a new future that returns the result of fun.")
  (async-transform [f fun] "Apply the function, passing in the result of this future, and returning a new future.
                            The returned value by fun should itself be an IFuture, or a ListenableFuture. Otherwise,
                            the value is wrapped in an immediate, successful future.

                            Returns a new future that returns the result of the future returned by fun.")
  (then! [f fun] "Add a callback to the given future; returns this.

                  fun should accept a single argument, a map containing:
                    :success  True or false, if the execution was successful
                    :result   The value returned by the execution, if successful
                    :exception The exception that occurred, if the execution was unsuccessful"))

(defprotocol ISettableFuture
  (succeed! [f result] "Succeed and set the future's value")
  (fail! [f ^Throwable exception] "Fail and set the future's exceeption"))

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
  [^ListenableFuture f fun]
  (Futures/addCallback f
                       (reify FutureCallback
                         (onSuccess [this v]
                           (apply fun [{:success true :result v}]))
                         (onFailure [this ex]
                           (apply fun [{:success false :exception ex}])))))

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
    (transform [this fun]
      (listenable-future (pipe f fun)))
    (async-transform [this fun]
      (listenable-future (pipe-async f fun)))
    (then! [this fun]
      (add-listener f fun)
      this)))

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

      (transform [this fun]
        (listenable-future (pipe (target this) fun)))

      (async-transform [this fun]
        (listenable-future (pipe-async (target this) fun)))

      (then! [this fun]
        (add-listener (target this) fun)
        this)

      ISettableFuture
      (succeed! [this result] (.set promise result))
      (fail! [_ exception] (.setException promise exception)))))

(def >| transform)
(def >>| async-transform)

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
          (succeed! p# ~@body)
          (catch Exception x# (fail! p# x#)))))
     p#))
