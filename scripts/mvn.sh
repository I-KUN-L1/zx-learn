#!/usr/bin/env bash
# 本项目 Maven 调用包装器（Windows / Git Bash 沙箱环境专用）
#
# 为什么需要它：
#   1. Maven 不在 PATH（安装在 D:\1\apache-maven-3.9.6）；
#   2. 直接调 `mvn.cmd` 会**静默空跑**（exit=0 且无任何输出），极易误判为“构建通过”；
#   3. 用 plexus-classworlds launcher 调用时，`-classpath` 必须用**正斜杠**路径：
#      写成 `D:\\1\\...\\plexus-classworlds-2.7.0.jar`（反斜杠）会报
#      `ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher`。
#
# 用法：  bash scripts/mvn.sh -pl zx-course -am -DskipTests package
set -euo pipefail

MAVEN_HOME="${MAVEN_HOME:-D:/1/apache-maven-3.9.6}"
export JAVA_HOME="${JAVA_HOME:-C:/Users/20670/.jdks/temurin-21}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

MSYS_NO_PATHCONV=1 java \
  -Dclassworlds.conf="${MAVEN_HOME}/bin/m2.conf" \
  -Dmaven.home="${MAVEN_HOME}" \
  -Dmaven.multiModuleProjectDirectory="${REPO_ROOT}" \
  -classpath "${MAVEN_HOME}/boot/plexus-classworlds-2.7.0.jar" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
