#!/bin/sh
set -eu

SOURCE=$1
DATA_ROOT=$2
WANT_NOPASSWD=${3:-1}
DEST='/Library/Application Support/Zapret'
LABEL=org.zapret.macos.engine
PLIST=/Library/LaunchDaemons/${LABEL}.plist
ANCHOR=com.apple/zapret

case "$DATA_ROOT" in
    /Users/*/'Library/Application Support/Zapret') ;;
    *) echo 'invalid user data path'; exit 1 ;;
esac
case "$DATA_ROOT" in
    *['|&;$`"\\<>']*|*$'\n'*|*$'\r'*) echo 'invalid user data path'; exit 1 ;;
esac

# Tear down legacy tpws (/opt/zapret2) install if present.
migrate_legacy_tpws() {
    OLD_INIT=/opt/zapret2/init.d/macos/zapret2
    OLD_PLIST=/Library/LaunchDaemons/zapret2.plist
    if [ -x "$OLD_INIT" ]; then
        "$OLD_INIT" stop >/dev/null 2>&1 || true
    fi
    /bin/launchctl bootout system/zapret2 >/dev/null 2>&1 || true
    /bin/rm -f "$OLD_PLIST"
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
    /bin/rm -f /etc/sudoers.d/zapret2
    if [ -d /opt/zapret2 ]; then
        /bin/rm -rf /opt/zapret2
    fi
}

migrate_legacy_tpws

/bin/launchctl bootout system/"$LABEL" >/dev/null 2>&1 || true
/sbin/pfctl -a "$ANCHOR" -F all >/dev/null 2>&1 || true
/usr/bin/pkill -9 -x utunws >/dev/null 2>&1 || true
/bin/mkdir -p "$DEST"
/usr/bin/rsync -a --delete "$SOURCE/" "$DEST/"
/usr/bin/touch "$DEST/ipset-any.txt"
/usr/sbin/chown -R root:wheel "$DEST"
# Temp staging dirs are often 0700; after droproot utunws must traverse BASE to read lists/bins.
/usr/bin/find "$DEST" -type d -exec /bin/chmod 755 {} +
/usr/bin/find "$DEST" -type f -exec /bin/chmod 644 {} +
/bin/chmod 755 "$DEST/install.sh" "$DEST/run.sh" "$DEST/restart.sh" "$DEST/stop.sh" "$DEST/watchdog.sh" "$DEST/uninstall.sh" "$DEST/install-sudoers.sh" "$DEST/bin/utunws"
DATA_ROOT="$DATA_ROOT" /usr/bin/awk '
BEGIN { r = ENVIRON["DATA_ROOT"] }
{
  n = split($0, a, "@DATA_ROOT@")
  out = a[1]
  for (i = 2; i <= n; i++) out = out r a[i]
  print out
}' "$DEST/org.zapret.macos.engine.plist.in" > "$PLIST"
/usr/sbin/chown root:wheel "$PLIST"
/bin/chmod 644 "$PLIST"
/bin/launchctl enable system/"$LABEL"
I=0
while ! /bin/launchctl bootstrap system "$PLIST"; do
    I=$((I + 1))
    if [ "$I" -gt 10 ]; then exit 1; fi
    sleep 0.5
done
/bin/launchctl kickstart -k system/"$LABEL"

I=0
while ! /sbin/ifconfig utun50 >/dev/null 2>&1; do
    I=$((I + 1))
    if [ "$I" -gt 100 ]; then
        /bin/launchctl bootout system/"$LABEL" >/dev/null 2>&1 || true
        /sbin/pfctl -a "$ANCHOR" -F all >/dev/null 2>&1 || true
        /usr/bin/pkill -9 -x utunws >/dev/null 2>&1 || true
        echo 'utunws did not stay running' >&2
        /usr/bin/tail -40 "$DEST/engine.log" >&2 2>/dev/null || true
        exit 1
    fi
    sleep 0.1
done

user_name="${DATA_ROOT#/Users/}"
user_name="${user_name%%/*}"
if [ "$WANT_NOPASSWD" = 1 ]; then
    "$DEST/install-sudoers.sh" "$user_name" || echo "zapret: passwordless sudoers skipped" >&2
else
    /bin/rm -f /etc/sudoers.d/zapret /etc/sudoers.d/zapret2
fi
