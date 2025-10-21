# -------- build stage --------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Leverage Docker layer caching
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# Copy sources and build
COPY src ./src
COPY src/main/resources ./src/main/resources
RUN mvn -q -DskipTests clean package

# -------- runtime stage --------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*-SNAPSHOT.jar /app/app.jar

# Expose HTTP port
EXPOSE 8181
ENTRYPOINT ["java","-jar","/app/app.jar"]
