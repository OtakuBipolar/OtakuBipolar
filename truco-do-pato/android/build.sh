#!/usr/bin/env bash
# Gera o APK do Truco do Pato sem Android SDK nem Gradle.
# Precisa de: JDK 17+, curl, python3, unzip. Roda no Linux x86_64 (ou WSL) e no macOS.
#
# As ferramentas vêm do Maven Central, conferidas por SHA-256:
#   aapt2 + recursos do framework (dentro do apktool-lib), android-all (classes do
#   Android 14 pra compilar), dalvik-dx (gera o classes.dex) e apksig (assina o APK).
#
# Variáveis opcionais:
#   KEYSTORE       chave de assinatura (padrão: ~/.truco-do-pato/release.p12)
#   NEW_KEY=1      cria uma chave nova se KEYSTORE não existir (o APK novo NÃO instala por cima
#                  de um assinado com outra chave)
#   KEYSTORE_PASS  senha da chave, 6+ caracteres (padrão: trucodopato)
#   KEY_ALIAS      apelido da chave (padrão: truco)
#   OUT_APK        onde salvar o APK (padrão: ../apk/TrucoDoPato.apk)
#   TOOLS_DIR      cache das ferramentas baixadas (padrão: .tools/)
set -euo pipefail

VERSION_CODE=1
VERSION_NAME=1.0
MIN_SDK=24
# Não subir pra 36 sem migrar o botão voltar: no Android 16, apps com target 36 deixam de
# receber onBackPressed (precisa de OnBackInvokedCallback).
TARGET_SDK=35

APP_DIR="$(cd "$(dirname "$0")" && pwd)"
WEB_DIR="$(cd "$APP_DIR/.." && pwd)"
TOOLS="${TOOLS_DIR:-$APP_DIR/.tools}"
BUILD="$APP_DIR/build"
OUT_APK="${OUT_APK:-$WEB_DIR/apk/TrucoDoPato.apk}"
KEYSTORE="${KEYSTORE:-$HOME/.truco-do-pato/release.p12}"
KEY_ALIAS="${KEY_ALIAS:-truco}"
export KEYSTORE_PASS="${KEYSTORE_PASS:-trucodopato}"
MAVEN=https://repo1.maven.org/maven2

sha256() {
  if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1
}

# fetch <arquivo> <caminho no Maven Central> <sha256>
fetch() {
  local file="$TOOLS/$1"
  if [ -f "$file" ] && [ "$(sha256 "$file")" = "$3" ]; then return; fi
  mkdir -p "$TOOLS"
  echo "baixando $1"
  local delay=5
  for attempt in 1 2 3 4 5 6; do
    # O Maven Central às vezes responde 429 (muitas requisições); espera e tenta de novo.
    if curl -fsSL -o "$file.part" "$MAVEN/$2"; then break; fi
    if [ "$attempt" = 6 ]; then echo "não consegui baixar $1" >&2; exit 1; fi
    sleep "$delay"; delay=$((delay * 2))
  done
  if [ "$(sha256 "$file.part")" != "$3" ]; then
    rm -f "$file.part"
    echo "checksum errado em $1: o arquivo baixado não é o esperado" >&2
    exit 1
  fi
  mv "$file.part" "$file"
}

# O aapt2 que vem no apktool é só Linux x86_64 e macOS (universal); confere antes de baixar 150 MB.
case "$(uname -s)/$(uname -m)" in
  Linux/x86_64) AAPT2_IN_JAR=prebuilt/linux/aapt2 ;;
  Darwin/*) AAPT2_IN_JAR=prebuilt/macosx/aapt2 ;;
  *) echo "sistema não suportado ($(uname -s) $(uname -m)): o build roda no Linux x86_64, no WSL x86_64 ou no macOS" >&2
     exit 1 ;;
esac

if [ ! -f "$KEYSTORE" ] && [ "${NEW_KEY:-0}" != 1 ]; then
  cat >&2 <<MSG
Chave de assinatura não encontrada em $KEYSTORE.
- Pra atualizar o app que já está no celular, use a mesma chave do APK instalado:
    KEYSTORE=/caminho/release.p12 ./build.sh
- Pra criar uma chave nova (o APK novo só instala depois de desinstalar o antigo, e o placar salvo se perde):
    NEW_KEY=1 ./build.sh
MSG
  exit 1
fi
if [ "${#KEYSTORE_PASS}" -lt 6 ]; then
  echo "KEYSTORE_PASS precisa ter pelo menos 6 caracteres" >&2
  exit 1
fi

fetch apktool-lib-3.0.3.jar org/apktool/apktool-lib/3.0.3/apktool-lib-3.0.3.jar \
  983773879fd89ede2cd938858e3efce2a90ac1123f6a5140e9d949dcf4464e3e
fetch android-all-14.jar org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar \
  6be2218c6a53fe3c57bc22ebdc723edcb7270a8a6f187545708aa5c0ed813977
fetch dalvik-dx-16.0.1.jar com/jakewharton/android/repackaged/dalvik-dx/16.0.1/dalvik-dx-16.0.1.jar \
  1e4b645628e3bdb097b5331d669e177ef235a551582a8c646dbe36865e541907
fetch apksig-2.3.0.jar com/android/tools/build/apksig/2.3.0/apksig-2.3.0.jar \
  9637078c0016244e4be0941836295365a7e2e5b164c59cb7885783c40460bfee

AAPT2="$TOOLS/aapt2"
FRAMEWORK="$TOOLS/android-framework.jar"
if [ ! -x "$AAPT2" ] || [ "$TOOLS/apktool-lib-3.0.3.jar" -nt "$AAPT2" ]; then
  unzip -o -j -q "$TOOLS/apktool-lib-3.0.3.jar" "$AAPT2_IN_JAR" prebuilt/android-framework.jar -d "$TOOLS"
  chmod +x "$AAPT2"
  touch "$AAPT2" "$FRAMEWORK"
fi

echo "== app web -> assets/www"
rm -rf "$BUILD"
mkdir -p "$BUILD/assets" "$BUILD/gen" "$BUILD/classes" "$BUILD/packer"
python3 "$APP_DIR/tools/make_www.py" "$WEB_DIR/index.html" "$APP_DIR/fonts" "$BUILD/assets/www"

echo "== recursos e manifesto (aapt2)"
"$AAPT2" compile --dir "$APP_DIR/res" -o "$BUILD/res.zip"
"$AAPT2" link -o "$BUILD/base.apk" \
  -I "$FRAMEWORK" \
  --manifest "$APP_DIR/AndroidManifest.xml" \
  -A "$BUILD/assets" \
  --java "$BUILD/gen" \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  "$BUILD/res.zip"

echo "== código (javac + dx)"
# Entre aspas: o arquivo de argumentos do javac quebra em caminhos com espaço.
find "$APP_DIR/src" "$BUILD/gen" -name '*.java' | sed 's/.*/"&"/' > "$BUILD/sources.txt"
# java.* vem da API Java 8 do próprio JDK; android.* vem do android-all.
javac -encoding UTF-8 --release 8 -Xlint:-options -Xlint:deprecation \
  -cp "$TOOLS/android-all-14.jar" -d "$BUILD/classes" @"$BUILD/sources.txt"
java -cp "$TOOLS/dalvik-dx-16.0.1.jar" com.android.dx.command.Main \
  --dex --min-sdk-version="$MIN_SDK" --output="$BUILD/classes.dex" "$BUILD/classes"

echo "== empacotar e assinar"
if [ ! -f "$KEYSTORE" ]; then
  echo "criando chave de assinatura nova em $KEYSTORE (guarde: atualizações precisam da mesma chave)"
  mkdir -p "$(dirname "$KEYSTORE")"
  # umask 077: a chave privada só fica legível pro próprio usuário.
  (umask 077 && keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -storepass "$KEYSTORE_PASS" \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=Truco do Pato, O=OtakuBipolar")
fi
javac -encoding UTF-8 -cp "$TOOLS/apksig-2.3.0.jar" -d "$BUILD/packer" "$APP_DIR/tools/PackApk.java"
mkdir -p "$(dirname "$OUT_APK")"
# O apksig 2.3.0 carrega o assinador v1 (que usa classes internas do JDK) mesmo com o v1
# desligado; no JDK 17+ é preciso liberar esse acesso só pra classe carregar.
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
  --add-exports java.base/sun.security.x509=ALL-UNNAMED \
  --add-exports java.base/sun.security.pkcs=ALL-UNNAMED \
  --add-exports java.base/sun.security.util=ALL-UNNAMED \
  -cp "$BUILD/packer:$TOOLS/apksig-2.3.0.jar" PackApk \
  "$BUILD/base.apk" "$BUILD/classes.dex" "$KEYSTORE" "$KEY_ALIAS" "$OUT_APK" "$MIN_SDK"

echo "certificado: $(keytool -list -v -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASS" -alias "$KEY_ALIAS" 2>/dev/null | grep -m1 'SHA256:' | sed 's/^[[:space:]]*//')"
"$AAPT2" dump badging "$OUT_APK" | grep -E "^(package|minSdkVersion|targetSdkVersion|application-label|launchable-activity|uses-permission)"
echo "pronto: $OUT_APK"
