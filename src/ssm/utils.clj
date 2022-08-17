(ns ssm.utils
  (:require [clojure.string :as str]
            [babashka.fs :as fs]))

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

(defn default-aws-config-path []
  (-> (fs/path "~/.aws/config")
      (fs/expand-home)
      (fs/normalize)
      (str)))

(defn aws-config-path []
  (or (System/getenv "AWS_CONFIG_FILE") (default-aws-config-path)))

(defn resolve-aws-profile [profile]
  (or profile (System/getenv "AWS_PROFILE") "default"))
