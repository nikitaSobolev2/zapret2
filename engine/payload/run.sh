#!/bin/sh
set -eu

DATA_ROOT=$1
BASE='/Library/Application Support/Zapret'
ANCHOR=com.apple/zapret
TOKEN_FILE=/var/run/zapret.pf-token
CONF_FILE=/var/run/zapret.conf
ENGINE_PID=
WATCHDOG_PID=
LOG_MAX_BYTES=5242880

case "$DATA_ROOT" in
    /Users/*/'Library/Application Support/Zapret') ;;
    *) exit 1 ;;
esac

clear_intercept() {
    /sbin/pfctl -a "$ANCHOR" -F all >/dev/null 2>&1 || true
}

release_pf_token() {
    if [ -s "$TOKEN_FILE" ]; then
        TOKEN=$(/bin/cat "$TOKEN_FILE" 2>/dev/null || true)
        if [ -n "$TOKEN" ]; then /sbin/pfctl -X "$TOKEN" >/dev/null 2>&1 || true; fi
        /bin/rm -f "$TOKEN_FILE"
    fi
}

rotate_log() {
    LOG="$BASE/engine.log"
    if [ -f "$LOG" ]; then
        SIZE=$(/usr/bin/stat -f%z "$LOG" 2>/dev/null || echo 0)
        if [ "$SIZE" -gt "$LOG_MAX_BYTES" ]; then
            /bin/mv -f "$LOG" "$BASE/engine.log.1" 2>/dev/null || true
        fi
    fi
}

cleanup() {
    trap - EXIT INT TERM HUP
    clear_intercept
    if [ -n "$WATCHDOG_PID" ]; then kill "$WATCHDOG_PID" >/dev/null 2>&1 || true; fi
    if [ -n "$ENGINE_PID" ]; then kill "$ENGINE_PID" >/dev/null 2>&1 || true; fi
    if [ -n "$WATCHDOG_PID" ]; then wait "$WATCHDOG_PID" >/dev/null 2>&1 || true; fi
    if [ -n "$ENGINE_PID" ]; then wait "$ENGINE_PID" >/dev/null 2>&1 || true; fi
    release_pf_token
}

trap cleanup EXIT INT TERM HUP
clear_intercept
/usr/bin/pkill -9 -x utunws >/dev/null 2>&1 || true

PHYSICAL_IFACE=$(/sbin/route -n get default | /usr/bin/awk '/interface:/{print $2; exit}')
GATEWAY=$(/sbin/route -n get default | /usr/bin/awk '/gateway:/{print $2; exit}')
if [ -z "$PHYSICAL_IFACE" ] || [ -z "$GATEWAY" ]; then exit 1; fi
# Never divert on tunnels (corp VPN ppp/utun/ipsec). Prefer a physical en* WAN.
case "$PHYSICAL_IFACE" in
    en[0-9]*) ;;
    *)
        PHYSICAL_IFACE=$(/sbin/ifconfig -l | /usr/bin/tr ' ' '\n' | /usr/bin/grep -E '^en[0-9]+$' | while read -r IFACE; do
            if /sbin/ifconfig "$IFACE" 2>/dev/null | /usr/bin/grep -q 'status: active' \
                && /sbin/ifconfig "$IFACE" 2>/dev/null | /usr/bin/grep -q 'inet '; then
                echo "$IFACE"
                break
            fi
        done)
        ;;
esac
case "$PHYSICAL_IFACE" in
    en[0-9]*) ;;
    *) echo "no physical WAN interface" >&2; exit 1 ;;
esac
/sbin/ping -c 1 -t 1 "$GATEWAY" >/dev/null 2>&1 || true
GATEWAY_MAC=$(/usr/sbin/arp -n "$GATEWAY" | /usr/bin/awk '/ at /{print $4; exit}')
case "$GATEWAY_MAC" in
    *:*:*:*:*:*) ;;
    *) exit 1 ;;
esac

GATEWAY6_MAC=
if /sbin/route -n get -inet6 default >/var/run/zapret.route6 2>/dev/null; then
    GATEWAY6=$(/usr/bin/awk '/gateway:/{print $2; exit}' /var/run/zapret.route6)
    if [ -n "$GATEWAY6" ]; then
        GATEWAY6_MAC=$(/usr/sbin/ndp -n "$GATEWAY6" 2>/dev/null | /usr/bin/awk '/ at |%/{for(i=1;i<=NF;i++) if($i ~ /^([0-9a-f]{1,2}:){5}[0-9a-f]{1,2}$/){print $i; exit}}')
    fi
fi
/bin/rm -f /var/run/zapret.route6

if [ -L "$DATA_ROOT/selected-strategy" ] || [ ! -f "$DATA_ROOT/selected-strategy" ]; then exit 1; fi
if [ -L "$DATA_ROOT/ipset-mode" ] || [ ! -f "$DATA_ROOT/ipset-mode" ]; then exit 1; fi
if [ -L "$DATA_ROOT/discord-udp" ]; then exit 1; fi
if [ -L "$DATA_ROOT/block-quic" ]; then exit 1; fi

STRATEGY=$(/usr/bin/tr -d '[:space:]' <"$DATA_ROOT/selected-strategy" 2>/dev/null || true)
if ! printf '%s\n' "$STRATEGY" | /usr/bin/grep -Eq '^general(-[a-z0-9]+)*$' || [ ! -f "$BASE/strategies/$STRATEGY.conf.in" ]; then
    STRATEGY=general-fake-tls-auto
fi

if [ -L "$DATA_ROOT" ] || [ -L "$DATA_ROOT/lists" ] || [ ! -d "$DATA_ROOT/lists" ]; then exit 1; fi
RUNTIME_LISTS="$BASE/lists"
/bin/mkdir -p "$RUNTIME_LISTS"

IPSET_MODE=$(/usr/bin/tr -d '[:space:]' <"$DATA_ROOT/ipset-mode" 2>/dev/null || true)
case "$IPSET_MODE" in
    loaded|any|none|'') ;;
    *) IPSET_MODE=none ;;
esac

LIST_NAMES='list-general.txt list-general-user.txt list-google.txt list-exclude.txt list-exclude-user.txt ipset-exclude.txt ipset-exclude-user.txt'
if [ "$IPSET_MODE" = "loaded" ]; then
    LIST_NAMES="$LIST_NAMES ipset-all.txt"
fi
for NAME in $LIST_NAMES; do
    SOURCE_LIST="$DATA_ROOT/lists/$NAME"
    if [ -L "$SOURCE_LIST" ] || [ ! -f "$SOURCE_LIST" ]; then exit 1; fi
    /usr/bin/install -m 0644 "$SOURCE_LIST" "$RUNTIME_LISTS/$NAME"
done
# Keep a tiny placeholder so strategies referencing ipset-all stay readable when mode != loaded.
if [ "$IPSET_MODE" != "loaded" ]; then
    /usr/bin/printf '' >"$RUNTIME_LISTS/ipset-all.txt"
    /bin/chmod 644 "$RUNTIME_LISTS/ipset-all.txt"
fi
/usr/sbin/chown -R root:wheel "$RUNTIME_LISTS"
/bin/chmod 755 "$BASE" "$RUNTIME_LISTS" "$BASE/bin"
/bin/chmod 644 "$RUNTIME_LISTS"/*

case "$IPSET_MODE" in
    loaded) IPSET="$RUNTIME_LISTS/ipset-all.txt" ;;
    any) IPSET="$BASE/ipset-any.txt" ;;
    *) IPSET="$BASE/ipset-none.txt" ;;
esac

DISCORD_UDP=1
if [ -f "$DATA_ROOT/discord-udp" ]; then
    DISCORD_UDP=$(/usr/bin/tr -d '[:space:]' <"$DATA_ROOT/discord-udp" 2>/dev/null || echo 1)
fi
# Browsers prefer HTTP/3; broken QUIC looks like “no internet” while curl/TCP works.
BLOCK_QUIC=1
if [ -f "$DATA_ROOT/block-quic" ]; then
    BLOCK_QUIC=$(/usr/bin/tr -d '[:space:]' <"$DATA_ROOT/block-quic" 2>/dev/null || echo 1)
fi

/usr/bin/sed -e "s|@BASE@|$BASE|g" -e "s|@LISTS@|$RUNTIME_LISTS|g" -e "s|@IPSET@|$IPSET|g" \
    "$BASE/strategies/$STRATEGY.conf.in" >"$CONF_FILE"
# Drop privileges after BPF/utun init (nfq opens devices before --user takes effect).
/usr/bin/printf '\n--user=nobody\n' >>"$CONF_FILE"
/bin/chmod 0600 "$CONF_FILE"

rotate_log
export ZAPRET_IFACE="$PHYSICAL_IFACE"
export ZAPRET_GATEWAY_MAC="$GATEWAY_MAC"
export ZAPRET_GATEWAY6_MAC="$GATEWAY6_MAC"
export ZAPRET_UTUN_UNIT=51
"$BASE/bin/utunws" @"$CONF_FILE" >>"$BASE/engine.log" 2>&1 &
ENGINE_PID=$!

I=0
while ! /sbin/ifconfig utun50 >/dev/null 2>&1; do
    I=$((I + 1))
    if ! kill -0 "$ENGINE_PID" 2>/dev/null || [ "$I" -gt 100 ]; then exit 1; fi
    sleep 0.1
done
/sbin/ifconfig utun50 10.77.0.1 10.77.0.2 netmask 255.255.255.255 up
"$BASE/watchdog.sh" $$ "$ENGINE_PID" &
WATCHDOG_PID=$!

if /sbin/pfctl -s info 2>/dev/null | /usr/bin/grep -q '^Status: Disabled'; then
    TOKEN=$(/sbin/pfctl -E 2>&1 | /usr/bin/awk '/Token :/ { print $3 }')
    if [ -n "$TOKEN" ]; then
        /bin/echo "$TOKEN" >"$TOKEN_FILE"
        /bin/chmod 0600 "$TOKEN_FILE"
    fi
fi

# Bind divert to physical WAN only so split-tunnel corp VPN (ppp0/utun) is untouched.
TCP_RULE="pass out quick on $PHYSICAL_IFACE route-to (utun50 10.77.0.2) inet proto tcp from any to any port {80,443,2053,2083,2087,2096,8443} user { >root } no state"
QUIC_BLOCK="block drop out quick on $PHYSICAL_IFACE inet proto udp from any to any port 443 user { >root } no state"
QUIC_DIVERT="pass out quick on $PHYSICAL_IFACE route-to (utun50 10.77.0.2) inet proto udp from any to any port 443 user { >root } no state"
DISCORD_DIVERT="pass out quick on $PHYSICAL_IFACE route-to (utun50 10.77.0.2) inet proto udp from any to any port {19294:19344,50000:50100} user { >root } no state"
ALL_UDP_DIVERT="pass out quick on $PHYSICAL_IFACE route-to (utun50 10.77.0.2) inet proto udp from any to any port {443,19294:19344,50000:50100} user { >root } no state"
{
    /bin/echo "$TCP_RULE"
    case "$BLOCK_QUIC" in
        0)
            if [ "$DISCORD_UDP" = 0 ]; then
                /bin/echo "$QUIC_DIVERT"
            else
                /bin/echo "$ALL_UDP_DIVERT"
            fi
            ;;
        *)
            /bin/echo "$QUIC_BLOCK"
            if [ "$DISCORD_UDP" != 0 ]; then
                /bin/echo "$DISCORD_DIVERT"
            fi
            ;;
    esac
} | /sbin/pfctl -a "$ANCHOR" -f -

while kill -0 "$ENGINE_PID" 2>/dev/null; do
    sleep 2
    CURRENT_IFACE=$(/sbin/route -n get default 2>/dev/null | /usr/bin/awk '/interface:/{print $2; exit}')
    CURRENT_GATEWAY=$(/sbin/route -n get default 2>/dev/null | /usr/bin/awk '/gateway:/{print $2; exit}')
    if [ "$CURRENT_IFACE" != "$PHYSICAL_IFACE" ] || [ "$CURRENT_GATEWAY" != "$GATEWAY" ]; then exit 1; fi
done
exit 1
