heap_size=$(grep -w "botify.preferences.maxHeapSize" src/main/resources/application.properties|cut -d'=' -f2)
if [ -z "$heap_size" ]
then
  java -jar build/libs/botify-1.0-SNAPSHOT.jar
else
  java -Xmx"$heap_size" -jar build/libs/botify-1.0-SNAPSHOT.jar
fi