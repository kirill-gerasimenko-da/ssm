(ns ssm.core
  (:require
   [clojure.spec.test.alpha :as stest]
   [clojure.spec.alpha :as s]
   [clojure.set :as set]
   [babashka.fs :as fs]
   [ssm.utils :as u]
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
                 :kwargs (s/keys* :opt-un [::profile ::decrypt-profile ::dump])))

  (s/fdef encrypt-parameter
    :args (s/cat :text ::non-empty-string
                 :kwargs (s/keys* :opt-un [::profile ::kms-key])))

  (s/fdef decrypt-parameter
    :args (s/cat :text ::non-empty-string
                 :kwargs (s/keys* :opt-un [::profile])))

  (stest/instrument `ssm.core/sync-parameters)
  (stest/instrument `ssm.core/encrypt-parameter)
  (stest/instrument `ssm.core/decrypt-parameter))

(defn- backup-existing-params [backup-path profile prefix env filter-out-secure?]
  (println "Backing up existing SSM parameters under" prefix "for" env "environment...")
  (let [backed-up (:parameters (backup/ssm->file backup-path profile prefix env filter-out-secure?))]
    (doseq [name (sort backed-up)] (println name))
    (println "Done.")
    backed-up))

(defn- update-params-from-config [profile config env filter-out-secure?]
  (println "Updating SSM parameters from the config file...")
  (let [updated (params/config->ssm profile config env filter-out-secure?)]
    (doseq [name (sort updated)] (println name))
    (println "Done.")
    updated))

(defn- delete-params [profile backed-up updated]
  (println "Deleting SSM parameters which are obsolete...")
  (let [to-delete (set/difference (set backed-up) (set updated))]
    (params/remove-params profile to-delete)
    (doseq [name (sort to-delete)] (println name))
    (println "Done.")))

(defn- dump-params [{:keys [prefix parameters]} env profile filter-out-secure?]
  (doall (map (fn [k]
                (let [param (k parameters)
                      name  (str "/" (u/normalize-key (str prefix "/" env "/" (subs (str k) 1))))
                      type  (:type param)
                      value (get-in param [:values (keyword env)])
                      cmd   (str "aws ssm put-parameter --overwrite "
                                 "--name " "\"" name  "\" "
                                 "--type " type " "
                                 "--value " "\"" value "\" "
                                 "--profile " "\"" profile "\"")]
                  (when (and value (or (not filter-out-secure?)
                                     (not (= type "SecureString"))))
                    (println cmd))
                  name))
              (keys parameters))))

(defn- decrypt-param
  "Returns paramter as is if it is not of SecureString type. If not - it
  decrypts the value for the specified environment and returns the parameter."
  [env decrypt [par-name {:keys [type values] :as par}]]
  (if (= type "SecureString")
    (let [env-key   (keyword env)
          decrypted (try (some-> (get values env-key)
                                 (decrypt))
                         (catch Exception e (throw (Exception. (str "Can not decrypt parameter "
                                                                    par-name " for environment '"
                                                                    env "' because:\n" e)))))]
      (when decrypted
        (let [updated-values (assoc values env-key decrypted)]
          {(keyword par-name) (assoc par :values updated-values)})))
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
  [config-file backup-dir env & {:keys [profile decrypt-profile dump filter-out-secure?]}]
  (let [profile            (u/resolve-aws-profile profile)
        decrypt-profile    (or decrypt-profile profile)
        config-path        (-> config-file fs/absolutize fs/normalize str)
        config             (-> config-path cfg/load-config)
        prefix             (:prefix config)
        backup-path        (backup/get-backup-path backup-dir prefix env)
        decrypt-fn         (fn [text] (enc/decrypt text :profile decrypt-profile))
        decrypted-config   (decrypt-secure-strings config env decrypt-fn)]

    (if dump
      (dump-params decrypted-config env profile filter-out-secure?)
      (do
        (println "====================================================")
        (println "Environment:" env)
        (println "Config path:" config-path)
        (println "Backup path:" backup-path)
        (println "Secure values filtering:" filter-out-secure?)
        (println "====================================================")

        (println "Starting...")

        (let [backed-up (backup-existing-params backup-path profile prefix env filter-out-secure?)
              updated   (update-params-from-config profile decrypted-config env filter-out-secure?)]
          (delete-params profile backed-up updated))))))

(defn encrypt-parameter
  [text & {:keys [profile kms-key]}]
  (println (enc/encrypt text :profile profile :kms-key kms-key)))

(defn decrypt-parameter
  [text & {:keys [profile]}]
  (println (enc/decrypt text :profile profile)))
