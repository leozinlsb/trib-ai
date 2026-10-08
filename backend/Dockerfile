# Imagem do backend para hospedagem de demonstração (ex.: Render).
# Banco H2 em arquivo dentro do container: zera a cada novo deploy/reinício; o seed recria as empresas fictícias.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
# usuário sem privilégios: a API não precisa de root (o banco H2 fica em /app/data)
RUN useradd --system --uid 10001 --home-dir /app tribia && mkdir -p /app/data && chown -R tribia /app
WORKDIR /app
COPY --from=build --chown=tribia /app/target/*.jar app.jar
USER tribia
# Render informa a porta em PORT; localmente usa 8090.
# Profile prod: sem console H2/Swagger, cookie Secure e senha do administrador obrigatória (TRIBIA_ADMIN_SENHA).
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8090
CMD ["sh", "-c", "java -Dserver.port=${PORT:-8090} -jar app.jar"]
