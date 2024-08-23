# Stage 1: Build
FROM maven:3.9.8-eclipse-temurin-21 AS buildstage
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: For image creation
FROM openjdk:17
WORKDIR /app
COPY --from=buildstage /app/target/*.jar ./app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]