(ns vapor.core.util
  (require [clojure.string :refer [join]])
  (import [java.io FileInputStream]
          [java.security DigestInputStream MessageDigest]))

(defn hash-file
  ([path] (hash-file path "SHA-256"))
  ([path hash]
   (let [md (MessageDigest/getInstance hash)
         in (DigestInputStream. (FileInputStream. path) md)]
     (slurp in)
     (join (map (partial format "%02x") (.digest md))))))
