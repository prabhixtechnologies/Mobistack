Add-Type -AssemblyName System.Drawing

function New-Mark([string]$Path, [int]$Size, [bool]$Round) {
  $bmp = New-Object System.Drawing.Bitmap $Size, $Size
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.Clear([System.Drawing.Color]::FromArgb(255, 20, 19, 15))
  if ($Round) {
    $g.Clear([System.Drawing.Color]::Transparent)
    $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 20, 19, 15))
    $g.FillEllipse($brush, 8, 8, $Size - 16, $Size - 16)
    $brush.Dispose()
  }
  $gold = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 201, 132, 29))
  $fontSize = [Math]::Floor($Size * 0.46)
  $font = New-Object System.Drawing.Font "Segoe UI", $fontSize, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
  $format = New-Object System.Drawing.StringFormat
  $format.Alignment = [System.Drawing.StringAlignment]::Center
  $format.LineAlignment = [System.Drawing.StringAlignment]::Center
  $g.DrawString("F", $font, $gold, (New-Object System.Drawing.RectangleF 0, 0, $Size, $Size), $format)
  $dir = Split-Path $Path
  if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
  $bmp.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
  $font.Dispose(); $gold.Dispose(); $g.Dispose(); $bmp.Dispose()
}

$root = Split-Path $PSScriptRoot -Parent
New-Mark (Join-Path $root "assets\icon.png") 1024 $false
New-Mark (Join-Path $root "assets\adaptive-icon.png") 1024 $true
New-Mark (Join-Path $root "assets\splash-icon.png") 1024 $false
Copy-Item (Join-Path $root "assets\icon.png") (Join-Path $root "assets\splash.png") -Force
Write-Output "Wrote FixFlow icons"
