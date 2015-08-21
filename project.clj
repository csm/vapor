(defproject vapor "0.1.0-SNAPSHOT"
  :description "vapor, you know, for clouds"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.google.guava/guava "18.0"]
                 [org.apache.sshd/sshd-core "0.14.0"]
                 [org.bouncycastle/bcprov-jdk15on "1.52"]
                 [org.bouncycastle/bcpkix-jdk15on "1.52"]
                 [amazonica "0.3.30"]
                 [com.netflix.frigga/frigga "0.13"]
                 [net.java.dev.jna/jna "4.1.0"]
                 [com.google.protobuf/protobuf-java "2.6.1"]
                 [riemann "0.2.10"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.3"]]
  :plugins [[lein-protobuf "0.4.3"]]
  :main ^:skip-aot vapor.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :proto-path "src/main/proto"
  :resource-paths ["src/main/resources"])
