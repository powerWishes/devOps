#\!/bin/sh
## java env
export JAVA_HOME={javaHome}
export JRE_HOME=$JAVA_HOME/jre
export JAVA_TOOL_OPTIONS="-Dfile.encoding=utf-8 -Duser.timezone=Asia/shanghai"
SERVICE_DIR={serverDir}
JARNAME={jarName}
PID={pid}.pid
cd $SERVICE_DIR
case "$1" in

  start)
    nohup $JAVA_HOME/bin/java -Xms{minMem} -Xmx{maxMem} -jar $JARNAME --spring.profiles.active={runtime} > /dev/null 2>&1 &
    echo $! > $SERVICE_DIR/$PID
    echo "== service start"
    ;;
  stop)
    kill -9 `cat $SERVICE_DIR/$PID`
    rm -rf $SERVICE_DIR/$PID
    echo "== service stop"
    ;;
  restart)
    $0 stop
    sleep 2
    $0 start
    echo "== service restart success"
    ;;
  *)
   # echo "Usage: service.sh {start|stop|restargei}"
    $0 stop
    sleep 2
    $0 start
    echo "== service restart success"
    ;;
esac
exit 0