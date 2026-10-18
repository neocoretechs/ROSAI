 #!/bin/bash
 #
 ### BEGIN INIT INFO
 # Provides:
 # Short-Description: run ros node
 # Description: run ros node depending on number of cmdl args
 ### END INIT INFO
 # Source function library.
 . /lib/init/vars.sh
 . /lib/lsb/init-functions

 CWD=$(pwd)
 prog="rosrun"
 PATH=$PATH:/usr/bin/java
 JRE_HOME=/usr/bin/java
 DEBUG=true
 MODEL=/home/jg/Downloads/Llama-3.3-70B-Instruct-Q4_K_M.gguf
 #
java -Xmx8192M --enable-preview --add-modules jdk.incubator.vector -Djava.library.path=/usr/lib/jni -DRoboCore.properties=/usr/share/jars/RoboCore.properties -cp /usr/share/jars/RosCore.jar:/usr/share/jars/RosBase.jar:/usr/share/jars/Ros.jar:/usr/share/jars/RosMsgs.jar:/usr/share/jars/commons-logging-1.1.2.jar:/usr/share/jars/RoboCore.jar:/usr/share/jars/rknn4j.jar:/usr/share/jars/RockSack.jar:/usr/share/jars/Relatrix.jar:/usr/share/jars/jsoup-1.20.1.jar:/usr/share/jars/rocksdbjni-9.10.0-linux-aarch64.jar:/usr/share/jars/json.jar:/usr/share/jars/rosai.jar com.neocoretechs.rosai.metadata.MetadataReader $MODEL
