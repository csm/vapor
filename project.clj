(defproject vapor "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.google.guava/guava "18.0"]
                 [org.apache.sshd/sshd-core "0.14.0"]
                 [org.bouncycastle/bcprov-jdk15on "1.52"]
                 [org.bouncycastle/bcpkix-jdk15on "1.52"]]
  :main ^:skip-aot vapor.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :java-source-paths ["javasrc"])
