$ErrorActionPreference = 'Stop'

$env:Path = "C:\Program Files\nodejs;$env:APPDATA\npm;$env:Path"
$firebase = Join-Path $env:APPDATA 'npm\firebase.cmd'
$npm = 'C:\Program Files\nodejs\npm.cmd'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not (Test-Path $firebase)) {
    throw "Firebase CLI niet gevonden. Installeer Node.js en Firebase CLI eerst."
}

Set-Location $projectRoot

Write-Host "Firebase project instellen: voegmaatje"
& $firebase use voegmaatje

Write-Host "Functions dependencies installeren"
Set-Location (Join-Path $projectRoot 'functions')
& $npm install

Set-Location $projectRoot
Write-Host "RESEND_API_KEY instellen. Plak de geheime key alleen in deze terminal."
& $firebase functions:secrets:set RESEND_API_KEY --project voegmaatje

Write-Host "Cloud Function deployen"
& $firebase deploy --only functions --project voegmaatje

Write-Host "Klaar. De username-e-mailfunctie is gedeployed."
