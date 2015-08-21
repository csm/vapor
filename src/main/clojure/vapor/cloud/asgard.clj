(ns vapor.cloud.asgard
  "Cloud inventory based on AWS/Asgard"
  (require [amazonica.aws.simpledb :as sdb]
           [amazonica.aws.ec2 :as ec2]
           [amazonica.aws.autoscaling :as autoscale]
           [vapor.cloud.inventory]
           [clojure.string :refer [upper-case lower-case]])
  (import [com.netflix.frigga Names]))

(defrecord AsgardCloudInventory []
  vapor.cloud.inventory/ICloudInventory
  (apps [this]
    (map lower-case
         (map :name (:items (sdb/select {:select-expression "SELECT name FROM CLOUD_APPLICATIONS"})))))

  (app-info [this app-name]
    (into {}
          (map (fn [e] [(keyword (:name e)) (:value e)])
               (:attributes (sdb/get-attributes {:domain-name "CLOUD_APPLICATIONS" :item-name (upper-case app-name)})))))

  (stacks [this]
    (into #{}
          (filter #(not (nil? %))
                  (->> (ec2/describe-tags {:filters [{:name "tag:aws:autoscaling:groupName" :values ["*"]}]})
                       (:tags)
                       (map :value)
                       (map #(Names/parseName %))
                       (map #(.getStack %))))))

  (clusters [this app-name]
    (into #{}
          (filter #(not (nil? %))
                  (->> (ec2/describe-tags {:filters [{:name "tag:aws:autoscaling:groupName" :values [(str (lower-case app-name) "-*")]}]})
                       (:tags)
                       (map :value)
                       (map #(Names/parseName %))
                       (map #(.getCluster %))))))

  (cluster-instances [this cluster-name]
    (->> (ec2/describe-tags {:filters [{:name "tag:aws:autoscaling:groupName" :values [(str cluster-name "*")]}]})
         (:tags)
         (filter #(= "instance" (:resource-type %)))
         (map :resource-id)))

  (instance-infos [this instance-ids]
    (->> (ec2/describe-instances {:instance-ids instance-ids})
         (:reservations)
         (map :instances)
         flatten
         )))
