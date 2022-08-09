(ns ssm.core
  (:require
   [clojure.spec.test.alpha :as stest]
   [clojure.spec.alpha :as s]
   [clojure.set :as set]
   [babashka.fs :as fs]
   [ssm.utils :as utl]
   [ssm.params :as params]
   [ssm.backup :as backup]
   [ssm.encrypt :as enc]
   [ssm.config :as cfg]))

(defn instrument-all []
  (s/def ::non-empty-string (s/and string? not-empty))
  (s/def ::nil-or-non-empty-string (s/or :nil nil? :non-empty ::non-empty-string))

  (s/fdef sync-parameters
    :args (s/cat :config-file ::non-empty-string
                 :env ::non-empty-string
                 :backup-dir ::non-empty-string
                 :kwargs (s/keys* :opt-un [::decrypt-region])))

  (s/fdef encrypt-parameter
    :args (s/cat :text ::non-empty-string
                 :key ::nil-or-non-empty-string
                 :kwargs (s/keys* :opt-un [::region])))

  (s/fdef decrypt-parameter
    :args (s/cat :text ::non-empty-string
                 :kwargs (s/keys* :opt-un [::region])))

  (stest/instrument `ssm.core/sync-parameters)
  (stest/instrument `ssm.core/encrypt-parameter)
  (stest/instrument `ssm.core/decrypt-parameter))

(defn- backup-existing-params [backup-path region prefix env]
  (println "Backing up existing SSM parameters under" prefix "for" env "environment...")
  (let [backed-up (:parameters (backup/ssm->file backup-path region prefix env))]
    (doseq [name (sort backed-up)] (println name))
    (println "Done.")
    backed-up))

(defn- update-params-from-config [config env]
  (println "Updating SSM parameters from the config file...")
  (let [updated (params/config->ssm config env)]
    (doseq [name (sort updated)] (println name))
    (println "Done.")
    updated))

(defn- delete-params [region backed-up updated]
  (println "Deleting SSM parameters which are obsolete...")
  (let [to-delete (set/difference (set backed-up) (set updated))]
    (params/remove-params region to-delete)
    (doseq [name (sort to-delete)] (println name))
    (println "Done.")))

(defn- dump-params [{:keys [region prefix parameters]} env]
  (doall (map (fn [k]
                (let [param (k parameters)
                      name  (str "/" (utl/normalize-key (str prefix "/" env "/" (subs (str k) 1))))
                      type  (:type param)
                      value (get-in param [:values (keyword env)])
                      cmd   (str "aws ssm put-parameter --overwrite "
                                 "--name " "\"" name  "\" "
                                 "--type " type " "
                                 "--value " "\"" value "\"")]
                  (when value
                    (println cmd))
                  name))
              (keys parameters))))

(defn- decrypt-param
  "Returns paramter as is if it is not of SecureString type. If not - it
  decrypts the value for the specified environment and returns the parameter."
  [env decrypt [par-name {:keys [type values] :as par}]]
  (if (= type "SecureString")
    (let [env-key        (keyword env)
          decrypted      (try (decrypt (get values env-key))
                              (catch Exception e (throw (Exception. (str "Can not decrypt parameter "
                                                                         par-name " for environment '"
                                                                         env "' because:\n" e)))))
          updated-values (assoc values env-key decrypted)]
      {(keyword par-name) (assoc par :values updated-values)})
    {(keyword par-name) par}))

(defn- decrypt-secure-strings
  "Decrypts all the SecureString typed parameters for the specified env."
  [config env decrypt]
  (let [decrypt-env-param (partial decrypt-param env decrypt)
        updated-params (->> config
                            (:parameters)
                            (map decrypt-env-param)
                            (into {}))]
    (assoc config :parameters updated-params)))

(defn sync-parameters
  [config-file backup-dir env & {:keys [decrypt-region dump]}]
  (let [config-path      (-> config-file fs/absolutize fs/normalize str)
        config           (-> config-path cfg/load)
        region           (:region config)
        prefix           (:prefix config)
        decrypted-config (decrypt-secure-strings config env (fn [text] (enc/decrypt text :region decrypt-region)))
        backup-path      (-> (backup/get-backup-path backup-dir region prefix env) fs/normalize str)]

    (if dump
      (dump-params decrypted-config env)
      (do
        (println "====================================================")
        (println "Region:" region)
        (println "Environment:" env)
        (println "Config path:" config-path)
        (println "Backup path:" backup-path)
        (println "====================================================")

        (println "Starting...")

        (let [backed-up (backup-existing-params backup-path region prefix env)
              updated   (update-params-from-config decrypted-config env)]
          (delete-params region backed-up updated))))))

(defn encrypt-parameter
  [text key & {:keys [region]}]
  (let [final-key (or key (let [aws-config-path (-> (fs/path (fs/home) ".aws/config")
                                                    (fs/absolutize)
                                                    (fs/normalize)
                                                    (str))]
                            (when (fs/exists? aws-config-path)
                              (get-in (aws-config/parse aws-config-path)
                                      ["default" "ssm-param-files-key"]))))]

    (if final-key
      (println (enc/encrypt text final-key :region region))
      (throw (Exception. "Encryption key is not found.")))))

(defn decrypt-parameter
  [text & {:keys [region]}]
  (println (enc/decrypt text :region region)))
