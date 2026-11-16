param([string]$PreviewPath)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assets = Join-Path $PSScriptRoot '../src/main/resources/assets/madoka_magic'
$textures = Join-Path $assets 'textures/item'
$models = Join-Path $assets 'models/item'
$overlays = Join-Path $textures 'witch_rune'
$seals = Join-Path $textures 'contract_seal'
New-Item -ItemType Directory -Force -Path $overlays, $seals | Out-Null

# Coordinates are relative to the center of each rune.
$runes = [ordered]@{
    sacrifice = @{ Color = '#ffe47a'; Paths = @('-0.7,0 -2,-3.4 -5,-4 -4,-0.8 -2.4,0 -4,2.3 -2.2,4 0,1.1', '0.7,0 2,-3.4 5,-4 4,-0.8 2.4,0 4,2.3 2.2,4 0,1.1', '0,-2.3 0,3.9') }
    take = @{ Color = '#ffb365'; Paths = @('0,-5 4.3,-2.5 4.3,2.5 0,5 -4.3,2.5 -4.3,-2.5 0,-5', '-2,-1 0,0.7 2,-1', '0,0.7 0,3.1') }
    atonement = @{ Color = '#b3d1ff'; Paths = @('0,-4.8 2.1,-1.7 4.8,-0.3 2,1 0,4.6 -2,1 -4.8,-0.3 -2.1,-1.7 0,-4.8', '0,-3 0,2.8', '-2.5,-0.2 2.5,-0.2') }
    kriemhild_gretchen = @{ Color = '#ff9fc5'; Paths = @('-4.8,2 -3.1,-1.5 0,-4.8 3.1,-1.5 4.8,2', '-3,3.3 0,0.5 3,3.3', '0,-1.4 0,4.7') }
    homulilly = @{ Color = '#d4b3ff'; Paths = @('-3.6,-4.8 3.6,-4.8 2.6,-2.4 0,0 -2.6,2.4 -3.6,4.8 3.6,4.8 2.6,2.4 0,0 -2.6,-2.4 -3.6,-4.8', '0,-5.5 0,-4.2', '-2.2,3 2.2,3') }
    candeloro = @{ Color = '#ffe7a1'; Paths = @('0,0 -4.7,-2.9 -4.2,1.6 0,0 4.7,-2.9 4.2,1.6 0,0', '0,0 -2.1,4.2 -0.7,3.4', '0,0 2.1,4.2 0.7,3.4', '-1,-2.9 0,-4.6 1,-2.9') }
    oktavia = @{ Color = '#91e2fd'; Paths = @('-4.8,-3.2 -2.2,-3 0,-0.7 2.2,-3 4.8,-3.2 3,-0.7 0,0.2 -3,-0.7 -4.8,-3.2', '0,0.2 -0.8,2.4 0.5,4.5', '-4.2,3.6 -2.7,3.1 -1.4,3.6', '1.8,3.6 3,3.1 4.2,3.6') }
    ophelia = @{ Color = '#ffad9f'; Paths = @('0,-5.3 -1.6,-2.7 0,-1.7 1.6,-2.7 0,-5.3', '0,-1.7 0,4.9', '0,-0.8 4.2,-0.8 3,1.7 0,1.7', '-2.3,3.6 0,4.5 2.3,3.6') }
    walpurgisnacht = @{ Color = '#f3b4e6'; Paths = @('0,-5.2 1.8,-3.8 4.4,-3.6 4.1,-1.1 5,1.2 2.9,2.7 1.7,4.8 0,4 -1.7,4.8 -2.9,2.7 -5,1.2 -4.1,-1.1 -4.4,-3.6 -1.8,-3.8 0,-5.2', '-2.7,1.6 0,-2.8 2.7,1.6 -2.7,1.6', '0,-1 0,3') }
}

$contractThemes = @('sacrifice', 'take', 'atonement')
$wax = @{ sacrifice = '#654c2b'; take = '#984c28'; atonement = '#334b70' }

function Write-Rune($Graphics, $Rune, [float]$X, [float]$Y, [float]$Scale) {
    $edge = [Drawing.Pen]::new([Drawing.Color]::FromArgb(225, 24, 17, 30), 2.6 * $Scale)
    $face = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml($Rune.Color), 1.15 * $Scale)
    $edge.LineJoin = $face.LineJoin = [Drawing.Drawing2D.LineJoin]::Round
    try {
        $paths = foreach ($path in $Rune.Paths) {
            $points = foreach ($pair in $path.Split(' ')) {
                $u, $v = $pair.Split(',')
                [Drawing.PointF]::new(
                    $X + $Scale * [float]::Parse($u, [CultureInfo]::InvariantCulture),
                    $Y + $Scale * [float]::Parse($v, [CultureInfo]::InvariantCulture))
            }
            ,([Drawing.PointF[]]$points)
        }
        foreach ($path in $paths) { $Graphics.DrawLines($edge, $path) }
        foreach ($path in $paths) { $Graphics.DrawLines($face, $path) }
    } finally {
        $edge.Dispose()
        $face.Dispose()
    }
}

function New-Overlay([string]$Theme, [bool]$IsContract) {
    $bitmap = [Drawing.Bitmap]::new(64, 64, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    try {
        if ($IsContract) {
            $shadow = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(235, 52, 34, 33))
            $body = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($wax[$Theme]))
            $rim = [Drawing.Pen]::new([Drawing.ColorTranslator]::FromHtml($runes[$Theme].Color), 0.7)
            try {
                $graphics.FillEllipse($shadow, 26.5, 29.5, 16, 16)
                $graphics.FillEllipse($body, 27.2, 29.7, 14, 14)
                $graphics.DrawEllipse($rim, 28.7, 31.2, 11, 11)
                Write-Rune $graphics $runes[$Theme] 34.2 36.7 0.77
            } finally { $shadow.Dispose(); $body.Dispose(); $rim.Dispose() }
        } else {
            Write-Rune $graphics $runes[$Theme] 38.3 30.4 1.08
        }
    } finally { $graphics.Dispose() }
    return $bitmap
}

function Write-Models([string]$Base, [string]$Overlay, [string]$Predicate, [string[]]$Themes) {
    $overrides = for ($index = 0; $index -lt $Themes.Count; $index++) {
        $theme = $Themes[$index]
        $model = [ordered]@{
            parent = 'minecraft:item/generated'
            textures = [ordered]@{ layer0 = "madoka_magic:item/$Base"; layer1 = "madoka_magic:item/$Overlay/$theme" }
        }
        $model | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $models "${Base}_$theme.json") -Encoding utf8NoBOM
        [ordered]@{ predicate = @{ "madoka_magic:$Predicate" = $index + 1 }; model = "madoka_magic:item/${Base}_$theme" }
    }
    $basePath = Join-Path $models "$Base.json"
    $baseModel = Get-Content -LiteralPath $basePath -Raw | ConvertFrom-Json -AsHashtable
    $baseModel.overrides = @($overrides)
    $baseModel | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $basePath -Encoding utf8NoBOM
}

$jobs = @(
    $contractThemes | ForEach-Object { @{ Theme = $_; Contract = $true; Root = $seals } }
    $runes.Keys | ForEach-Object { @{ Theme = $_; Contract = $false; Root = $overlays } }
)
foreach ($job in $jobs) {
    $bitmap = New-Overlay $job.Theme $job.Contract
    try { $bitmap.Save((Join-Path $job.Root "$($job.Theme).png"), [Drawing.Imaging.ImageFormat]::Png) }
    finally { $bitmap.Dispose() }
}
Write-Models 'contract' 'contract_seal' 'contract_seal' $contractThemes
Write-Models 'grief_seed' 'witch_rune' 'witch_rune' @($runes.Keys)

if ($PreviewPath) {
    $sheet = [Drawing.Bitmap]::new(720, 620)
    $graphics = [Drawing.Graphics]::FromImage($sheet)
    $font = [Drawing.Font]::new('Segoe UI', 11)
    $paper = [Drawing.Bitmap]::new((Join-Path $textures 'contract.png'))
    $griefSeed = [Drawing.Bitmap]::new((Join-Path $textures 'grief_seed.png'))
    try {
        $graphics.Clear([Drawing.Color]::FromArgb(39, 35, 45))
        $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
        for ($i = 0; $i -lt $jobs.Count; $i++) {
            $job = $jobs[$i]
            $x = ($i % 4) * 180
            $y = [Math]::Floor($i / 4) * 206
            $base = if ($job.Contract) { $paper } else { $griefSeed }
            $layer = [Drawing.Bitmap]::new((Join-Path $job.Root "$($job.Theme).png"))
            try {
                $graphics.DrawImage($base, $x + 10, $y, 160, 160)
                $graphics.DrawImage($layer, $x + 10, $y, 160, 160)
            } finally { $layer.Dispose() }
            $kind = if ($job.Contract) { 'Contract' } else { 'Grief Seed' }
            $graphics.DrawString($kind, $font, [Drawing.Brushes]::LightGray, $x + 12, $y + 159)
            $graphics.DrawString(($job.Theme -replace '_', ' '), $font, [Drawing.Brushes]::White, $x + 12, $y + 179)
        }
        $sheet.Save($ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($PreviewPath),
            [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $griefSeed.Dispose()
        $paper.Dispose()
        $font.Dispose()
        $graphics.Dispose()
        $sheet.Dispose()
    }
}
