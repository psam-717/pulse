@echo off
cd /d "%~dp0"
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot
echo Starting Pulse Backend with JDK 25...
.\mvnw spring-boot:run
pause