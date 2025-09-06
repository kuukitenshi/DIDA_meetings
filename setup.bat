@echo off
setlocal enabledelayedexpansion

REM ==== Versions ====
set JAVA_RELEASE=22
set JAVA_VERSION=22.0.2
set MAVEN_VERSION=3.8.4
set PROTOC_VERSION=3.12.0

REM ==== Install dir ====
set INSTALL_DIR=%cd%\dev_env

if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
cd "%INSTALL_DIR%"

REM ==== Download Java ====
echo Downloading Java %JAVA_VERSION%...
if not exist "jdk-%JAVA_VERSION%" (
    powershell -Command "Invoke-WebRequest -Uri https://download.oracle.com/java/%JAVA_RELEASE%/archive/jdk-%JAVA_VERSION%_windows-x64_bin.zip -OutFile java.zip"
    powershell -Command "Expand-Archive -Path java.zip -DestinationPath ."
    del java.zip
)

REM ==== Download Maven ====
echo Downloading Maven %MAVEN_VERSION%...
if not exist "apache-maven-%MAVEN_VERSION%" (
    powershell -Command "Invoke-WebRequest -Uri https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip -OutFile maven.zip"
    powershell -Command "Expand-Archive -Path maven.zip -DestinationPath ."
    del maven.zip
)

REM ==== Download Protoc ====
echo Downloading Protoc %PROTOC_VERSION%...
if not exist "protoc-%PROTOC_VERSION%" (
    mkdir protoc-%PROTOC_VERSION%
    powershell -Command "Invoke-WebRequest -Uri https://github.com/protocolbuffers/protobuf/releases/download/v%PROTOC_VERSION%/protoc-%PROTOC_VERSION%-win64.zip -OutFile protoc.zip"
    powershell -Command "Expand-Archive -Path protoc.zip -DestinationPath protoc-%PROTOC_VERSION%"
    del protoc.zip
)

REM ==== Create env.bat ====
(
echo @echo off
echo set JAVA_HOME=%INSTALL_DIR%\jdk-%JAVA_VERSION%
echo set MAVEN_HOME=%INSTALL_DIR%\apache-maven-%MAVEN_VERSION%
echo set PROTOC_HOME=%INSTALL_DIR%\protoc-%PROTOC_VERSION%
echo set PATH=%%JAVA_HOME%%\bin;%%MAVEN_HOME%%\bin;%%PROTOC_HOME%%\bin;%%PATH%%
) > env.bat

echo.
echo ✅ Environment setup complete.
echo 👉 To use it, run:
echo     call "%INSTALL_DIR%\env.bat"
