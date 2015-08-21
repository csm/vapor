(ns vapor.cloud.inventory)

(defprotocol ICloudInventory
  (apps [this] "Return a list of application names.")
  (app-info [this app-name] "Return a map of information about the app named app-name.")
  (stacks [this] "Return a list of all stack names.")
  (clusters [this app-name] "Return a list of clusters in application app-name.")
  (cluster-instances [this cluster-name] "Return a list of instance IDs in the cluster cluster-name.")
  (instance-infos [this instance-ids] "Return a list of instance info given a list of instance-ids."))