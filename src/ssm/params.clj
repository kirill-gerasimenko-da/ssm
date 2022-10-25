(ns ssm.params
  (:require [pod.babashka.aws :as aws]
            [pod.babashka.aws.credentials :as aws-cred]
            [clojure.string :as str]
            [ssm.utils :as utl]))

(defn- get-params
  [profile prefix next-token filter-out-secure?]
  (let [request    {:op      :GetParametersByPath
                    :request {:Path           prefix
                              :Recursive      true
                              :WithDecryption true
                              :NextToken      next-token
                              :MaxResults     10}}
        ssm-client (aws/client {:api                  :ssm
                                :credentials-provider (aws-cred/profile-credentials-provider profile)})
        request    (if filter-out-secure?
                     (assoc-in request [:request :ParameterFilters] [{:Key "Type" :Values "String"}])
                     request)
        response   (aws/invoke ssm-client request)]
    (if (:cognitect.anomalies/category response)
      (throw (Exception. (str "Failed getting params: " response)))
      response)))

(defn- param->map
  [prefix {:keys [Value Type Name Version]}]
  (let [no-prefix-name   (-> Name
                             (str/replace-first prefix "")
                             (utl/normalize-key))
        [_ env par-name] (re-find #"([^/]*)/(.*)" no-prefix-name)]
    {(keyword par-name) {:values {(keyword env) Value}
                         :type Type
                         :version Version}}))

(defn get-all-params [profile prefix filter-out-secure?]
  (loop [{:keys [NextToken]} nil
         params              []]
    (let [response (get-params profile prefix NextToken filter-out-secure?)]
      (if (:NextToken response)
        (recur response (concat params (:Parameters response)))
        (vec (concat params (:Parameters response)))))))

(defn params->config [prefix params]
  {:prefix     prefix
   :parameters (into {} (map #(param->map prefix %) params))})

(defn- put-param
  [profile type name value]
  (let [request    {:op      :PutParameter
                    :request {:Overwrite true
                              :Tier      "Standard"
                              :Name      name
                              :DataType  "text"
                              :Type      type
                              :Value     value}}
        ssm-client (aws/client {:api                  :ssm
                                :credentials-provider (aws-cred/profile-credentials-provider profile)})
        response   (aws/invoke ssm-client request)]
    (when (:cognitect.anomalies/category response)
      (throw (Exception. (str "Failed updating params: " response))))))

(defn config->ssm
  [profile {:keys [prefix parameters]} env filter-out-secure?]
  (->> (keys parameters)
       (map (fn [k]
              (let [param (k parameters)
                    name  (str "/" (utl/normalize-key (str prefix "/" env "/" (subs (str k) 1))))
                    type  (:type param)
                    value (get-in param [:values (keyword env)])]
                (when (and value (or (not filter-out-secure?)
                                     (not (= type "SecureString"))))
                  (put-param profile type name value)
                  name))))
       (filter #(not (nil? %)))
       (doall)))

(defn remove-params [profile params]
  (let [batches (partition 10 10 nil params)]
    (doseq [batch batches]
      (let [request    {:op :DeleteParameters :request {:Names (vec batch)}}
            ssm-client (aws/client {:api                  :ssm
                                    :credentials-provider (aws-cred/profile-credentials-provider profile)})
            response   (aws/invoke ssm-client request)]
        (when (:cognitect.anomalies/category response)
          (throw (Exception. (str "Failed removing params: " response))))))))


