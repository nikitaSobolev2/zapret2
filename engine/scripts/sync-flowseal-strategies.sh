#!/bin/sh
# Refresh strategy pack from Flowseal before a release.
#
# Usage:
#   ./engine/scripts/sync-flowseal-strategies.sh /path/to/zapret-discord-youtube
#   ./engine/scripts/sync-flowseal-strategies.sh /path/to/zapret-mac-discord-youtube
#
# Prefer macos/Payload/strategies/*.conf.in when present (already Darwin-ready).
# Otherwise convert Windows general*.bat → engine/payload/strategies/*.conf.in
# (strips GameFilter blocks and winws --wf-* launch lines).
set -eu

SRC=${1:-}
ROOT=$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)
DEST="$ROOT/engine/payload"
STRATEGIES="$DEST/strategies"
BIN="$DEST/bin"
TSV="$DEST/strategies.tsv"

if [ -z "$SRC" ] || [ ! -d "$SRC" ]; then
    echo "usage: $0 /path/to/Flowseal/zapret-discord-youtube-or-mac-port" >&2
    exit 2
fi

title_for() {
    case "$1" in
        general) echo "general" ;;
        general-alt) echo "general (ALT)" ;;
        general-alt2) echo "general (ALT2)" ;;
        general-alt3) echo "general (ALT3)" ;;
        general-alt4) echo "general (ALT4)" ;;
        general-alt5) echo "general (ALT5)" ;;
        general-alt6) echo "general (ALT6)" ;;
        general-alt7) echo "general (ALT7)" ;;
        general-alt8) echo "general (ALT8)" ;;
        general-alt9) echo "general (ALT9)" ;;
        general-alt10) echo "general (ALT10)" ;;
        general-alt11) echo "general (ALT11)" ;;
        general-alt12) echo "general (ALT12)" ;;
        general-exp) echo "general (EXP)" ;;
        general-fake-tls-auto) echo "general (FAKE TLS AUTO)" ;;
        general-fake-tls-auto-alt) echo "general (FAKE TLS AUTO ALT)" ;;
        general-fake-tls-auto-alt2) echo "general (FAKE TLS AUTO ALT2)" ;;
        general-fake-tls-auto-alt3) echo "general (FAKE TLS AUTO ALT3)" ;;
        general-simple-fake) echo "general (SIMPLE FAKE)" ;;
        general-simple-fake-alt) echo "general (SIMPLE FAKE ALT)" ;;
        general-simple-fake-alt2) echo "general (SIMPLE FAKE ALT2)" ;;
        *) echo "$1" ;;
    esac
}

bat_to_conf() {
    # stdin: bat → stdout: conf.in lines
    /usr/bin/tr -d '\r' | /usr/bin/awk '
        BEGIN { ORS="" }
        /^[[:space:]]*start[[:space:]]/ {
            line=$0
            sub(/^.*winws\.exe"[[:space:]]+/, "", line)
            gsub(/\^[[:space:]]*$/, "", line)
            # drop winws traffic filters
            gsub(/--wf-tcp=[^[:space:]]+[[:space:]]*/, "", line)
            gsub(/--wf-udp=[^[:space:]]+[[:space:]]*/, "", line)
            # strip GameFilter segments (Windows-only)
            n=split(line, parts, /[[:space:]]*--new[[:space:]]*/)
            out=""
            for (i=1; i<=n; i++) {
                seg=parts[i]
                gsub(/^[[:space:]]+|[[:space:]]+$/, "", seg)
                if (seg == "") continue
                if (seg ~ /%GameFilter/) continue
                if (out != "") out = out "\n--new\n"
                # rewrite paths
                gsub(/%BIN%/, "@BASE@/bin/", seg)
                gsub(/%LISTS%/, "@LISTS@/", seg)
                gsub(/"%LISTS%ipset-all\.txt"/, "\"@IPSET@\"", seg)
                gsub(/--ipset="@LISTS@\/ipset-all\.txt"/, "--ipset=\"@IPSET@\"", seg)
                gsub(/--ipset=%LISTS%ipset-all\.txt/, "--ipset=\"@IPSET@\"", seg)
                # normalize quotes already handled; print flags one per line
                gsub(/[[:space:]]+--/, "\n--", seg)
                out = out seg
            }
            print out "\n"
        }
    '
}

MAC_STRAT="$SRC/macos/Payload/strategies"
if [ -d "$MAC_STRAT" ]; then
    echo "copying Darwin strategies from $MAC_STRAT"
    /bin/mkdir -p "$STRATEGIES"
    /usr/bin/find "$STRATEGIES" -name 'general*.conf.in' -delete
    /usr/bin/rsync -a --include='general*.conf.in' --exclude='*' "$MAC_STRAT/" "$STRATEGIES/"
    SRC_BIN="$SRC/macos/Payload/bin"
    if [ -d "$SRC_BIN" ]; then
        echo "merging fake payloads from $SRC_BIN"
        /usr/bin/rsync -a --include='*.bin' --exclude='*' "$SRC_BIN/" "$BIN/"
    fi
else
    echo "converting Windows general*.bat from $SRC"
    /bin/mkdir -p "$STRATEGIES"
    /usr/bin/find "$STRATEGIES" -name 'general*.conf.in' -delete
    for BAT in "$SRC"/general*.bat; do
        [ -f "$BAT" ] || continue
        BASE_NAME=$(/usr/bin/basename "$BAT" .bat)
        case "$BASE_NAME" in
            general|general-alt*|general-exp|general-fake-*|general-simple-*) ;;
            *) continue ;;
        esac
        OUT="$STRATEGIES/$BASE_NAME.conf.in"
        bat_to_conf <"$BAT" >"$OUT"
        # ensure @IPSET@ placeholder for ipset-all references that slipped through
        /usr/bin/sed -i '' 's|--ipset="@LISTS@/ipset-all.txt"|--ipset="@IPSET@"|g' "$OUT"
        /usr/bin/sed -i '' 's|--ipset=@LISTS@/ipset-all.txt|--ipset="@IPSET@"|g' "$OUT"
        echo "  wrote $OUT"
    done
    if [ -d "$SRC/bin" ]; then
        echo "merging fake payloads from $SRC/bin"
        /usr/bin/rsync -a --include='*.bin' --exclude='*' "$SRC/bin/" "$BIN/"
    fi
fi

# lists (optional refresh of defaults)
if [ -d "$SRC/lists" ]; then
    echo "refreshing default-lists from $SRC/lists"
    /usr/bin/rsync -a "$SRC/lists/" "$DEST/default-lists/"
elif [ -d "$SRC/macos/Payload/lists" ]; then
    echo "refreshing default-lists from macos Payload"
    /usr/bin/rsync -a "$SRC/macos/Payload/lists/" "$DEST/default-lists/"
fi

# Flowseal packs apply QUIC fake only to list-general; with ipset=none that
# leaves googlevideo HTTP/3 without desync (site loads, video hangs). Inject
# a list-google UDP/443 profile after the first QUIC block when missing.
GOOGLE_UDP='--filter-udp=443
--hostlist="@LISTS@/list-google.txt"
--dpi-desync=fake
--dpi-desync-repeats=11
--dpi-desync-fake-quic="@BASE@/bin/quic_initial_www_google_com.bin"
--new
'
for CONF in "$STRATEGIES"/general*.conf.in; do
    [ -f "$CONF" ] || continue
    /usr/bin/grep -q 'list-google.txt' "$CONF" || continue
    if /usr/bin/awk '
        prev ~ /^--filter-udp=443$/ && $0 == "--hostlist=\"@LISTS@/list-google.txt\"" { found=1 }
        { prev=$0 }
        END { exit found ? 0 : 1 }
    ' "$CONF"; then
        continue
    fi
    /usr/bin/awk -v add="$GOOGLE_UDP" '
        BEGIN { done=0 }
        {
            print
            if (!done && $0 ~ /quic_initial_www_google_com\.bin/) {
                if ((getline line) > 0) {
                    print line
                    if (line == "--new") {
                        printf "%s", add
                        done=1
                    }
                }
            }
        }
    ' "$CONF" >"$CONF.tmp" && /bin/mv "$CONF.tmp" "$CONF"
done

# rebuild strategies.tsv from conf.in files
: >"$TSV"
for CONF in "$STRATEGIES"/general*.conf.in; do
    [ -f "$CONF" ] || continue
    ID=$(/usr/bin/basename "$CONF" .conf.in)
    printf '%s\t%s\n' "$ID" "$(title_for "$ID")" >>"$TSV"
done
/usr/bin/sort -o "$TSV" "$TSV"

COUNT=$(/usr/bin/wc -l <"$TSV" | /usr/bin/tr -d ' ')
echo "synced $COUNT strategies → $STRATEGIES"
echo "update engine/NOTICE.md with Flowseal commit/date, then rebuild the app."
