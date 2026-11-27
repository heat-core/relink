param(
    [string]$Agent = "agent-a",
    [string]$Note = "Engineering milestone checkpoint",
    [switch]$WriteHandoff
)
$ErrorActionPreference = "Stop"
$ctl = Join-Path $PSScriptRoot "harness_ctl.py"
$argsList = @($ctl, "checkpoint", "--agent", $Agent, "--note", $Note)
if ($WriteHandoff) { $argsList += "--write-handoff" }
& python @argsList
exit $LASTEXITCODE
