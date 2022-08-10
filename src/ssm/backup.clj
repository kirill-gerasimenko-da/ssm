(ns ssm.backup
  (:require
   [babashka.fs :as fs]
   [ssm.params :as par]
   [ssm.utils  :as utl]
   [ssm.config :as cfg]))

(defn get-backup-path
  [backup-dir region prefix env]
  (let [file-name   (str "backup_" env "_" (utl/now-time) ".yaml")
        path        (fs/path backup-dir region (utl/normalize-key prefix) file-name)
        config-path (str (fs/absolutize path))]
    config-path))

(defn ssm->file
  [backup-path region prefix env]
  (let [env-prefix  (str prefix "/" env)
        params      (par/get-all-params region env-prefix)
        config      (par/params->config region prefix params)]
    (fs/create-dirs (fs/parent backup-path))
    (cfg/write-config backup-path config)
    {:backup-path backup-path
     :parameters (map :Name params)}))
