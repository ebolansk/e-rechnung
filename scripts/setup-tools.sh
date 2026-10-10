#!/usr/bin/env bash
# Lädt JDK 21 (Temurin) und Maven nach .tools/ (wird nicht eingecheckt). Versionen und Prüfsummen: scripts/tool-versions.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/tool-versions.sh
mkdir -p .tools
arch=$(uname -m)
case "$arch" in
  x86_64) jdk_file="$JDK_LINUX_X64_FILE"; jdk_sha="$JDK_LINUX_X64_SHA256" ;;
  aarch64|arm64) jdk_file="$JDK_LINUX_AARCH64_FILE"; jdk_sha="$JDK_LINUX_AARCH64_SHA256" ;;
  *) echo "Unbekannte Architektur: $arch" >&2; exit 1 ;;
esac
if [ ! -x .tools/jdk/bin/java ]; then
  rm -rf .tools/jdk && mkdir -p .tools/jdk
  download_verified "$JDK_URL_BASE/$jdk_file" .tools/jdk.tar.gz sha256 "$jdk_sha"
  tar -xzf .tools/jdk.tar.gz -C .tools/jdk --strip-components=1
  rm .tools/jdk.tar.gz
fi
if [ ! -x .tools/maven/bin/mvn ]; then
  rm -rf .tools/maven && mkdir -p .tools/maven
  download_verified "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    .tools/maven.tgz sha512 "$MAVEN_SHA512"
  tar -xzf .tools/maven.tgz -C .tools/maven --strip-components=1
  rm .tools/maven.tgz
fi
echo "Fertig. Mit 'source scripts/env.sh' aktivieren."
