# Engine credits

macOS packet-desync engine (`utunws`) and strategy/list pack are adapted from:

- [bol-van/zapret](https://github.com/bol-van/zapret) (MIT) — nfq / desync core
- [Flowseal/zapret-discord-youtube](https://github.com/Flowseal/zapret-discord-youtube)
  and the macOS utun+BPF port in Flowseal’s zapret-mac work — strategies, fake
  payloads, host/IP lists, and LaunchDaemon runtime scripts

GameFilter pieces from the Windows pack are not included.

## Refreshing strategies before a release

Do **not** ship bol-van `blockcheck` as autoresolve. Autoresolve in the app is a
profile roulette over packaged `.conf.in` files.

To pull newer Flowseal packs:

```sh
# Prefer the macOS port (already has *.conf.in + bins):
./engine/scripts/sync-flowseal-strategies.sh /path/to/zapret-mac-discord-youtube

# Or convert Windows general*.bat (GameFilter stripped):
./engine/scripts/sync-flowseal-strategies.sh /path/to/zapret-discord-youtube
```

Then rebuild the app (`./gradlew packageDmg` / CI) so `engine/payload` is restaged.
Note the upstream commit/date in the release notes when the pack changes.
