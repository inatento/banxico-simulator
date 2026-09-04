# Build multi-etapa: no depende de tener Maven/JDK instalados en el host, solo Docker.
# (Los Dockerfile de judeca/minos/radamanto asumen `mvn clean install` ya corrido en el host antes
# de `docker build` -- aquí se deja todo dentro del build para que `docker build .` sea el único
# paso, más portable para levantar el simulador en una máquina/CI limpia.)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN adduser -D -h /app simulador
COPY --from=build /build/target/banxico-simulator.jar app.jar

# Puerto SPEI, puerto ARA, puerto de la API de control HTTP (ver control/ControlServer.java).
EXPOSE 6001 6002 8089

# data/ (identidad propia + H2) y config/ (simulator.properties + minos-public-cert.pem) se
# esperan como volúmenes montados -- ver docker-compose.yml. Sin montarlos el simulador funciona
# igual (genera su propia identidad efímera dentro del contenedor) pero se pierde al recrearlo.
VOLUME ["/app/data", "/app/config"]

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
	CMD wget -q -O - http://localhost:8089/health | grep -q '"ok"' || exit 1

USER simulador
ENTRYPOINT ["java", "-jar", "app.jar"]
