#!/bin/sh
set -eu

LABEL=org.zapret.macos.engine
PLIST=/Library/LaunchDaemons/${LABEL}.plist
if [ ! -f "$PLIST" ]; then exit 0; fi
/bin/launchctl enable system/"$LABEL"
if ! /bin/launchctl print system/"$LABEL" >/dev/null 2>&1; then
    /bin/launchctl bootstrap system "$PLIST"
fi
/bin/launchctl kickstart -k system/"$LABEL"
