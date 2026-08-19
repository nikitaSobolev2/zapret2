#!/bin/sh
set -e
PATH="/usr/sbin:/sbin:/usr/bin:/bin"
export PATH

user="$1"
case "$user" in
    ""|*[!A-Za-z0-9._-]*)
        echo "invalid sudoers username" >&2
        exit 1
        ;;
esac

stop="/Library/Application Support/Zapret/stop.sh"
restart="/Library/Application Support/Zapret/restart.sh"
sudoers=/etc/sudoers.d/zapret

tmp="$(mktemp)"
umask 337
stop_esc="$(printf '%s' "$stop" | sed 's/ /\\ /g')"
restart_esc="$(printf '%s' "$restart" | sed 's/ /\\ /g')"
printf '%s ALL=(root) NOPASSWD: %s, %s\n' "$user" "$stop_esc" "$restart_esc" >"$tmp"
if visudo -cf "$tmp" >/dev/null 2>&1; then
    install -m 440 -o root -g wheel "$tmp" "$sudoers"
    rm -f "$tmp"
    rm -f /etc/sudoers.d/zapret2
    echo OK
else
    rm -f "$tmp"
    echo "generated sudoers rule failed validation" >&2
    exit 1
fi
