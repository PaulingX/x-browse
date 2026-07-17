# 单容器构建：Spring Boot 内嵌前端静态资源
FROM maven:3.9-eclipse-temurin-17 AS backend-builder

# 配置阿里云 Maven 镜像
RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?> \
    <settings xmlns="http://maven.apache.org/SETTINGS/1.2.0" \
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
              xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd"> \
      <mirrors> \
        <mirror> \
          <id>aliyun</id> \
          <mirrorOf>central</mirrorOf> \
          <name>Aliyun Maven</name> \
          <url>https://maven.aliyun.com/repository/public</url> \
        </mirror> \
      </mirrors> \
    </settings>' > /root/.m2/settings.xml

WORKDIR /backend
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY backend/src ./src
RUN mvn package -DskipTests -B

FROM node:18-alpine AS frontend-builder
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm config set registry https://registry.npmmirror.com && npm ci
COPY frontend/ .
RUN npm run build

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN mkdir -p /app/data /app/data/cache

COPY --from=backend-builder /backend/target/*.jar app.jar
COPY --from=frontend-builder /frontend/dist /app/static

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
