$ErrorActionPreference = 'Stop'

$env:Path = "C:\Program Files\nodejs;$env:APPDATA\npm;$env:Path"
$firebase = Join-Path $env:APPDATA 'npm\firebase.cmd'
$npm = 'C:\Program Files\nodejs\npm.cmd'
$projectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

if (-not (Test-Path $firebase)) {
    throw "Firebase CLI niet gevonden. Installeer Node.js en Firebase CLI eerst."
}

Set-Location $projectRoot
& $firebase use voegmaatje

Set-Location (Join-Path $projectRoot 'functions')
& $npm install

Set-Location $projectRoot
Write-Host "Plak RESEND_API_KEY alleen in deze terminal."
& $firebase functions:secrets:set RESEND_API_KEY --project voegmaatje

& $firebase deploy --only functions --project voegmaatje
Write-Host "Klaar: Firebase Functions zijn gedeployed."
