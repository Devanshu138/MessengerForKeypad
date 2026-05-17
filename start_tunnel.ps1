# ============================================================
#  KeypadMessenger - One-Click Launcher
#  Starts the Node.js backend + internet tunnel together
# ============================================================

$BackendDir = Join-Path $PSScriptRoot "backend"

# ── 1. Kill any old instances ────────────────────────────────
Write-Host ""
Write-Host "  Stopping any old servers/tunnels..." -ForegroundColor DarkGray
Get-Process -Name "node" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Get-Process -Name "ssh"  -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 500

# ── 2. Start Node.js backend in the background ──────────────
Write-Host "  Starting Node.js backend on port 3000..." -ForegroundColor Cyan
$nodeJob = Start-Job -ScriptBlock {
    param($dir)
    Set-Location $dir
    & node --env-file=.env server.js 2>&1
} -ArgumentList $BackendDir

# Wait a moment to let the server boot up
Start-Sleep -Seconds 2

# Check if it started OK
$output = Receive-Job $nodeJob
if ($output -match "listening") {
    Write-Host "  Backend is running!" -ForegroundColor Green
} else {
    Write-Host "  Backend started (waiting for connection)..." -ForegroundColor Yellow
}

# ── 3. Start the SSH tunnel and capture the URL ─────────────
Write-Host "  Opening internet tunnel..." -ForegroundColor Cyan
Write-Host ""

# Run tunnel in foreground so the script stays alive
# We parse stdout line by line to extract and highlight the URL
$pinfo = New-Object System.Diagnostics.ProcessStartInfo
$pinfo.FileName = "ssh"
$pinfo.Arguments = "-o StrictHostKeyChecking=no -R 80:localhost:3000 nokey@localhost.run"
$pinfo.RedirectStandardOutput = $true
$pinfo.RedirectStandardError  = $true
$pinfo.UseShellExecute = $false

$p = New-Object System.Diagnostics.Process
$p.StartInfo = $pinfo
$p.Start() | Out-Null

$urlFound = $false

try {
    while (-not $p.StandardOutput.EndOfStream) {
        $line = $p.StandardOutput.ReadLine()

        # Highlight the tunnel URL line
        if ($line -match "\.lhr\.life") {
            $null = $line -match "(https?://[^\s,]+\.lhr\.life)"
            $url = $Matches[1]
            Write-Host ""
            Write-Host "  =============================================" -ForegroundColor DarkCyan
            Write-Host "   YOUR TUNNEL URL:" -ForegroundColor White
            Write-Host ""
            Write-Host "   $url" -ForegroundColor Yellow
            Write-Host ""
            Write-Host "   Use http:// version on your phone:" -ForegroundColor Gray
            $httpUrl = $url -replace "^https://","http://"
            Write-Host "   $httpUrl" -ForegroundColor Green
            Write-Host "  =============================================" -ForegroundColor DarkCyan
            Write-Host ""
            Write-Host "  Tunnel is LIVE. Keep this window open!" -ForegroundColor Green
            Write-Host "  Press Ctrl+C to stop everything." -ForegroundColor DarkGray
            Write-Host ""
            $urlFound = $true
        } elseif (-not $urlFound) {
            # Show other output in gray until we get the URL
            Write-Host "  $line" -ForegroundColor DarkGray
        }
    }
} finally {
    # Cleanup: stop the Node.js background job when tunnel closes
    Write-Host ""
    Write-Host "  Shutting down backend server..." -ForegroundColor DarkGray
    Stop-Job  $nodeJob -ErrorAction SilentlyContinue
    Remove-Job $nodeJob -ErrorAction SilentlyContinue
    Get-Process -Name "node" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Write-Host "  All services stopped. Goodbye!" -ForegroundColor DarkGray
}
