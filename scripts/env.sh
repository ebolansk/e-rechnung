# source scripts/env.sh
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="$ROOT/.tools/jdk"
export PATH="$JAVA_HOME/bin:$ROOT/.tools/maven/bin:$PATH"
