(ns modules.system.gather-facts
  (require [clojure.string])
  (:import (java.net InetAddress InterfaceAddress NetworkInterface Inet4Address Inet6Address)))

(def vapor-module-info
  {:name "gather-facts"
   :author "Casey Marshall"
   :description "Gathers facts about the host system."})

(defn netifs
  []
  (into {}
        (map (fn [ni] [(.getDisplayName ni)
                       {:name           (.getDisplayName ni)
                        :up?            (.isUp ni)
                        :loopback?      (.isLoopback ni)
                        :virtual?       (.isVirtual ni)
                        :mtu            (.getMTU ni)
                        :mac-address    (clojure.string/join ":" (map #(format "%02x" %) (.getHardwareAddress ni)))
                        :ipv4-addresses (map (fn [^InterfaceAddress addr] {:address (when-let [a (.getAddress addr)] (.getHostAddress a))
                                                                           :broadcast (when-let [bc (.getBroadcast addr)] (.getHostAddress bc))
                                                                           :prefix-length (.getNetworkPrefixLength addr)})
                                             (filter #(instance? Inet4Address (.getAddress %)) (.getInterfaceAddresses ni)))
                        :ipv6-addresses (map (fn [^InterfaceAddress addr] {:address (when-let [a (.getAddress addr)] (.getHostAddress a))
                                                                           :broadcast (when-let [bc (.getBroadcast addr)] (.getHostAddress bc))
                                                                           :prefix-length (.getNetworkPrefixLength addr)})
                                             (filter #(instance? Inet6Address (.getAddress %)) (.getInterfaceAddresses ni)))}])
       (enumeration-seq (NetworkInterface/getNetworkInterfaces)))))

(prn {:arch (System/getProperty "os.arch")
      :os (System/getProperty "os.name")
      :java-version (System/getProperty "java.version")
      :java-vendor (System/getProperty "java.vendor")
      :network-interfaces (netifs)})