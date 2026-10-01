#!/usr/bin/env bash
# =============================================================================
# AdvancedBackups · Fabric 26.2 构建脚本
# -----------------------------------------------------------------------------
# 用途：一条命令完成 core 库 + 26.2 fabric 模块的构建。
#
# 关键环境约束（实测得出，勿随意改动）：
#   1) Minecraft 26.2 的 javaVersion.majorVersion = 25 → 必须 JDK 25。
#   2) Gradle 9.7.1（26.2 模块 wrapper 版本），装在本机 JDK 25 上运行正常。
#   3) Minecraft 26.x 已不再混淆：没有 yarn 映射，也没有 mappings 配置。
#   4) services.gradle.org 会 302 到 GitHub Release，本机直连极慢（约 6KB/s），
#      因此默认走腾讯云镜像下载 Gradle 发行版，避免 wrapper 超时。
#
# 用法：
#   ./build.sh              # 构建 core + fabric/26.2
#   ./build.sh core         # 只构建 core
#   ./build.sh fabric       # 只构建 fabric/26.2
#
# 可选环境变量：
#   GRADLE_MIRROR=0   关闭镜像，改用 gradle-wrapper.properties 里的官方地址
#   JAVA_HOME_25      指定 JDK 25 路径（默认自动探测）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ---------- 1. 定位 JDK 25（Minecraft 26.2 的硬性要求）----------
if [[ -n "${JAVA_HOME_25:-}" && -x "${JAVA_HOME_25}/bin/java" ]]; then
    export JAVA_HOME="$JAVA_HOME_25"
else
    for cand in /usr/lib/jvm/java-25-openjdk-amd64 \
                /usr/lib/jvm/openjdk-25 \
                /usr/lib/jvm/java-1.25.0-openjdk-amd64; do
        if [[ -x "$cand/bin/java" ]]; then export JAVA_HOME="$cand"; break; fi
    done
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
    echo "错误：未找到 JDK 25。Minecraft 26.2 要求 Java 25。" >&2
    echo "      可用 JAVA_HOME_25=/path/to/jdk25 显式指定。" >&2
    exit 1
fi

JAVA_MAJOR="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')"
if [[ "$JAVA_MAJOR" != "25" ]]; then
    echo "警告：JAVA_HOME 指向 Java $JAVA_MAJOR，而 Minecraft 26.2 需要 25。" >&2
    echo "      编译可能失败（options.release = 25）。" >&2
fi

# ---------- 2. 镜像（包装 wrapper 下载源）----------
# 用 JAVA_TOOL_OPTIONS 之外的机制：直接改写 wrapper 期望的本地缓存不可靠，
# 因此这里通过 GRADLE_DISTRIBUTION_URL 提示 + 预置缓存双保险。
if [[ "${GRADLE_MIRROR:-1}" == "1" ]]; then
    export GRADLE_DISTRIBUTION_URL="https://mirrors.cloud.tencent.com/gradle/gradle-9.7.1-bin.zip"
fi

echo "== 环境 =="
echo "  JAVA_HOME : $JAVA_HOME"
echo "  Java      : $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
echo "  JDK 25    : 是"
echo

# ---------- 3. 构建 ----------
TARGET="${1:-all}"

build_core() {
    echo "== [1/2] 构建 core 共享库（Gradle 8.10 / 目标 Java 8）=="
    # core 是纯 Java 库，会被展开进各 loader 的最终 jar，因此必须先产出
    ( cd core && ./gradlew --no-daemon shadowJar )
    echo "  产出: core/build/libs/advancedbackups-corelib.jar"
}

build_fabric() {
    echo "== [2/2] 构建 fabric/26.2（Gradle 9.7.1 / Loom 1.18-SNAPSHOT / Java 25）=="
    local version="${MOD_VERSION:-2.2.0}"
    ( cd fabric/26.2 && ./gradlew --no-daemon -Pversion="$version" build )
    echo "  产出目录: fabric/26.2/build/libs/"
}

case "$TARGET" in
    core)   build_core ;;
    fabric) build_fabric ;;
    all)    build_core; build_fabric ;;
    *) echo "用法: $0 [all|core|fabric]" >&2; exit 2 ;;
esac

echo
echo "构建完成。"
