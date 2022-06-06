(ns ssm.config
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clj-yaml.core :as yaml]))

(defn load [path]
  (yaml/parse-string (slurp path) {:keywords true}))

(defn write [path config]
  (let [dump-options {:indent 2 :flow-style :block}
        lines (str/split-lines (yaml/generate-string config :dumper-options dump-options))]
    (with-open [w (io/writer path)]
      (doseq [line lines]
        (.write w line)
        (.newLine w)))))
