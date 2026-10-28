#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out/classes
find src -name '*.java' -print > out/sources.txt
javac --release 17 -encoding UTF-8 -d out/classes @out/sources.txt
printf 'Main-Class: agro.AgroHarvest\n' > out/MANIFEST.MF
jar cfm AgroHarvest.jar out/MANIFEST.MF -C out/classes .
echo 'Created AgroHarvest.jar'
