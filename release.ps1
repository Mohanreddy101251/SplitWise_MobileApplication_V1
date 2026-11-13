# Builds the shareable, signed release APK -> E:\SpliWise\dist\SpliWise-<version>.apk
# Asks for the key password in this terminal only; it is kept in memory for this build and then cleared.
$env:JAVA_HOME = 'E:\AndroidDev\jdk\jdk-17.0.21.0.2+1'
$env:ANDROID_HOME = 'E:\AndroidDev\sdk'
$key = 'E:\SpliWise-keys\spliwise-release.jks'
if (-not (Test-Path $key)) { Write-Host "No release key yet. Run make-key.ps1 first."; exit 1 }
Set-Location $PSScriptRoot

$keytool = 'E:\AndroidDev\jdk\jdk-17.0.21.0.2+1\bin\keytool.exe'
$bstr = [IntPtr]::Zero
# Check the password against the key first (passed via an environment variable, never on the command line)
for ($try = 1; $try -le 3; $try++) {
    $secure = Read-Host -AsSecureString 'Release key password'
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $env:SPLIWISE_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    & $keytool -list -keystore $key -alias spliwise -storepass:env SPLIWISE_STORE_PASSWORD *> $null
    if ($LASTEXITCODE -eq 0) { Write-Host 'Password OK - building...'; break }
    Write-Host "Wrong password ($try of 3). Letters are hidden while you type; try again."
    Remove-Item Env:SPLIWISE_STORE_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr); $bstr = [IntPtr]::Zero
}
if (-not $env:SPLIWISE_STORE_PASSWORD) { 'EXIT CODE: 1' | Tee-Object -FilePath release.log; exit 1 }
try {
    $env:SPLIWISE_KEYSTORE = $key
    & E:\AndroidDev\gradle-8.11.1\bin\gradle.bat testReleaseUnitTest assembleRelease --console=plain 2>&1 | Tee-Object -FilePath release.log
    $ok = $LASTEXITCODE -eq 0
} finally {
    if ($bstr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
    Remove-Item Env:SPLIWISE_STORE_PASSWORD, Env:SPLIWISE_KEYSTORE -ErrorAction SilentlyContinue
}
if ($ok) {
    $apk = Get-ChildItem app\build\outputs\apk\release\*.apk | Select-Object -First 1
    $ver = (Select-String -Path app\build.gradle.kts -Pattern 'versionName = "(.+)"').Matches[0].Groups[1].Value
    New-Item -ItemType Directory -Force dist | Out-Null
    $out = "dist\SpliWise-$ver.apk"
    Copy-Item $apk.FullName $out -Force
    "{0} · {1:N1} MB" -f (Resolve-Path $out), ((Get-Item $out).Length / 1MB) | Tee-Object -FilePath release.log -Append
    & 'E:\AndroidDev\sdk\build-tools\35.0.0\apksigner.bat' verify --print-certs $out | Select-Object -First 3 | Tee-Object -FilePath release.log -Append
}
"EXIT CODE: $(if ($ok) {0} else {1})" | Tee-Object -FilePath release.log -Append
