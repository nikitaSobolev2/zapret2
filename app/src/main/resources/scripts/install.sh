#!/bin/sh
# installs an already built zapret2 tree and starts it
# usage : install.sh SRC DST CONFIG
set -e

SRC="$1"
DST="$2"
CFG="$3"
PLIST_DIR=/Library/LaunchDaemons

[ -n "$SRC" ] && [ -n "$DST" ] && [ -n "$CFG" ] || {
	echo "usage: install.sh SRC DST CONFIG" >&2
	exit 2
}
[ -f "$SRC/init.d/macos/zapret2" ] || {
	echo "$SRC is not a zapret2 source tree" >&2
	exit 2
}
[ -f "$CFG" ] || {
	echo "config $CFG does not exist" >&2
	exit 2
}

INIT="$DST/init.d/macos/zapret2"

echo "* stopping previous instance"
[ -x "$INIT" ] && "$INIT" stop </dev/null || true

echo "* installing files to $DST"
mkdir -p "$DST"
# rsync keeps the relative symlinks of the built binaries. config is written separately
rsync -a --delete --exclude=/config --exclude=/tmp/ "$SRC/" "$DST/"
mkdir -p "$DST/tmp" "$DST/init.d/macos/custom.d"

echo "* writing config"
cp "$CFG" "$DST/config"

chown -R root:wheel "$DST"
chmod 755 "$DST" "$INIT"
find "$DST" -name "*.sh" -exec chmod 755 {} +

# binaries are normally symlinked by make, this covers a tree that came without them
[ -x "$DST/tpws/tpws" ] || "$DST/install_bin.sh"

echo "* registering launchd job"
# RunAtLoad in the plist is enough for boot, launchctl is avoided to skip session/domain quirks
ln -fs "$DST/init.d/macos/zapret2.plist" "$PLIST_DIR"

echo "* starting zapret2"
"$INIT" start
