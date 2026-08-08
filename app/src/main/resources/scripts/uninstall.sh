#!/bin/sh
# removes zapret2 : daemons, PF hooks, launchd job and files
# usage : uninstall.sh DST

DST="${1:-/opt/zapret2}"
PLIST=/Library/LaunchDaemons/zapret2.plist
FALLBACK=

echo "* stopping zapret2 and removing hooks"
if [ -x "$DST/uninstall_easy.sh" ]; then
	"$DST/uninstall_easy.sh" </dev/null || FALLBACK=1
else
	FALLBACK=1
fi

[ -n "$FALLBACK" ] && {
	echo "* uninstaller unavailable, cleaning up manually"
	[ -x "$DST/init.d/macos/zapret2" ] && "$DST/init.d/macos/zapret2" stop </dev/null || true
	pkill -f "pidfile=/var/run/tpws_" || true
	for anchor in zapret2 zapret2-v4 zapret2-v6; do
		pfctl -a "$anchor" -F all 2>/dev/null || true
		rm -f "/etc/pf.anchors/$anchor"
	done
	[ -f /etc/pf.conf ] && {
		sed -i '' '/zapret2/d' /etc/pf.conf
		pfctl -f /etc/pf.conf 2>/dev/null || true
	}
}

rm -f "$PLIST"
rm -f /etc/sudoers.d/zapret2
rm -rf "$DST"

echo "* zapret2 removed"
