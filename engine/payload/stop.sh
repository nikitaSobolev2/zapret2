#!/bin/sh

LABEL=org.zapret.macos.engine
ANCHOR=com.apple/zapret
TOKEN_FILE=/var/run/zapret.pf-token
/bin/launchctl disable system/"$LABEL" >/dev/null 2>&1 || true
/bin/launchctl bootout system/"$LABEL" >/dev/null 2>&1 || true
/sbin/pfctl -a "$ANCHOR" -F all >/dev/null 2>&1 || true
/usr/bin/pkill -9 -x utunws >/dev/null 2>&1 || true
if [ -s "$TOKEN_FILE" ]; then
    TOKEN=$(/bin/cat "$TOKEN_FILE" 2>/dev/null || true)
    if [ -n "$TOKEN" ]; then /sbin/pfctl -X "$TOKEN" >/dev/null 2>&1 || true; fi
    /bin/rm -f "$TOKEN_FILE"
fi
