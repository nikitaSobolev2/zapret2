# Zapret macOS app

Compose Desktop control app for zapret on macOS. The DPI engine is **utunws** (Flowseal-style
nfq packet desync over utun + BPF), not tpws. The app installs a LaunchDaemon, PF `route-to`
rules, strategy profiles, and editable host/IP lists.

Credits: [bol-van/zapret](https://github.com/bol-van/zapret) (MIT core) and
[Flowseal](https://github.com/Flowseal/zapret-discord-youtube) strategies/lists/macOS utun path.
See [`engine/NOTICE.md`](../engine/NOTICE.md).

### Engine packaging

Gradle stages [`engine/payload`](../engine/payload) and builds universal `utunws` via
[`engine/build_utunws.sh`](../engine/build_utunws.sh) into app resources (`engine/`).
Install copies that payload to `/Library/Application Support/Zapret` and starts
LaunchDaemon `org.zapret.macos.engine`.

User state:

- `~/Library/Application Support/Zapret/selected-strategy`
- `~/Library/Application Support/Zapret/ipset-mode` (`none` | `loaded` | `any`)
- `~/Library/Application Support/Zapret/lists/*` (editable in Settings; Reset restores package defaults)

Requires a physical WAN with gateway ARP (Ethernet/Wi‑Fi). Use split-tunnel VPN only.
PF divert is IPv4-only (same as upstream Flowseal macOS).

The DMG also ships a headless **tg-ws-proxy** sidecar — vendored MIT code by
**[Flowseal](https://github.com/Flowseal/tg-ws-proxy)** under `third_party/tg-ws-proxy/`.
Gradle task `buildTgWsProxySidecar` builds it into app resources. The app (and the Homebrew
cask `postflight`) restores the execute bit on nested Mach-O if a DMG/Homebrew copy dropped it.

### In-app updates

Settings → **Обновления**: toggle **Автообновление** (default on). Prefs:
`~/Library/Application Support/Zapret/app-prefs.json`. Same DMG as Homebrew (`vX.Y.Z` release assets).
Unsigned builds may still need `xattr -cr` once after replace.

### Telegram Desktop fix

Off by default so a sidecar crash cannot block Zapret. To use it:

1. Start Zapret (power button → status «Работает»).
2. **Settings → Telegram MTProto** — turn on **«Включён с Zapret»** (takes effect immediately).
3. Click **«Открыть в Telegram»**.
4. Fallback: copy `tg://` link.

Helps **Telegram Desktop** only. Config/logs: `~/Library/Application Support/Zapret/tg-ws-proxy/`.

## Install with Homebrew

```bash
brew tap nikitaSobolev2/zapret2 https://github.com/nikitaSobolev2/zapret2
brew install --cask zapret
brew update && brew upgrade --cask zapret
```

First launch (unsigned build):

```bash
xattr -cr /Applications/Zapret.app
```

## Install from DMG

1. Download the matching disk image from the GitHub release:
   `Zapret-<version>-arm64.dmg` (Apple Silicon) or `Zapret-<version>-x86_64.dmg` (Intel).
2. Drag `Zapret.app` to Applications.
3. Launch and use the power button (administrator password) to install the engine.

## Release checklist

1. Tag `vX.Y.Z` and push the tag.
2. Workflow **`build`** creates the GitHub Release (Linux archives).
3. Workflow **`macOS app`** builds Apple Silicon and Intel DMGs, attaches them to the same release, bumps the cask.
4. `workflow_dispatch` on `macOS app` only builds a CI artifact — no publish/cask bump.

```bash
cd app && ./gradlew packageDmg -PappVersion=1.2.3
```

### Gatekeeper (unsigned builds)

```bash
xattr -cr /Applications/Zapret.app
```
