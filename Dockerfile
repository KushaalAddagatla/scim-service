FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user — running as root in a container is unnecessary
# and violates the principle of least privilege. ECS also flags it in
# security findings if the task definition has no user override.
RUN addgroup -S scim && adduser -S scim -G scim

COPY target/*.jar app.jar

USER scim
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
