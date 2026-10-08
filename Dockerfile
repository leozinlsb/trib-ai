# Imagem única para hospedagem de demonstração (ex.: Render): a API Spring Boot serve também o front React.
# O contexto do build é a raiz do repositório (precisa enxergar frontend/ e backend/).
# Banco H2 em arquivo dentro do container: zera a cada novo deploy/reinício; o seed recria as empresas fictícias.

# 1) front: gera frontend/dist
FROM node:22-alpine AS front
WORKDIR /front
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# 2) backend: o front compilado entra no jar (classpath:/static, servido por FrontendConfig)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=front /front/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# 3) execução
FROM eclipse-temurin:21-jre
# usuário sem privilégios: a API não precisa de root (o banco H2 fica em /app/data)
RUN useradd --system --uid 10001 --home-dir /app tribia && mkdir -p /app/data && chown -R tribia /app
WORKDIR /app
COPY --from=build --chown=tribia /app/target/*.jar app.jar
USER tribia
# Profile prod: sem console H2/Swagger, cookie Secure e senha do administrador obrigatória (TRIBIA_ADMIN_SENHA).
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
# Render informa a porta em PORT; localmente usa 8090.
EXPOSE 8090
CMD ["sh", "-c", "java -Dserver.port=${PORT:-8090} -jar app.jar"]
