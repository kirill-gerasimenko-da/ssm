(ns ssm.backup
  (:require
   [babashka.fs :as fs]
   [ssm.params :as par]
   [ssm.utils  :as utl]
   [ssm.config :as cfg]))

(defn get-backup-path
  [backup-dir prefix env]
  (let [file-name   (str "backup_" env "_" (utl/now-time) ".yaml")
        path        (fs/path backup-dir (utl/normalize-key prefix) file-name)
        config-path (str (fs/absolutize path))]
    (-> config-path fs/normalize str)))

(defn ssm->file
  [backup-path profile prefix env]
  (let [env-prefix (str prefix "/" env)
        params     (par/get-all-params profile env-prefix)
        config     (par/params->config prefix params)]
    (fs/create-dirs (fs/parent backup-path))
    (cfg/write-config backup-path config)
    {:backup-path backup-path
     :parameters  (map :Name params)}))
