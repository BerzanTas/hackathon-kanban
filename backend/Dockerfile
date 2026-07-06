# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy the Maven wrapper and project descriptor first so dependency resolution
# is cached in its own layer and only re-runs when pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# The wrapper script may have CRLF line endings when checked out on Windows;
# normalize to LF and make it executable so it runs under /bin/sh.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

# Now copy sources and build the executable jar (tests run in CI, not the image build).
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Copy the Spring Boot executable jar. The maven-plugin repackaged jar is the only
# plain *.jar in target/ (the non-executable original is *.jar.original).
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
