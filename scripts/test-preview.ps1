param([string]$BaseUrl = 'http://localhost:8080', [string]$EnvFile = '.env', [switch]$ReuseExisting)
$ErrorActionPreference = 'Stop'
# Local acceptance only. Creates two clearly labelled synthetic demo resources.
# Credentials are read into memory and are never printed or written to output.
$settings = @{}
foreach ($line in Get-Content -LiteralPath $EnvFile) {
    if ($line -match '^([A-Z0-9_]+)=(.*)$') { $settings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'") }
}
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$login = @{ email = $settings['APP_ADMIN_EMAIL']; password = $settings['APP_ADMIN_PASSWORD'] } | ConvertTo-Json
if (-not $settings['APP_ADMIN_PASSWORD']) { throw 'Admin credentials are missing from the local env file.' }
$null = Invoke-RestMethod "$BaseUrl/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $login -WebSession $session
$null = Invoke-RestMethod "$BaseUrl/api/v1/users/me" -WebSession $session
$csrf = $session.Cookies.GetCookies([Uri]$BaseUrl) | Where-Object Name -eq 'XSRF-TOKEN' | Select-Object -First 1
if (-not $csrf) { throw 'No CSRF cookie returned after login.' }
$headers = @{ 'X-XSRF-TOKEN' = [Uri]::UnescapeDataString($csrf.Value) }
$topics = Invoke-RestMethod "$BaseUrl/api/v1/topics?size=100" -WebSession $session
$topic = $topics.result.content | Where-Object { $_.visibility -eq 'PUBLIC' } | Select-Object -First 1
if (-not $topic) { throw 'A public topic is required for guest preview acceptance.' }
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
foreach ($file in @('target/demo-preview.pdf', 'target/preview-demo.docx', 'target/preview-slides.pptx', 'target/preview-table.xlsx')) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Missing generated fixture: $file" }
    $extension = [IO.Path]::GetExtension($file).TrimStart('.')
    $expected = switch ($extension) { 'pptx' { 2 }; 'xlsx' { 1 }; default { 3 } }
    $form = @{ title = "[DEMO] Học qua trò chơi — bản $extension";
        description = 'Synthetic preview acceptance fixture. No real school or children data.';
        topicId = [string]$topic.id; file = Get-Item -LiteralPath $file }
    if ($ReuseExisting) {
        $query = [Uri]::EscapeDataString($form.title)
        $uploaded = (Invoke-RestMethod "$BaseUrl/api/v1/resources?keyword=$query&size=20").result.content |
            Where-Object { $_.title -eq $form.title -and $_.fileExtension -eq $extension } | Select-Object -First 1
        if (-not $uploaded) { throw "No existing labelled demo resource for $extension." }
    } else {
        $uploaded = (Invoke-RestMethod "$BaseUrl/api/v1/resources" -Method Post -Form $form -Headers $headers -WebSession $session).result
    }
    $id = $uploaded.id
    $before = (Invoke-RestMethod "$BaseUrl/api/v1/resources/$($uploaded.slug)").result.downloadCount
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    do {
        $preview = (Invoke-RestMethod "$BaseUrl/api/v1/resources/$id/preview").result
        if ($preview.status -ne 'PROCESSING') { break }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($preview.status -ne 'READY' -or $preview.pageCount -ne $expected) {
        throw "Preview for $extension did not become READY with $expected pages: $($preview.status)"
    }
    $page = [Math]::Min(2, $expected)
    $image = Invoke-WebRequest "$BaseUrl/api/v1/resources/$id/preview/pages/$page"
    if ($image.StatusCode -ne 200 -or $image.Headers['Content-Type'] -notlike 'image/jpeg*' -or $image.RawContentLength -lt 1000) { throw 'Page image invalid.' }
    if ($image.Headers['Cache-Control'] -notcontains 'no-store') { throw 'Preview must not be publicly cached.' }
    $download = Invoke-WebRequest "$BaseUrl/api/v1/resources/$id/file" -SkipHttpErrorCheck
    if ($download.StatusCode -ne 401) { throw 'Guest downloaded the original file.' }
    $after = (Invoke-RestMethod "$BaseUrl/api/v1/resources/$($uploaded.slug)").result.downloadCount
    if ($before -ne $after) { throw 'Preview changed the original download count.' }
    try {
        $null = Invoke-RestMethod "$BaseUrl/api/v1/resources/$id/visibility" -Method Patch -ContentType 'application/json' -Body '{"visibility":"PRIVATE"}' -Headers $headers -WebSession $session
        foreach ($route in @('preview', 'preview/pages/1')) {
            $denied = Invoke-WebRequest "$BaseUrl/api/v1/resources/$id/$route" -SkipHttpErrorCheck
            if ($denied.StatusCode -ne 404) { throw "Private $route leaked to guest." }
        }
        $allowed = Invoke-WebRequest "$BaseUrl/api/v1/resources/$id/preview/pages/1" -WebSession $session
        if ($allowed.StatusCode -ne 200) { throw 'Admin preview failed.' }
    } finally {
        $null = Invoke-RestMethod "$BaseUrl/api/v1/resources/$id/visibility" -Method Patch -ContentType 'application/json' -Body '{"visibility":"PUBLIC"}' -Headers $headers -WebSession $session
    }
    Write-Output "$extension PASS: pages=$expected, guest preview=200, original=401, private preview=404, download count unchanged"
    Write-Output "DEMO_URL=http://localhost:4201/resources/$($uploaded.slug)"
}
