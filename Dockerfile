# 知行智学通用 Dockerfile
# 用法（在仓库根目录执行，构建上下文为根目录）：
#   docker build --build-arg APP_NAME=zx-auth -t zx-learn/zx-auth .
#   （docker-compose.prod.yml 对 16 个服务均使用 context: . + args.APP_NAME=<模块名>）
#
# 前置：先在本机打包，保证 <模块>/target/<模块>.jar 存在
#   mvn -DskipTests package
#
# ⚠ ADD 的路径必须带模块目录：jar 产出在**各自模块目录**下（zx-auth/target/zx-auth.jar），
#   而不是仓库根的 target/ 下（根 pom 是聚合 pom，不存在 target/zx-auth.jar）。
# 基础镜像用官方 Eclipse Temurin JRE（仅运行 jar，无需 JDK）
FROM eclipse-temurin:21-jre

ARG APP_NAME=zx-auth
ENV TZ=Asia/Shanghai

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

ADD ${APP_NAME}/target/${APP_NAME}.jar /app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dfile.encoding=UTF-8", "/app.jar"]
