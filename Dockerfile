FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system stockflow && useradd --system --gid stockflow stockflow
COPY --from=build /workspace/target/stockflow-0.1.0-SNAPSHOT.jar /app/stockflow.jar
USER stockflow
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/stockflow.jar"]
