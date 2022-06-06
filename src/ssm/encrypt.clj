(ns ssm.encrypt
  (:require
   [clojure.java.io :as io]
   [pod.babashka.aws :as aws])
  (:import java.util.Base64))

(defn- encode [to-encode]
  (.encodeToString (Base64/getEncoder) to-encode))

(defn- decode [to-decode]
  (.decode (Base64/getDecoder) to-decode))

(defn encrypt
  [text key & {:keys [region]}]
  (let [request  {:op :Encrypt :request {:KeyId key :Plaintext (.getBytes text)}}
        kms-client (aws/client {:api :kms :region region})
        response (aws/invoke kms-client request)]
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
