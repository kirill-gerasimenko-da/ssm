(ns ssm.encrypt
  (:require
   [clojure.java.io :as io]
   [babashka.fs :as fs]
   [pod.babashka.aws.config :as aws-config]
   [pod.babashka.aws.credentials :as aws-cred]
   [pod.babashka.aws :as aws]
   [ssm.utils :as u])
  (:import java.util.Base64))

(defn- resolve-kms-key [& {:keys [profile kms-key]}]
  (let [config-path (u/aws-config-path)
        key (or kms-key (when (fs/exists? config-path)
                          (get-in (aws-config/parse config-path)
                                  [(u/resolve-aws-profile profile) "ssm-param-files-key"])))]
    key))

(defn- encode [to-encode]
  (.encodeToString (Base64/getEncoder) to-encode))

(defn- decode [to-decode]
  (.decode (Base64/getDecoder) to-decode))

(defn encrypt
  [text & {:keys [profile kms-key]}]
  (let [profile (u/resolve-aws-profile profile)
        kms-key (resolve-kms-key :profile profile :kms-key kms-key)
        _ (when-not kms-key (throw (Exception. "Encryption key is not found.")))
        request  {:op :Encrypt :request {:KeyId kms-key :Plaintext (.getBytes text)}}
        kms-client (aws/client {:api :kms
                                :credentials-provider (aws-cred/profile-credentials-provider profile)})
        response (aws/invoke kms-client request)]
    (if (:cognitect.anomalies/category response)
      (throw (Exception. (str "Failed encrypting: " response)))
      (-> response
          (:CiphertextBlob)
          (.readAllBytes)
          (encode)))))

(defn decrypt
  [text & {:keys [profile]}]
  (let [profile (u/resolve-aws-profile profile)
        bytes      (try (-> (decode text) (io/input-stream))
                        (catch Exception e
                          (throw (Exception. (str "Failed to decode the text: " e)))))
        request    {:op :Decrypt :request {:CiphertextBlob bytes}}
        kms-client (aws/client {:api :kms
                                :credentials-provider (aws-cred/profile-credentials-provider profile)})
        response (aws/invoke kms-client request)]
    (if (:cognitect.anomalies/category response)
      (throw (Exception. (str "Failed decrypting: " response)))
      (-> response
          (:Plaintext)
          (slurp)))))
