FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-noble
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    AQEP_OUTPUT_DIR=/app/artifacts \
    OLLAMA_HOST=http://ollama:11434
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates fonts-liberation libcups2t64 libasound2t64 libatk-bridge2.0-0t64 libatk1.0-0t64 \
    libatspi2.0-0t64 libcairo2 libdbus-1-3 libdrm2 libgbm1 libglib2.0-0t64 libnspr4 \
    libnss3 libpango-1.0-0 libx11-6 libx11-xcb1 libxcb1 libxcomposite1 libxdamage1 \
    libxext6 libxfixes3 libxkbcommon0 libxrandr2 libxrender1 libxshmfence1 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/platform-cli/target/platform-cli-1.0.0-SNAPSHOT.jar /app/aqep.jar
RUN java -cp /app/aqep.jar com.microsoft.playwright.CLI install chromium
ENTRYPOINT ["java", "-jar", "/app/aqep.jar"]
