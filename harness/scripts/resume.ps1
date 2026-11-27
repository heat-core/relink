param(
    [string]$Agent = "agent-a",
    [switch]$AcquireLease,
    [switch]$ForceStale
)
$ErrorActionPreference = "Stop"
$ctl = Join-Path $PSScriptRoot "harness_ctl.py"
$argsList = @($ctl, "resume", "--agent", $Agent)
if ($AcquireLease) { $argsList += "--acquire-lease" }
if ($ForceStale) { $argsList += "--force-stale" }
& python @argsList
exit $LASTEXITCODE
