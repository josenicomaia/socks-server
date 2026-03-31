# Stage 1: Build with Maven + JDK 26
FROM ubuntu:24.04 AS build
WORKDIR /app

# Install JDK 26 (Azul Zulu) + Maven
ARG MAVEN_VERSION=3.9.5
RUN apt-get update && apt-get install -y curl gnupg && \
    curl -fsSL https://repos.azul.com/azul-repo.key | gpg --dearmor -o /usr/share/keyrings/azul.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/azul.gpg] https://repos.azul.com/zulu/deb stable main" > /etc/apt/sources.list.d/zulu.list && \
    apt-get update && apt-get install -y zulu26-jdk && \
    curl -fsSL https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz | tar xz -C /opt && \
    ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/bin/mvn && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/zulu26

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:resolve -q || true

# Build
COPY src ./src
RUN mvn clean package -DskipTests -q

# Stage 2: Run with JDK 26
FROM ubuntu:24.04
RUN apt-get update && apt-get install -y curl gnupg && \
    curl -fsSL https://repos.azul.com/azul-repo.key | gpg --dearmor -o /usr/share/keyrings/azul.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/azul.gpg] https://repos.azul.com/zulu/deb stable main" > /etc/apt/sources.list.d/zulu.list && \
    apt-get update && apt-get install -y zulu26-jre-headless && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/zulu26

WORKDIR /app
COPY --from=build /app/target/server-*.jar server.jar
EXPOSE 5353
ENTRYPOINT ["java", "-jar", "server.jar"]
