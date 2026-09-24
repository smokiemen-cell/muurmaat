$ErrorActionPreference = 'Stop'

$env:Path = "C:\Program Files\nodejs;$env:APPDATA\npm;$env:Path"
$firebase = Join-Path $env:APPDATA 'npm\firebase.cmd'
$npm = 'C:\Program Files\nodejs\npm.cmd'
$projectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

function Invoke-Checked($FilePath, [string[]]$Arguments) {
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Commando mislukt: $FilePath $($Arguments -join ' ')"
    }
}

if (-not (Test-Path $firebase)) {
    throw "Firebase CLI niet gevonden. Installeer Node.js en Firebase CLI eerst."
}

Set-Location $projectRoot
Invoke-Checked $firebase @('use', 'voegmaatje')

Set-Location (Join-Path $projectRoot 'functions')
Invoke-Checked $npm @('install')

Set-Location $projectRoot
Write-Host "Plak SERPAPI_KEY alleen in deze terminal."
Invoke-Checked $firebase @('functions:secrets:set', 'SERPAPI_KEY', '--project', 'voegmaatje')

Invoke-Checked $firebase @('deploy', '--only', 'functions', '--project', 'voegmaatje')
Write-Host "Klaar: Firebase Functions zijn gedeployed."
