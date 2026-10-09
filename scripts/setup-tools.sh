#!/usr/bin/env bash
# Lädt JDK 21 (Temurin) und Maven 3.9.9 nach .tools/ (wird nicht eingecheckt).
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .tools
arch=$(uname -m)
case "$arch" in
  x86_64) jdk_arch=x64 ;;
  aarch64|arm64) jdk_arch=aarch64 ;;
  *) echo "Unbekannte Architektur: $arch" >&2; exit 1 ;;
esac
if [ ! -x .tools/jdk/bin/java ]; then
  rm -rf .tools/jdk && mkdir -p .tools/jdk
  curl -fsSL -o .tools/jdk.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/${jdk_arch}/jdk/hotspot/normal/eclipse"
  tar -xzf .tools/jdk.tar.gz -C .tools/jdk --strip-components=1
  rm .tools/jdk.tar.gz
fi
if [ ! -x .tools/maven/bin/mvn ]; then
  rm -rf .tools/maven && mkdir -p .tools/maven
  curl -fsSL -o .tools/maven.tgz \
    https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
  tar -xzf .tools/maven.tgz -C .tools/maven --strip-components=1
  rm .tools/maven.tgz
fi
echo "Fertig. Mit 'source scripts/env.sh' aktivieren."
