<#
.SYNOPSIS
Starts the ReLink call-audio helper (uid 2000) over authorized wired USB ADB.
Never reads or passes secrets in process arguments; reads the one-time code from masked stdin.
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$Port,
    [string]$Serial = "",
    [string]$AdbPath = "adb"
)

$ErrorActionPreference = "Stop"
$adbArgs = @()
if ($Serial) { $adbArgs += @("-s", $Serial) }

$pkg = "app.relink.android"
$apk = (& $AdbPath @adbArgs shell "pm path $pkg" | Select-String '^package:' | Select-Object -First 1).ToString() -replace '^package:', ''
if (-not $apk) {
    $pkg = "app.tether.android"
    $apk = (& $AdbPath @adbArgs shell "pm path $pkg" | Select-String '^package:' | Select-Object -First 1).ToString() -replace '^package:', ''
}
if (-not $apk) { throw "ReLink package not found on connected device." }
$apk = $apk.Trim()

$secure = Read-Host -Prompt "Enter 32-char one-time code shown in ReLink (masked)" -AsSecureString
$bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $raw = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
} finally {
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}

$token = ($raw -replace '[\s-]', '').ToLowerInvariant()
$raw = $null
if ($token -notmatch '^[0-9a-f]{32}$') {
    throw "Invalid one-time code: expected 32 hex characters."
}

$logFile = "/data/local/tmp/$pkg-helper.log"
$cmd = "(umask 077 && _tf=`$(mktemp '/data/local/tmp/.$pkg.XXXXXX') && IFS= read -r _t && [ -n `"`$_t`" ] && printf '%s\n' `"`$_t`" > `"`$_tf`" || { rm -f `"`${_tf:-}`"; echo shell-init-failed:TOKEN_INPUT; exit 1; }; setsid sh -c `"CLASSPATH=$apk exec app_process / app.tether.helper.HelperMain serve-file $Port '`$_tf' --replace`" </dev/null >'$logFile' 2>&1 & sleep 1; if kill -0 `$! 2>/dev/null; then echo process-alive; else rm -f `"`$_tf`"; echo process-exited; grep -E '^shell-init-failed:[A-Z_]+' '$logFile' | tail -1; fi)"

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $AdbPath
$psi.Arguments = (($adbArgs + @("shell", $cmd)) | ForEach-Object { if ($_ -match '\s|"' ) { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }) -join ' '
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false

$proc = [System.Diagnostics.Process]::Start($psi)
$proc.StandardInput.WriteLine($token)
$proc.StandardInput.Close()
$stdout = $proc.StandardOutput.ReadToEnd()
$stderr = $proc.StandardError.ReadToEnd()
$proc.WaitForExit()
if ($stdout) { Write-Output $stdout.Trim() }
if ($stderr) { Write-Error $stderr.Trim() }
if ($proc.ExitCode -ne 0) { exit $proc.ExitCode }
