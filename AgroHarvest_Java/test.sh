#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
./build.sh
java -ea -cp out/classes agro.RewardEngineTest
java -Djava.awt.headless=true -jar AgroHarvest.jar --screenshots screenshots
