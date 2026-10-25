#!/usr/bin/env sh
# Executa o Elevar Fiscal (requer Java 8 ou superior)
DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$DIR/dist/elevar-fiscal.jar" ]; then JAR="$DIR/dist/elevar-fiscal.jar"
elif [ -f "$DIR/target/elevar-fiscal.jar" ]; then JAR="$DIR/target/elevar-fiscal.jar"
else echo "Nao encontrei o elevar-fiscal.jar em dist/ nem em target/."; exit 1; fi
exec java -jar "$JAR"
