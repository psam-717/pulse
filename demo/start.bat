@echo off
setlocal
cd /d "%~dp0"

rem ------------------------------------------------------------------
rem Pulse Backend launcher — requires JDK 25 (Spring Boot 4).
rem   start.bat            -> clean build + run on :8080
rem   start.bat compile    -> compile only (fast verify, skips tests)
rem   start.bat test       -> run the test suite
rem Auto-detects JDK 25 in the standard local locations before falling
rem back to JAVA_HOME. Set JAVA_HOME yourself if none of these match.
rem ------------------------------------------------------------------

rem ---- 1. Locate JDK 25 ----
set "JAVA_HOME="
for /d %%D in (
  "%LOCALAPPDATA%\jdk25\jdk-25*"
  "C:\Program Files\Eclipse Adoptium\jdk-25*"
  "C:\Program Files\Java\jdk-25*"
  "%ProgramFiles%\Eclipse Adoptium\jdk-25*"
) do (
  if not defined JAVA_HOME (
    if exist "%%~fD\bin\java.exe" set "JAVA_HOME=%%~fD"
  )
)
if not defined JAVA_HOME (
  echo [ERROR] JDK 25 not found. Install Temurin 25 ^(e.g. under %%LOCALAPPDATA%%\jdk25^) or set JAVA_HOME before running this script.
  exit /b 1
)
echo Using JAVA_HOME=%JAVA_HOME%
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem ---- 2. Pick Maven: standalone install if present, else the wrapper ----
set "MVN=mvnw.cmd"
if exist "%LOCALAPPDATA%\maven\apache-maven-3.9.15\bin\mvn.cmd" (
  set "MVN=%LOCALAPPDATA%\maven\apache-maven-3.9.15\bin\mvn.cmd"
)

rem ---- 3. Goal dispatch ----
set "GOAL=spring-boot:run"
if /I "%~1"=="compile" set "GOAL=compile"
if /I "%~1"=="test"    set "GOAL=test"
if /I "%~1"=="package" set "GOAL=package"

if /I "%GOAL%"=="spring-boot:run" (
  echo Starting Pulse Backend with JDK 25 ^(clean build^)...
  call "%MVN%" clean spring-boot:run
) else if /I "%GOAL%"=="test" (
  echo Running Pulse Backend tests with JDK 25...
  call "%MVN%" test
) else (
  echo Building Pulse Backend with JDK 25 ^(goal: %GOAL%, tests skipped^)...
  call "%MVN%" -DskipTests %GOAL%
)
exit /b %ERRORLEVEL%
