# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 内置阿里云 Maven 镜像，保证国内网络一键构建（海外网络同样可用）
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -q dependency:go-offline -DskipTests
COPY src ./src
RUN mvn -q package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/cold-chain.jar /app/cold-chain.jar
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/cold-chain.jar"]
