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
 ROS_HOSTNAME=ROSCOE2ORIN
 ROS_MASTER=ROSCOE2M2
 ROS_NODECLASS=$1
 DEBUG=true
 #MODEL=/home/jg/Downloads/Llama-3.3-70B-Instruct-Q4_K_M.gguf
 #
 runmodel() {
java -server -Xmx8g --enable-native-access=ALL-UNNAMED --enable-preview --add-modules jdk.incubator.vector -Djava.library.path=/usr/lib/jni -DRoboCore.properties=/usr/share/jars/RoboCore.properties -cp /usr/share/jars/RosCore.jar:/usr/share/jars/RosBase.jar:/usr/share/jars/Ros.jar:/usr/share/jars/RosMsgs.jar:/usr/share/jars/commons-logging-1.1.2.jar:/usr/share/jars/RoboCore.jar:/usr/share/jars/rknn4j.jar:/usr/share/jars/RockSack.jar:/usr/share/jars/Relatrix.jar:/usr/share/jars/jsoup-1.20.1.jar:/usr/share/jars/rocksdbjni-9.10.0-linux-aarch64.jar:/usr/share/jars/json.jar:/usr/share/jars/rosai.jar org.ros.RosLauncher $ROS_NODECLASS __master:=$ROS_MASTER __ip:=$ROS_HOSTNAME __debug:=$DEBUG -m $MODEL -n 16000 -temp 1 --top-p 0.95 --min-p .1 -i> $LOGFILE &
}
#
run() {
#set -x
java -server -Xmx8g --enable-native-access=ALL-UNNAMED --enable-preview --add-modules jdk.incubator.vector -Djava.library.path=/usr/lib/jni -DRoboCore.properties=/usr/share/jars/RoboCore.properties -cp /usr/share/jars/RosCore.jar:/usr/share/jars/RosBase.jar:/usr/share/jars/Ros.jar:/usr/share/jars/RosMsgs.jar:/usr/share/jars/commons-logging-1.1.2.jar:/usr/share/jars/RoboCore.jar:/usr/share/jars/rknn4j.jar:/usr/share/jars/RockSack.jar:/usr/share/jars/Relatrix.jar:/usr/share/jars/jsoup-1.20.1.jar:/usr/share/jars/rocksdbjni-9.10.0-linux-aarch64.jar:/usr/share/jars/json.jar:/usr/share/jars/rosai.jar org.ros.RosLauncher -temp 1 --min-p .1 --top-p 0.95 -i $ROS_NODECLASS __master:=$ROS_MASTER __ip:=$ROS_HOSTNAME >$LOGFILE &
#set +x
}
rmunnolog() {
java server -Xmx8g --enable-native-access=ALL-UNNAMED --enable-preview --add-modules jdk.incubator.vector -Djava.library.path=/usr/lib/jni -DRoboCore.properties=/usr/share/jars/RoboCore.properties -cp /usr/share/jars/RosCore.jar:/usr/share/jars/RosBase.jar:/usr/share/jars/Ros.jar:/usr/share/jars/RosMsgs.jar:/usr/share/jars/commons-logging-1.1.2.jar:/usr/share/jars/RoboCore.jar:/usr/share/jars/rknn4j.jar:/usr/share/jars/RockSack.jar:/usr/share/jars/Relatrix.jar:/usr/share/jars/jsoup-1.20.1.jar:/usr/share/jars/rocksdbjni-9.10.0-linux-aarch64.jar:/usr/share/jars/json.jar:/usr/share/jars/rosai.jar org.ros.RosLauncher -temp 1 --min-p .1 --top-p .95 -i $ROS_NODECLASS __master:=$ROS_MASTER __ip:=$ROS_HOSTNAME &
}
if [ "$LOGFILE" == *.gguf ]; then
	ECHO "REFUSING TO OVERWRITE MODEL FILE!"
	exit 1
fi
case "$#" in
    1)
	runnolog
	;;
    2)
	LOGFILE=$2
        run
        ;;
    3)
	MODEL=$2
	LOGFILE=$3
        runmodel 
        ;;
    *)
        echo "wrong number of args"
        exit 1
        ;;
esac
exit 0
