 #! /bin/bash
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
 #core=roscore
 #coreclass=RosCore
 progs=(model)
 #database=/home/jg/db/ai
 classes=(ModelRunner)
 PATH=$PATH:/usr/bin/java
 JRE_HOME=/usr/bin/java
 start() {
	# Start the master
	#/home/odroid/$core.sh $database
 	#sleep 5
 	#if ps ax | grep -v "grep" | grep -q "$coreclass" ; then echo "startup"; else echo "startup FAIL"; fi
       #echo
       # Attach 
	indices=(${!progs[*]})
	for ((i=0; i<${#indices[*]}; i++));
	do
    		echo ${progs[${indices[i]}]}
		prog=${progs[${indices[i]}]}
		class=${classes[${indices[i]}]}
		echo $prog $class
		/home/jg/$prog.sh
 		sleep 2
 		if ps ax | grep -v "grep" | grep -q "$class" ; then echo "startup"; else echo "startup FAIL"; fi
		echo $prog $class
	done
 }
 stop() {
       # Stop services.
	indices=(${!progs[*]})
	for ((i=0; i<${#indices[*]}; i++));
	do
    		echo ${progs[${indices[i]}]}
		prog=${progs[${indices[i]}]}
		class=${classes[${indices[i]}]}
		echo $prog $class
 		/bin/kill -9 $(ps ax | grep '[j]ava' | grep "$class" | awk '{print $1}') 
 		sleep 5
 		if ps ax | grep -v "grep" | grep -q "$class" ; then echo "$prog shutdown FAIL"; else echo "$prog shutdown"; fi
       		echo
	done
	# shutdown master
 	#/bin/kill -9 $(ps ax | grep '[j]ava' | grep "$coreclass" | awk '{print $1}') 
 	#sleep 5
 	#if ps ax | grep -v "grep" | grep -q "$coreclass" ; then echo "$core shutdown FAIL"; else echo "$core shutdown"; fi
       	#echo
 }

 # See how we were called.
 case "$1" in
 start)
 	start
       ;;
 stop)
 	stop
       ;;
 restart|reload)
 	stop
 	start
 	;;
 *)
       echo $"Usage: $0 {start|stop|restart}"
       exit 1
 esac
 
 exit 0
