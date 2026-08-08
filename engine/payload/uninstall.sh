#!/bin/sh
set -eu

LABEL=org.zapret.macos.engine
DEST='/Library/Application Support/Zapret'
PLIST=/Library/LaunchDaemons/${LABEL}.plist
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
/bin/rm -f "$PLIST" /var/run/zapret.conf /var/run/zapret.route6
/bin/rm -rf "$DEST"

# Legacy tpws leftovers
OLD_INIT=/opt/zapret2/init.d/macos/zapret2
if [ -x "$OLD_INIT" ]; then
    "$OLD_INIT" stop >/dev/null 2>&1 || true
fi
/bin/launchctl bootout system/zapret2 >/dev/null 2>&1 || true
/bin/rm -f /Library/LaunchDaemons/zapret2.plist /etc/sudoers.d/zapret2
for a in zapret2 zapret2-v4 zapret2-v6; do
    /sbin/pfctl -a "$a" -F all >/dev/null 2>&1 || true
    /bin/rm -f "/etc/pf.anchors/$a"
done
if [ -f /etc/pf.conf ]; then
    /usr/bin/sed -i '' \
        -e '/^anchor "zapret2"/d' \
        -e '/^rdr-anchor "zapret2"/d' \
        -e '/^set limit table-entries 5000000$/d' \
        /etc/pf.conf 2>/dev/null || true
    /sbin/pfctl -f /etc/pf.conf >/dev/null 2>&1 || true
fi
/usr/bin/pkill -9 -f 'pidfile=/var/run/tpws_' >/dev/null 2>&1 || true
/bin/rm -rf /opt/zapret2
