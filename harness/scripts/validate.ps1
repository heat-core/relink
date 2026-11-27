param(
    [string]$SourceRoot = "",
    [switch]$SkipHashes
)
$ErrorActionPreference = "Stop"
$ctl = Join-Path $PSScriptRoot "harness_ctl.py"
$argsList = @($ctl, "validate")
if ($SourceRoot) { $argsList += @("--source-root", $SourceRoot) }
if ($SkipHashes) { $argsList += "--skip-hashes" }
& python @argsList
$ctlExit = $LASTEXITCODE

# Also verify PowerShell AST syntax of all harness and repo .ps1 scripts without executing them.
$psScripts = Get-ChildItem -Path $PSScriptRoot -Filter "*.ps1" | Select-Object -ExpandProperty FullName
$repoPs1 = (Join-Path $PSScriptRoot "..\..\tools\start-helper.ps1")
if (Test-Path -LiteralPath $repoPs1) { $psScripts += $repoPs1 }
$syntaxErrors = @()
foreach ($file in $psScripts) {
    $tokens = $null
    $errs = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($file, [ref]$tokens, [ref]$errs)
    if ($errs -and $errs.Count -gt 0) {
        $syntaxErrors += "$file : $($errs[0].Message)"
    }
}
if ($syntaxErrors.Count -gt 0) {
    Write-Error ($syntaxErrors -join "`n")
    exit 1
}
Write-Output "PowerShell AST syntax OK ($($psScripts.Count) scripts checked)."
exit $ctlExit
