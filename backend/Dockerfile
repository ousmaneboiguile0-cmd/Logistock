# Étape 1 : compilation avec Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Télécharge les dépendances dans une couche à part (reconstructions plus rapides)
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -DskipTests package

# Étape 2 : image d'exécution légère
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/logistock-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
