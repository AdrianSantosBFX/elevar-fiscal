#!/usr/bin/env sh
# Executa o Facilita.sig (requer Java 8 ou superior)
DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$DIR/dist/facilita-sig.jar" ]; then JAR="$DIR/dist/facilita-sig.jar"
elif [ -f "$DIR/target/facilita-sig.jar" ]; then JAR="$DIR/target/facilita-sig.jar"
else echo "Nao encontrei o facilita-sig.jar em dist/ nem em target/."; exit 1; fi
exec java -jar "$JAR"
