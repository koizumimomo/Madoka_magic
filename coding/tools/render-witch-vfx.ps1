$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$atlas = [Drawing.Bitmap]::new(256, 64, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
$canvas = [Drawing.Graphics]::FromImage($atlas)

function Draw-Tile([int]$Slot, [scriptblock]$Draw) {
    $state = $canvas.Save()
    try {
        $canvas.TranslateTransform(($Slot % 8) * 32, [Math]::Floor($Slot / 8) * 32)
        & $Draw
    } finally {
        $canvas.Restore($state)
    }
}

function Shape([string]$Color, [string]$Coordinates) {
    $brush = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($Color))
    try {
        $points = foreach ($pair in $Coordinates.Split(' ')) {
            $x, $y = $pair.Split(',')
            [Drawing.Point]::new([int]$x, [int]$y)
        }
        $canvas.FillPolygon($brush, [Drawing.Point[]]$points)
    } finally {
        $brush.Dispose()
    }
}

function Oval([string]$Color, [float]$X, [float]$Y, [float]$W, [float]$H) {
    $brush = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($Color))
    try { $canvas.FillEllipse($brush, $X, $Y, $W, $H) }
    finally { $brush.Dispose() }
}

function Butterfly([bool]$Folded) {
    if ($Folded) {
        Shape '#1b1623' '15,12 9,4 5,3 5,12 8,20 11,22 9,28 13,29 16,23 19,29 23,28 21,22 24,20 27,12 27,3 23,4 17,12'
        Shape '#e0ad3d' '15,13 9,7 7,6 7,12 10,19 13,20 11,27 13,27 16,21 19,27 21,27 19,20 22,19 25,12 25,6 23,7 17,13'
        Shape '#38262a' '15,15 10,10 10,15 13,19 16,21 19,19 22,15 22,10 17,15'
    } else {
        Shape '#1b1623' '15,12 11,6 2,3 1,9 3,16 9,20 5,24 7,28 12,28 16,23 20,28 25,28 27,24 23,20 29,16 31,9 30,3 21,6 17,12'
        Shape '#f6c54d' '15,13 10,8 3,5 3,9 5,15 11,19 7,24 8,26 12,26 16,21 20,26 24,26 25,24 21,19 27,15 29,9 29,5 22,8 17,13'
        Shape '#30232b' '15,15 9,10 5,8 7,14 12,17 10,23 12,23 16,20 20,23 22,23 20,17 25,14 27,8 23,10 17,15'
    }
    Shape '#ffe095' '15,10 17,10 17,22 15,22'
}

try {
    Draw-Tile 0 {
        $star = [Drawing.Drawing2D.GraphicsPath]::new()
        $ink = [Drawing.SolidBrush]::new([Drawing.Color]::White)
        try {
            $canvas.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $star.AddBezier(16, 1, 16.8, 10.3, 17.6, 13.8, 27, 15.5)
            $star.AddBezier(27, 15.5, 18.8, 16.7, 17, 20.6, 16, 31)
            $star.AddBezier(16, 31, 15, 20.6, 13.2, 16.7, 5, 15.5)
            $star.AddBezier(5, 15.5, 14.4, 13.8, 15.2, 10.3, 16, 1)
            $star.CloseFigure()
            $canvas.FillPath($ink, $star)
        } finally {
            $ink.Dispose()
            $star.Dispose()
        }
    }
    Draw-Tile 1 { Butterfly $false }
    Draw-Tile 2 { Butterfly $true }
    Draw-Tile 3 {
        Shape '#747474' '16,2 29,9 29,23 16,30 3,23 3,9'
        Shape '#ffffff' '16,4 27,10 27,21 16,27 5,21 5,10'
        Shape '#737373' '16,8 24,12 24,19 16,23 8,19 8,12'
        Shape '#bfbfbf' '16,10 21,13 21,18 16,21 11,18 11,13'
    }
    Draw-Tile 4 {
        Shape '#6f6f6f' '16,2 19,8 25,17 26,22 23,27 16,30 9,27 6,22 7,17 13,8'
        Shape '#bfbfbf' '16,5 22,15 24,21 22,25 16,27 10,25 8,21 10,16'
        Shape '#ffffff' '15,11 13,17 11,19 11,22 14,23 16,20 16,16'
        Shape '#ebebeb' '20,23 23,21 22,25 19,26'
    }
    Draw-Tile 5 {
        Shape '#727272' '1,22 6,21 11,14 9,7 15,12 21,11 26,3 25,11 30,12 27,17 21,17 17,24 15,29 13,23 8,28 3,27'
        Shape '#ffffff' '3,24 7,23 13,16 12,11 16,15 22,13 26,7 24,13 28,13 26,15 20,15 16,22 15,26 14,21 7,26'
    }
    Draw-Tile 6 {
        Shape '#8e8e8e' '4,25 6,17 12,9 21,5 27,7 29,13 26,19 18,25 9,28'
        Shape '#ffffff' '6,24 9,16 14,11 22,7 26,9 27,13 24,17 16,22'
        Shape '#c9c9c9' '6,24 17,19 25,13 23,17 15,23 8,26'
    }
    Draw-Tile 7 {
        Shape '#767676' '12,2 20,2 21,7 24,8 28,7 31,14 27,17 26,20 28,24 22,29 18,26 14,26 10,29 4,24 6,20 5,17 1,14 4,7 8,8 11,7'
        Oval '#dedede' 7 6 18 18
        Oval '#929292' 10 9 12 12
        Shape '#ffffff' '15,9 17,9 17,15 21,18 19,20 15,17'
    }
    Draw-Tile 8 {
        Shape '#797979' '1,7 8,4 16,11 24,4 31,7 29,20 23,18 19,15 25,26 20,25 18,30 15,18 10,29 8,23 3,25 13,16 7,21 2,20'
        Shape '#ffffff' '3,8 8,6 15,12 16,16 10,17 4,19 6,14 4,12'
        Shape '#ededed' '29,8 24,6 17,12 16,16 22,16 27,18 26,14 28,12'
        Shape '#c7c7c7' '14,16 18,16 20,23 22,25 19,24 18,27 16,19 10,26 9,23 7,23'
    }
    Draw-Tile 9 {
        Shape '#8b8b8b' '12,5 27,1 28,5 27,22 21,26 17,23 17,20 23,16 23,8 16,10 16,25 10,30 5,28 5,24 12,20'
        Shape '#ffffff' '13,6 26,3 26,21 22,24 18,23 18,21 24,17 24,6 15,9 15,24 10,28 6,27 6,25 13,21'
    }
    Draw-Tile 10 {
        Shape '#818181' '3,29 8,19 15,15 18,8 29,2 25,14 20,17 17,23 10,25'
        Shape '#ffffff' '6,26 11,19 17,16 21,8 27,4 23,13 19,15 16,21'
        Shape '#bcbcbc' '6,16 10,12 12,12 11,15 7,18'
    }
    Draw-Tile 11 {
        Shape '#797979' '3,10 17,7 22,4 28,7 30,16 28,24 22,28 17,25 3,22'
        Shape '#cfcfcf' '5,12 17,9 16,15 16,19 18,23 5,21'
        Oval '#ffffff' 18 7 9 17
        Oval '#b8b8b8' 20 10 5 11
    }
    Draw-Tile 12 {
        Shape '#ffffff' '7,7 12,4 14,9 9,11'
        Shape '#c4c4c4' '17,12 26,9 24,17 20,20'
        Shape '#999999' '8,21 14,18 16,24 12,28'
    }
    Draw-Tile 13 {
        Shape '#ffffff' '3,11 11,8 19,9 29,4 30,7 20,12 11,11 3,14'
        Shape '#d0d0d0' '2,17 11,14 20,15 30,10 30,13 20,18 11,17 2,20'
        Shape '#a1a1a1' '2,23 11,20 20,21 30,16 30,19 20,24 11,23 2,26'
    }
    Draw-Tile 14 {
        Shape '#666666' '16,1 23,13 23,19 16,31 9,19 9,13'
        Shape '#dbdbdb' '16,3 21,14 20,19 16,28 11,18 11,14'
        Shape '#ffffff' '16,3 16,16 11,18 11,14'
        Shape '#aeaeae' '16,16 20,15 19,20 16,27'
    }
    Draw-Tile 15 {
        Shape '#6f6f6f' '8,5 24,5 30,11 30,21 24,27 8,27 2,21 2,11'
        Shape '#b7b7b7' '9,7 23,7 28,12 28,20 23,25 9,25 4,20 4,12'
        Shape '#ffffff' '8,9 13,8 22,8 25,11 24,14 21,11 11,11 7,15 5,13'
        $mode = $canvas.CompositingMode
        try {
            $canvas.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
            Shape 'Transparent' '10,13 22,13 24,15 24,18 22,20 10,20 8,18 8,15'
        } finally {
            $canvas.CompositingMode = $mode
        }
    }
    $assetDirectory = Join-Path $PSScriptRoot '../src/main/resources/assets/madoka_magic/textures/particle'
    $atlas.Save((Join-Path $assetDirectory 'ritual_visuals.png'), [Drawing.Imaging.ImageFormat]::Png)
} finally {
    $canvas.Dispose()
    $atlas.Dispose()
}
