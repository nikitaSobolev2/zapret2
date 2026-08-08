#!/bin/sh
# Build universal utunws into DEST (default: ../app/build/appResources/common/engine/bin/utunws).
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
NFQ="$ROOT/nfq"
DEST="${1:-$ROOT/../app/build/appResources/common/engine/bin/utunws}"
SDKROOT="${SDKROOT:-$(/usr/bin/xcrun --sdk macosx --show-sdk-path)}"
CC="${CC:-$(/usr/bin/xcrun -f clang)}"
export SDKROOT CC

/usr/bin/make -C "$NFQ" clean mac
/bin/mkdir -p "$(dirname "$DEST")"
/bin/cp "$NFQ/utunws" "$DEST"
/bin/chmod 755 "$DEST"
echo "built $DEST"
