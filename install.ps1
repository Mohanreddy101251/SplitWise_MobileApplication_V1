# Build + test, then install on the connected phone and open the app. Output also in build.log.
& $PSScriptRoot\build.ps1
if ((Get-Content $PSScriptRoot\build.log -Tail 1) -match 'EXIT CODE: 0') {
    & E:\AndroidDev\sdk\platform-tools\adb.exe install -r $PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk
    & E:\AndroidDev\sdk\platform-tools\adb.exe shell am start -n com.spliwise/.MainActivity
}
