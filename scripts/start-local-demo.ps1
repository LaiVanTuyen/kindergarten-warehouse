$ErrorActionPreference='Stop'
$backend=Split-Path $PSScriptRoot -Parent
$frontend=Join-Path (Split-Path $backend -Parent) 'kindergarten-warehouse-angular'
Push-Location $backend
$previousSecure=$env:COOKIE_SECURE
$previousSameSite=$env:COOKIE_SAME_SITE
$previousOrigins=$env:CORS_ALLOWED_ORIGINS
try {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker chưa sẵn sàng. Cập nhật WSL bằng quyền quản trị rồi mở Docker Desktop.' }
    if (-not (Test-Path (Join-Path $frontend 'dist/apps/portal/browser/index.html')) -or -not (Test-Path (Join-Path $frontend 'dist/apps/admin/browser/index.html'))) {
        Push-Location $frontend
        try { $env:NX_DAEMON='false'; npx nx run-many -t build -p portal admin -c demo; if($LASTEXITCODE -ne 0){throw 'Build FE thất bại'} } finally { Pop-Location }
    }
    $env:COOKIE_SECURE='false'
    $env:COOKIE_SAME_SITE='Lax'
    $env:CORS_ALLOWED_ORIGINS='http://localhost:4300,http://localhost:4301,http://127.0.0.1:4300,http://127.0.0.1:4301'
    docker compose -p warehouse_demo --env-file .env.demo -f docker-compose.yml up -d --build
    if ($LASTEXITCODE -ne 0) {throw 'Khởi động backend demo thất bại'}
    $ready=$false
    for($i=0;$i -lt 150;$i++) {
        # Actuator requires authentication; use a public database-backed API instead.
        try {$health=Invoke-RestMethod 'http://127.0.0.1:8081/api/v1/age-groups' -TimeoutSec 3; if($null -ne $health.result){$ready=$true;break}}catch{}
        Start-Sleep -Seconds 2
    }
    if(-not $ready){throw 'Backend chưa healthy. Kiểm tra docker compose logs app.'}
    $listeners=Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object {$_.LocalPort -in 4300,4301}
    if(-not $listeners){
        Start-Process -FilePath (Get-Command node).Source -ArgumentList 'scratch/demo-server.cjs' -WorkingDirectory $frontend -WindowStyle Hidden -RedirectStandardOutput (Join-Path $frontend 'scratch/demo-server.log') -RedirectStandardError (Join-Path $frontend 'scratch/demo-server-error.log')
    }elseif(($listeners.LocalPort | Sort-Object -Unique).Count -ne 2){throw 'Chỉ một cổng frontend đang mở. Kiểm tra tiến trình chiếm cổng 4300/4301.'}
    Write-Host 'Portal: http://localhost:4301 ; Admin: http://localhost:4300'
    Write-Host 'Tài khoản admin đọc từ .env.demo. Không dùng số liệu dashboard để nghiệm thu thống kê.'
} finally {
    $env:COOKIE_SECURE=$previousSecure
    $env:COOKIE_SAME_SITE=$previousSameSite
    $env:CORS_ALLOWED_ORIGINS=$previousOrigins
    Pop-Location
}
