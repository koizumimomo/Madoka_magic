param([Parameter(Mandatory = $true)][string]$MinecraftClientJar)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $MinecraftClientJar).Path)
try {
    $stream = $jar.GetEntry('assets/minecraft/textures/entity/bee/bee_angry.png').Open()
    try {
        $uv = [Drawing.Bitmap]::new($stream)
        $bee = [Drawing.Bitmap]::new($uv.Width, $uv.Height)
        $eyes = [Drawing.Bitmap]::new($uv.Width, $uv.Height)
        try {
            for ($y = 0; $y -lt $uv.Height; $y++) {
                for ($x = 0; $x -lt $uv.Width; $x++) {
                    $pixel = $uv.GetPixel($x, $y)
                    if ($pixel.A -eq 0) { continue }
                    $value = (0.22 * $pixel.R + 0.64 * $pixel.G + 0.14 * $pixel.B) / 255
                    $redEye = $y -ge 13 -and $y -le 15 -and $pixel.R -gt 170 -and $pixel.G -lt 80
                    $color = if ($redEye) {
                        [Drawing.Color]::FromArgb($pixel.A, 255, [int](24 + 30 * $value), [int](36 + 32 * $value))
                    } elseif ($y -ge 18) {
                        [Drawing.Color]::FromArgb($pixel.A, [int](105 + 114 * $value),
                            [int](101 + 104 * $value), [int](126 + 107 * $value))
                    } else {
                        $shade = [int](6 + 46 * $value)
                        [Drawing.Color]::FromArgb($pixel.A, $shade, [int]($shade * 0.9), $shade + 9)
                    }
                    $bee.SetPixel($x, $y, $color)
                    if ($redEye) { $eyes.SetPixel($x, $y, $color) }
                }
            }
            $entityTextures = Join-Path $PSScriptRoot '../src/main/resources/assets/madoka_magic/textures/entity'
            $bee.Save((Join-Path $entityTextures 'black_bee.png'), [Drawing.Imaging.ImageFormat]::Png)
            $eyes.Save((Join-Path $entityTextures 'black_bee_eyes.png'), [Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $uv.Dispose()
            $bee.Dispose()
            $eyes.Dispose()
        }
    } finally {
        $stream.Dispose()
    }
} finally {
    $jar.Dispose()
}

$rune = [Drawing.Bitmap]::new(64, 64)
try {
    $rows = @(
        '...hhhh...',
        '..hwwwwh..',
        '.hwwwwwwh.',
        '.hw..w..h.',
        '.hw..w..h.',
        '..hwwwwh..',
        '...hw h...',
        '...hwhh...'
    )
    for ($y = 0; $y -lt $rows.Length; $y++) {
        for ($x = 0; $x -lt $rows[$y].Length; $x++) {
            $color = switch ($rows[$y][$x]) {
                'h' { [Drawing.ColorTranslator]::FromHtml('#ad89bb') }
                'w' { [Drawing.ColorTranslator]::FromHtml('#eee3e9') }
                default { [Drawing.Color]::Transparent }
            }
            $rune.SetPixel(33 + $x, 24 + $y, $color)
        }
    }
    $runePath = Join-Path $PSScriptRoot '../src/main/resources/assets/madoka_magic/textures/item/witch_rune/wither.png'
    $rune.Save($runePath, [Drawing.Imaging.ImageFormat]::Png)
} finally {
    $rune.Dispose()
}
