(ns vapor.logs.server
  "Log server. Accepts protobuf-formatted log messages, applies analysis on the log
  stream (and perhaps triggers alerts), then passes each event along to Cassandra
  for storage."
  (require [riemann.transport :refer [channel-initializer]]
           [riemann.transport.tcp :refer [int32-frame-decoder int32-frame-encoder]]
           [riemann.time :refer [unix-time]])
  (:import (io.netty.handler.codec.protobuf ProtobufDecoder ProtobufEncoder)
           (vapor.logs LogProto)
           (io.netty.handler.codec MessageToMessageDecoder)
           (java.util List)
           (io.netty.util.concurrent DefaultEventExecutorGroup)))

(defrecord Query [string])
(defrecord StackFrame [className file method line])
(defrecord Log [time host level name message exception stack ttl])
(defrecord Msg [ok error logs query decode-time])

(defn protobuf-decoder
  []
  (ProtobufDecoder. (LogProto$Msg/getDefaultInstance)))

(defn protobuf-encoder
  []
  (ProtobufEncoder.))

(defn default-ttl
  [level]
  (condp = level
    LogProto$Level/Error_VALUE   (* 30 24 60 60)
    LogProto$Level/Warning_VALUE (* 30 24 60 60)
    LogProto$Level/Info_VALUE    (* 7  24 60 60)
    (* 24 60 60)))

(defn post-load-logs
  [log]
  (-> log
      #(if (:time %) % (assoc % :time (unix-time)))
      #(if (:level %) % (assoc % :level LogProto$Level/Info_VALUE))
      #(if (:ttl %) % (assoc % :ttl (default-ttl (:level %))))))

(defn decode-pb-stack-frame
  [^LogProto$StackFrame frame]
  (StackFrame. (when (.hasClassName log) (.getClassName log))
               (when (.hasFile log) (.getFile log))
               (when (.hasMethod log) (.getMethod log))
               (when (.hasLine log) (.getLine log))))

(defn decode-pb-log
  [^LogProto$Log log]
  (Log. (when (.hasTime log) (.getTime log))
        (when (.hasHost log) (.getHost log))
        (when (.hasLevel log) (.getLevel log))
        (when (.hasName log) (.getName log))
        (when (.hasMessage log) (.getMessage log))
        (when (.hasException log) (.getException log))
        (mapv decode-pb-stack-frame (.getStackList log))
        (when (.hasTtl log) (.getTtl log))))

(defn decode-pb-msg
  [^LogProto$Msg msg]
  (let [t (System/nanoTime)]
    (Msg. (when (.hasOk msg) (.getOk msg))
          (when (.hasError msg) (.getError msg))
          (mapv decode-pb-log (.getLogsList msg))
          (when (.hasQuery msg) (.getQuery msg))
          t)))

(defn decode-msg
  [msg]
  (let [msg (decode-pb-msg msg)]
    (assoc msg :logs (map post-load-logs (:logs msg)))))

(defn msg-decoder
  []
  (proxy [MessageToMessageDecoder] []
    (decode [context msg ^List out]
      (.add out (decode-msg msg)))
    (isSharable [] true)))

(defn msg-encoder
  [])

(defn gen-tcp-handler
  [])

(defn log-executor
  []
  (DefaultEventExecutorGroup. (.. Runtime getRuntime availableProcessors)))

(defonce ^DefaultEventExecutorGroup shared-log-executor (log-executor))

(defn initializer
  []
  (channel-initializer
             int32-frame-decoder (int32-frame-decoder)
    ^:shared int32-frame-encoder (int32-frame-encoder)
    ^:shared protobuf-decoder    (protobuf-decoder)
    ^:shared protobuf-encoder    (protobuf-encoder)
    ^:shared msg-decoder         (msg-decoder)
    ^:shared msg-encoder         (msg-encoder)
    ^{:shared true :executor shared-log-executor} handler
    (gen-tcp-handler)))