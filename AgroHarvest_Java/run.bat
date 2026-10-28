@echo off
cd /d "%~dp0"
java -jar AgroHarvest.jar
if errorlevel 1 pause
