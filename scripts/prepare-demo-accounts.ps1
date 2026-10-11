$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$settings=@{}
foreach($line in Get-Content (Join-Path $root '.env.demo')) {
    if($line -match '^([A-Z0-9_]+)=(.*)$'){$settings[$matches[1]]=$matches[2].Trim().Trim('"').Trim("'")}
}
$base='http://localhost:4301'
$session=New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null=Invoke-RestMethod "$base/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body (@{email=$settings.APP_ADMIN_EMAIL;password=$settings.APP_ADMIN_PASSWORD}|ConvertTo-Json) -WebSession $session
$null=Invoke-RestMethod "$base/api/v1/users/me" -WebSession $session
$csrf=$session.Cookies.GetCookies([Uri]$base)|Where-Object Name -eq 'XSRF-TOKEN'|Select-Object -First 1
$headers=@{'X-XSRF-TOKEN'=[Uri]::UnescapeDataString($csrf.Value)}
$users=(Invoke-RestMethod "$base/api/v1/users?keyword=demo_admin&size=100" -WebSession $session).result.content
if(-not ($users|Where-Object username -eq 'demo_admin')){
    $null=Invoke-RestMethod "$base/api/v1/users" -Method Post -ContentType 'application/json' -Body (@{username='demo_admin';email='demo.admin@example.com';password='<redacted>';fullName='Quản trị Demo';roles=@('ADMIN')}|ConvertTo-Json) -Headers $headers -WebSession $session
}
$demoSession=New-Object Microsoft.PowerShell.Commands.WebRequestSession
$null=Invoke-RestMethod "$base/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body (@{email='demo.admin@example.com';password='<redacted>'}|ConvertTo-Json) -WebSession $demoSession
$null=Invoke-RestMethod "$base/api/v1/users?size=1" -WebSession $demoSession
Write-Output 'Demo admin login and ADMIN permissions verified.'
# Soft-delete only duplicate synthetic resources created by our acceptance retries.
$report=Get-Content -Raw (Join-Path $root 'target/demo-acceptance.json')|ConvertFrom-Json
$resources=(Invoke-RestMethod "$base/api/v1/admin/resources?size=100" -WebSession $session).result.content
foreach($title in @('[DEMO] Học qua trò chơi — giáo viên gửi duyệt','[DEMO] Tài liệu cần bổ sung — mẫu từ chối')){
    $matching=@($resources|Where-Object title -eq $title|Sort-Object createdAt -Descending)
    foreach($item in @($matching|Select-Object -Skip 1)){
        if($item.id -ne $report.resource.id){$null=Invoke-RestMethod "$base/api/v1/resources/$($item.id)" -Method Delete -WebSession $session -Headers $headers}
    }
}
Write-Output 'Duplicate acceptance fixtures soft-deleted; final demo resources preserved.'
