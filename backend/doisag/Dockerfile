# build em duas etapas: a primeira compila, a segunda so roda
# assim a imagem final n carrega o maven nem o codigo fonte

# ---- etapa 1: compila o jar ----
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /app

# copia so o pom primeiro pra cachear o download das dependencias.
# se o pom n muda, o docker reaproveita essa camada
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# os testes rodam no ci entao aqui so empacota
RUN mvn -B clean package -DskipTests

# ---- etapa 2: imagem final, so com o runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# roda como usuario sem privilegio, n como root
RUN addgroup -S doisag && adduser -S doisag -G doisag
USER doisag

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
