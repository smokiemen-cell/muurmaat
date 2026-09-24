@echo off
set "PATH=C:\Program Files\nodejs;%APPDATA%\npm;%PATH%"
"C:\Program Files\nodejs\node.exe" "%APPDATA%\npm\node_modules\firebase-tools\lib\bin\firebase.js" %*
