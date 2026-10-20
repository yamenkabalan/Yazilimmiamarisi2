@echo off
rem Usage: logflow --config configs\error-report.yaml
java -jar "%~dp0target\logflow.jar" %*
