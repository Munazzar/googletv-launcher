# Store theme folders on the TV for the on-TV picker (Launcher settings > Theme), without applying any of them.
# Each lands in the launcher's files/themes/<folder name>/ through its ThemeProvider, as tools\theme.ps1 does.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools\theme-library.ps1 -TvHost 192.168.1.50 themes\glass ..\noor-theme
param(
    [string]$TvHost = $env:TV_HOST,
    [Parameter(ValueFromRemainingArguments = $true)][string[]]$Themes
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
if (-not $Themes) { [Console]::Error.WriteLine('usage: theme-library.ps1 -TvHost <ip> <theme folder> [...]'); exit 2 }
$env:TV_HOST = $TvHost
$Themes = $Themes | ForEach-Object { (Resolve-Path $_).Path }
Set-Location $PSScriptRoot
$py = 'venv\Scripts\python.exe'
$pkg = 'com.gathin.tvhome'
$dir = "/sdcard/Android/data/$pkg/files"
$uri = "content://$pkg.theme"
$tmp = '/data/local/tmp/cyt-lib'
function Invoke-Tv {
    $out = & $py -I @args
    if ($LASTEXITCODE -ne 0) { throw "$($args[0]) $($args[1]) failed (exit code $LASTEXITCODE)" }
    $out
}

foreach ($theme in $Themes) {
    $name = Split-Path $theme -Leaf
    if ($name -notmatch '^[A-Za-z0-9_-]{1,40}$') { throw "theme folder name '$name' must be letters, digits, - or _" }
    if (-not (Test-Path (Join-Path $theme 'config.json'))) { throw "no config.json in $theme" }
    $files = @(@('config.json', 'wallpaper.jpg', 'verses.json') | Where-Object { Test-Path (Join-Path $theme $_) })
    $walls = Join-Path $theme 'wallpapers'
    if (Test-Path $walls -PathType Container) {
        $files += Get-ChildItem $walls -File | Where-Object { $_.Name -match '^[A-Za-z0-9_.-]+\.(jpg|jpeg|png|webp)$' -or $_.Name -eq 'captions.txt' } |
            ForEach-Object { "wallpapers/$($_.Name)" }
    }
    Invoke-Tv tv.py "rm -rf $dir/themes/$name 2>/dev/null; true" | Out-Null
    foreach ($f in $files) {
        $stage = "$tmp-$($f -replace '/', '-')"
        Invoke-Tv b64push.py (Join-Path $theme $f) $stage | Out-Null
        # Provider first (the file then belongs to the launcher); cp for firmware without `content write`.
        Invoke-Tv tv.py "{ o=`$(content write --uri $uri/themes/$name/$f < $stage 2>&1) && case `"`$o`" in *'Unsupported operation'*|*rror*) false ;; esac; } || { mkdir -p `$(dirname $dir/themes/$name/$f) && cp $stage $dir/themes/$name/$f; }; rm -f $stage" | Out-Null
    }
    Write-Host "stored $name ($($files.Count) files)"
}
Write-Host 'Pick one on the TV: hold OK on Settings > Launcher settings > Theme.'
