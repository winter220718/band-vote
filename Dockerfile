FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src
COPY data ./data

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /opt/render/project/src

COPY --from=build /app/target/band-vote-app-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build /app/data ./data

EXPOSE 10000

CMD ["sh", "-c", "java -jar app.jar"]
