@echo off
setlocal
set DIR=%~dp0
if "%DIR%"=="" set DIR=.
set DIR=%DIR:~0,-1%
set GRADLE_HOME=%DIR%\gradle\wrapper\..\..
set JAVA_HOME=%JAVA_HOME%
set PATH=%JAVA_HOME%\bin;%GRADLE_HOME%\bin;%PATH%
jav...