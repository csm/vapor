(ns modules.system.hash-file
  (require [clojure.java.io :refer [input-stream]]
           [clojure.string :refer [join]])
  (import [java.security MessageDigest]
          [java.io InputStream]))

(defn input-seq
  [^InputStream instream]
  (let [f (fn step []
            (let [c (.read instream)]
               (when-not (== -1 c)
                 (cons (byte c) (lazy-seq (step))))))]
    (lazy-seq (f))))

(let [opts (read)]
  (with-open [input (input-stream (:file opts))]
    (let [hash (MessageDigest/getInstance (or (:hash opts) "SHA-256"))]
      (doall (map #(.update hash %) (input-seq input)))
      (prn {:digest (join (map (partial format "%02x") (.digest hash)))}))))