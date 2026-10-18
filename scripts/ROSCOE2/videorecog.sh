 #!/bin/bash
 #
 ### BEGIN INIT INFO
 # Provides:
 # Should-Start:
 # Short-Description: Bring up/down
 # Description: Bring up/down
 ### END INIT INFO
 # Source function library.
 . /lib/init/vars.sh
 . /lib/lsb/init-functions

 CWD=$(pwd)
 prog="rosrun"
 PATH=$PATH:/usr/bin/java
 JRE_HOME=/usr/bin/java
 ROS_HOSTNAME=roslet
 ROS_MASTER=roslet
 MAINCLASS=com.neocoretechs.robocore.video.VideoObjectRecog
 LOG=/home/odroid/videorecog.log
 MODELDIR=/etc
java -Xmx1024M --enable-preview --add-modules jdk.incubator.vector -Djava.library.path=/usr/lib/jni -DRoboCore.properties=/usr/share/jars/RoboCore.properties -cp /usr/share/jars/RosCore.jar:/usr/share/jars/RosBase.jar:/usr/share/jars/Ros.jar:/usr/share/jars/RosMsgs.jar:/usr/share/jars/commons-logging-1.1.2.jar:/usr/share/jars/RoboCore.jar:/usr/share/jars/rknn4j.jar:/usr/share/jars/RockSack.jar:/usr/share/jars/roscar.jar:/usr/share/jars/Relatrix.jar:/usr/share/jars/jsoup-1.20.1.jar:/usr/share/jars/rocksdbjni-9.10.0-linux-aarch64.jar:/usr/share/jars/json.jar org.ros.RosLauncher $MAINCLASS __master:=$ROS_MASTER __ip:=$ROS_HOSTNAME __modelFile:=yolo11s.rknn>$LOG&

