# One-time: creates YOUR release signing key, outside the project folder (never goes to Git).
# keytool asks for the password itself; nothing is saved by this script.
# KEEP THE .jks FILE AND ITS PASSWORD SAFE: without them you can never ship an update to installed copies.
$dir = 'E:\SpliWise-keys'
$key = Join-Path $dir 'spliwise-release.jks'
if (Test-Path $key) { Write-Host "Key already exists: $key (not overwriting)"; exit 0 }
New-Item -ItemType Directory -Force $dir | Out-Null
& 'E:\AndroidDev\jdk\jdk-17.0.21.0.2+1\bin\keytool.exe' -genkeypair -v -keystore $key -alias spliwise `
    -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=SpliWise, O=Personal, C=IN'
if (Test-Path $key) { Write-Host "`nCreated $key`nBack it up (e.g. to a pen drive) and remember the password." }
