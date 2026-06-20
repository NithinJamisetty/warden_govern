# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/swms-1.0.jar swms.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "swms.jar"]
