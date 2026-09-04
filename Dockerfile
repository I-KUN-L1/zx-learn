# 知行智学通用 Dockerfile
# 用法：docker build --build-arg APP_NAME=zx-auth -t zx-learn/zx-auth .
# 基础镜像用官方 Eclipse Temurin JRE（仅运行 jar，无需 JDK）
FROM eclipse-temurin:21-jre

ARG APP_NAME=zx-auth
ENV TZ=Asia/Shanghai

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

ADD target/${APP_NAME}.jar /app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dfile.encoding=UTF-8", "/app.jar"]
