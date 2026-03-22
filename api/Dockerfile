# =============================================================================
# Dockerfile - NestSpend API (Spring Boot)
# =============================================================================
# Construction multi-étapes :
#   1. Étape "build"  : compilation Maven avec JDK 17
#   2. Étape "runtime": image minimale JRE 17 pour l'exécution
# =============================================================================

# ---------------------------------------------------------------------------
# Étape 1 : Compilation
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /workspace

# Copie du wrapper Maven et du pom.xml en premier pour profiter du cache Docker
# (les dépendances ne sont retéléchargées que si pom.xml change)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Téléchargement des dépendances (mise en cache Docker)
RUN ./mvnw dependency:go-offline -B -q

# Copie du code source
COPY src src

# Construction du jar sans les tests (les tests tournent en CI)
RUN ./mvnw package -DskipTests -B -q

# Extraction des couches Spring Boot pour optimiser le cache Docker
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ---------------------------------------------------------------------------
# Étape 2 : Image d'exécution minimale
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# Métadonnées
LABEL maintainer="NestSpend Team"
LABEL description="NestSpend API - Gestion de dépenses personnelles"

# Création d'un utilisateur non-root pour la sécurité
RUN addgroup -S nestspend && adduser -S nestspend -G nestspend
USER nestspend

WORKDIR /app

# Copie des couches Spring Boot dans l'ordre pour optimiser le cache
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

# Port exposé
EXPOSE 8080

# Point d'entrée - utilisation du launcher Spring Boot optimisé
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
