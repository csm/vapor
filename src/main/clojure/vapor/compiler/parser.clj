(ns vapor.compiler.parser
  (:import (java.io PushbackReader Reader InputStream InputStreamReader FileReader)))

(defn parse
  [input]
  (cond
    (instance? PushbackReader input) (read input)
    (instance? Reader input) (read (PushbackReader. input))
    (instance? InputStream input) (PushbackReader. (InputStreamReader. input))
    (string? input) (PushbackReader. (FileReader. input))))