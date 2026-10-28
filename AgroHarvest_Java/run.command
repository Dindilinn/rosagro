#!/bin/bash
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1; then
    echo "Java 17+ не найдена. Установите JDK 17 или 21 и повторите запуск."
    read -r -p "Нажмите Enter для закрытия..."
    exit 1
fi
java -jar AgroHarvest.jar
