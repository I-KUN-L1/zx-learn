#!/usr/bin/env bash
# 知行智学服务部署脚本
# 用法：./startup.sh -c <容器名> -n <项目名> -d <jar路径> -p <端口> -o "<JVM参数>" -a <调试端口>
set -e

CONTAINER_NAME=""
PROJECT_NAME=""
JAR_PATH=""
PORT="8080"
JVM_OPTS="-Xms256m -Xmx512m"
DEBUG_PORT=""

while getopts "c:n:d:p:o:a:h" opt; do
  case $opt in
    c) CONTAINER_NAME=$OPTARG ;;
    n) PROJECT_NAME=$OPTARG ;;
    d) JAR_PATH=$OPTARG ;;
    p) PORT=$OPTARG ;;
    o) JVM_OPTS=$OPTARG ;;
    a) DEBUG_PORT=$OPTARG ;;
    h) echo "用法: $0 -c 容器名 -n 项目名 -d jar路径 -p 端口 -o JVM参数 -a 调试端口"; exit 0 ;;
    *) echo "未知参数"; exit 1 ;;
  esac
done

if [ -z "$CONTAINER_NAME" ] || [ -z "$PROJECT_NAME" ] || [ -z "$JAR_PATH" ]; then
  echo "错误：-c -n -d 为必填参数"
  exit 1
fi

IMAGE_NAME="zx-learn/${PROJECT_NAME}"

# 构建镜像
docker build --build-arg APP_NAME="${PROJECT_NAME}" -t "${IMAGE_NAME}" .

# 移除旧容器
docker rm -f "${CONTAINER_NAME}" 2>/dev/null || true

# 启动容器
DEBUG_OPTS=""
if [ -n "$DEBUG_PORT" ]; then
  DEBUG_OPTS="-p ${DEBUG_PORT}:5005 -e JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
fi

docker run -d \
  --name "${CONTAINER_NAME}" \
  -p "${PORT}:8080" \
  -e JAVA_OPTS="${JVM_OPTS}" \
  --restart=always \
  ${DEBUG_OPTS} \
  "${IMAGE_NAME}"

echo "服务 ${PROJECT_NAME} 已启动，端口: ${PORT}"
