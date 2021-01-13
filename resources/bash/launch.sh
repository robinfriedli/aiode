heap_size=$(grep -w "HEAP_SIZE" resources/settings.properties|cut -d'=' -f2)
if [ -z "$heap_size" ]
then
  java -Dsun.net.httpserver.maxReqTime=30 -Dsun.net.httpserver.maxRspTime=30 -jar target/botify-1.0-SNAPSHOT.jar
else
  java -Xmx"$heap_size" -Dsun.net.httpserver.maxReqTime=30 -Dsun.net.httpserver.maxRspTime=30 -jar target/botify-1.0-SNAPSHOT.jar
fi