heap_size=$(grep -w "botify.preferences.max_heap_size" src/main/resources/application.properties|cut -d'=' -f2)
if [ -z "$heap_size" ]
then
  java -Dsun.net.httpserver.maxReqTime=30 -Dsun.net.httpserver.maxRspTime=30 -jar build/libs/botify-1.0-SNAPSHOT.jar
else
  java -Xmx"$heap_size" -Dsun.net.httpserver.maxReqTime=30 -Dsun.net.httpserver.maxRspTime=30 -jar build/libs/botify-1.0-SNAPSHOT.jar
fi
