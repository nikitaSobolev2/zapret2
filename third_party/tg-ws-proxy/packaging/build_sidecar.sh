#!/bin/sh
# Build headless tg-ws-proxy into DEST (default: ../../app/build/appResources/common/tg-ws-proxy).
set -e
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
DEST="${1:-$ROOT/../../app/build/appResources/common/tg-ws-proxy}"
VENV="${TG_WS_PROXY_VENV:-$ROOT/.venv}"
PY="${VENV}/bin/python"

mkdir -p "$DEST"
if [ ! -x "$PY" ]; then
	python3 -m venv "$VENV"
	"$PY" -m pip install -U pip
	"$PY" -m pip install -e "$ROOT" pyinstaller
fi

WORK="$(mktemp -d "${TMPDIR:-/tmp}/tg-ws-proxy-build.XXXXXX")"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

cd "$ROOT"
"$PY" -m PyInstaller \
	--noconfirm \
	--clean \
	--distpath "$WORK/dist" \
	--workpath "$WORK/build" \
	"$ROOT/packaging/headless.spec"

rm -rf "$DEST"
mkdir -p "$(dirname "$DEST")"
cp -R "$WORK/dist/tg-ws-proxy" "$DEST"
find "$DEST" \( -name 'tg-ws-proxy' -o -name 'Python' -o -name '*.so' -o -name '*.dylib' \) \
	-exec chmod +x {} +
echo "built $DEST/tg-ws-proxy"
