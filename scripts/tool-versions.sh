# source scripts/tool-versions.sh
# Festgelegte Werkzeugversionen mit Prüfsummen für setup-tools.sh und build-dist.sh. Ein Update ist eine bewusste Änderung dieser
# Datei: neues Release unter https://adoptium.net/ wählen und die SHA-256-Summen aus der Adoptium-Release-Übersicht (oder der API
# https://api.adoptium.net/v3/assets/latest/21/hotspot) eintragen; bei Maven die .sha512-Datei von archive.apache.org.
JDK_RELEASE="jdk-21.0.12.1+1"
JDK_URL_BASE="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1"
JDK_WIN_X64_FILE="OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip"
JDK_WIN_X64_SHA256="f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e"
JDK_LINUX_X64_FILE="OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz"
JDK_LINUX_X64_SHA256="ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94"
JDK_LINUX_AARCH64_FILE="OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.12.1_1.tar.gz"
JDK_LINUX_AARCH64_SHA256="23e37e026f12f3e706f18938ff611db3032d075b09d0879a25d06718c773e223"
MAVEN_VERSION="3.9.9"
MAVEN_SHA512="a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b"

# Lädt url nach file und bricht ab, wenn die Prüfsumme nicht stimmt (Algorithmus: sha256 oder sha512).
download_verified() {
  local url="$1" file="$2" algo="$3" sum="$4"
  curl -fsSL -o "$file" "$url"
  if ! echo "$sum  $file" | "${algo}sum" -c - >/dev/null 2>&1; then
    rm -f "$file"
    echo "Prüfsumme ($algo) von $url stimmt nicht: Datei verworfen." >&2
    return 1
  fi
}
