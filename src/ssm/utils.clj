(ns ssm.utils
  (:require [clojure.string :as str]))

(defn normalize-key
  "Normalizes provided key, removing extra / signs."
  [key]
  (->> (str/split key #"/")
       (filter #(> (count %) 0))
       (str/join "/")))

(defn map->nsmap
"Apply the string n to the supplied structure m as a namespace."
 [m n]
 (clojure.walk/postwalk
 (fn [x]
   (if (keyword? x)
     (keyword n (name x))
     x))
  m))

(defn now-time []
  (.format (java.text.SimpleDateFormat. "MM.dd.yyyy-HH_mm_ss") (new java.util.Date)))
