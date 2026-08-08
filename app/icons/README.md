# App icons

- `Zapret.icns` — Dock / `.app` / DMG (wired in `build.gradle.kts`)
- `icon-1024.png` — master raster from `src/main/resources/zapret-logo.svg`

Regenerate after editing the SVG:

```bash
TMP=$(mktemp -d)
cp src/main/resources/zapret-logo.svg "$TMP/"
qlmanage -t -s 1024 -o "$TMP" "$TMP/zapret-logo.svg"
cp "$TMP/zapret-logo.svg.png" icons/icon-1024.png
sips -z 256 256 icons/icon-1024.png --out src/main/resources/zapret-icon.png

mkdir -p icons/Zapret.iconset
for spec in \
  "16:icon_16x16.png" "32:icon_16x16@2x.png" "32:icon_32x32.png" "64:icon_32x32@2x.png" \
  "128:icon_128x128.png" "256:icon_128x128@2x.png" "256:icon_256x256.png" \
  "512:icon_256x256@2x.png" "512:icon_512x512.png" "1024:icon_512x512@2x.png"
do
  size=${spec%%:*}; name=${spec##*:}
  sips -z "$size" "$size" icons/icon-1024.png --out "icons/Zapret.iconset/$name" >/dev/null
done
iconutil -c icns icons/Zapret.iconset -o icons/Zapret.icns
rm -rf icons/Zapret.iconset
```

Menu-bar tray icon stays the Compose-drawn ring in `TrayApp.kt` (unchanged).
