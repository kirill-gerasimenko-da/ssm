(ns ssm.encrypt
  (:require
   [clojure.java.io :as io]
   [babashka.fs :as fs]
   [pod.babashka.aws.config :as aws-config]
   [pod.babashka.aws.credentials :as aws-cred]
   [pod.babashka.aws :as aws])
  (:import java.util.Base64))

(defn- default-aws-config-path []
  (-> (fs/path (fs/home) ".aws/config")
      (fs/absolutize)
      (fs/normalize)
      (str)))

(defn- aws-config-path []
  (or (System/getenv "AWS_CONFIG_FILE") (default-aws-config-path)))

(defn- resolve-kms-key [& {:keys [profile kms-key]}]
  (let [config-path (aws-config-path)]
    (or kms-key (when (fs/exists? config-path)
                  (get-in (aws-config/parse config-path)
                          [(or profile "default") "ssm-param-files-key"])))))

(defn- encode [to-encode]
  (.encodeToString (Base64/getEncoder) to-encode))

(defn- decode [to-decode]
  (.decode (Base64/getDecoder) to-decode))

(defn encrypt
  [text & {:keys [profile kms-key]}]
  (let [profile (or profile "default")
        kms-key (resolve-kms-key :profile profile :kms-key kms-key)
        request  {:op :Encrypt :request {:KeyId kms-key :Plaintext (.getBytes text)}}
        kms-client (aws/client {:api :kms
                                :credentials-provider (aws-cred/profile-credentials-provider profile)})
        response (aws/invoke kms-client request)]
    (when-not kms-key (throw (Exception. "Encryption key is not found.")))
    (if (:cognitect.anomalies/category response)
      (throw (Exception. (str "Failed encrypting: " response)))
      (-> response
          (:CiphertextBlob)
          (.readAllBytes)
          (encode)))))

(defn decrypt
  [text & {:keys [region]}]
  (let [bytes      (try (-> (decode text) (io/input-stream))
                        (catch Exception e
                          (throw (Exception. (str "Failed to decode the text: " e)))))
        request    {:op :Decrypt :request {:CiphertextBlob bytes}}
        kms-client (aws/client {:api :kms :region region})
        response (aws/invoke kms-client request)]
    (if (:cognitect.anomalies/category response)
      (throw (Exception. (str "Failed decrypting: " response)))
      (-> response
          (:Plaintext)
          (slurp)))))
