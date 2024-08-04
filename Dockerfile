FROM openjdk:17-jdk-bullseye as builder
WORKDIR /app
COPY gradle /app/gradle
COPY src /app/src
COPY gradlew build.gradle settings.gradle /app
RUN ./gradlew build

FROM openjdk:17-alpine  as dist
COPY --from=builder /app/build/libs/aiode-1.0-SNAPSHOT.jar /app.jar
COPY versions.xml /
CMD java -jar /app.jar
