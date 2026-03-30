# ── Стадия сборки ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Копируем Maven wrapper и pom.xml отдельно — кэшируем зависимости
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Копируем исходники и собираем JAR
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# ── Финальный образ ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Создаём непривилегированного пользователя
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/target/ai-secretary-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# -Djava.net.useSystemProxies=false — отключаем системные прокси (важно для Catalina-среды)
# На Fly.io это не нужно, но не мешает
ENTRYPOINT ["java", \
  "-Djava.net.useSystemProxies=false", \
  "-DsocksProxyHost=", \
  "-Xmx256m", \
  "-jar", "app.jar"]