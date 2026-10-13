@echo off
rem Usage: logflow data\access-small.log --min-status 400 --path-prefix /api --fields timestamp,clientIp,status
java -jar "%~dp0target\logflow.jar" %*
