# source scripts/env.sh
# Nutzt die lokal geladenen Werkzeuge (.tools/), falls vorhanden; sonst gelten JDK und Maven der Umgebung (zum Beispiel in der CI).
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -d "$ROOT/.tools/jdk" ]; then
  export JAVA_HOME="$ROOT/.tools/jdk"
  export PATH="$JAVA_HOME/bin:$ROOT/.tools/maven/bin:$PATH"
fi
