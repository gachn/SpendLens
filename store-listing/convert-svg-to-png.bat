@echo off
REM SVG to PNG conversion script for Play Store graphics

echo Converting SVG files to PNG...
echo.

REM Check if ImageMagick is installed
where magick >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Using ImageMagick...
    magick convert -background none -density 300 -resize 512x512 app-icon.svg app-icon.png
    magick convert -background none -density 300 -resize 1024x500 feature-graphic.svg feature-graphic.png
    echo.
    echo Conversion complete!
    echo Generated files:
    echo   - app-icon.png (512x512)
    echo   - feature-graphic.png (1024x500)
) else (
    echo ImageMagick not found.
    echo.
    echo Install ImageMagick from: https://imagemagick.org/script/download.php#windows
    echo.
    echo Alternative conversion methods:
    echo 1. Online converters: https://cloudconvert.com/svg-to-png
    echo 2. Inkscape: File → Export PNG Image
    echo 3. Adobe Illustrator: File → Export → Export As → PNG
    echo 4. GIMP: Open SVG → File → Export As → PNG
)

pause