# Imagem do backend para hospedagem de demonstração (ex.: Render).
# Banco H2 em arquivo dentro do container: zera a cada novo deploy/reinício; o seed recria as empresas fictícias.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Render informa a porta em PORT; localmente usa 8090.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8090
CMD ["sh", "-c", "java -Dserver.port=${PORT:-8090} -jar app.jar"]
