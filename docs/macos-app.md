# Zapret macOS app

Compose Desktop control app for zapret2: install, start/stop, settings, uninstall.
Works with a split-tunnel corporate VPN when `IFACE_WAN` is the physical interface (usually `en0`).

Packaging runs `make mac` into the staged source tree, so the DMG ships a universal prebuilt
`tpws` (plus `ip2net` / `mdig`). Install skips compile when that binary is present — Xcode CLT
is only needed for source-only / Gradle-dev trees without a prior build.

The DMG also ships a headless **tg-ws-proxy** sidecar — vendored MIT code by
**[Flowseal](https://github.com/Flowseal/tg-ws-proxy)**
([github.com/Flowseal/tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy)) under
`third_party/tg-ws-proxy/`. Gradle task `buildTgWsProxySidecar` builds it into app resources.
The Compose app starts/stops it with Zapret (user process on `127.0.0.1:1443`, no sudo).
Settings credit the author with a link to that repository.

### In-app updates

Settings → **Обновления**: toggle **Автообновление** (default on). When enabled, the app checks
GitHub Releases on launch and, if a newer `Zapret-*.dmg` exists, downloads and replaces the
`.app` (modal with **Отмена**). Manual **Проверить обновления** / **Обновить сейчас** work the
same path. Prefs: `~/Library/Application Support/Zapret/app-prefs.json`. Same DMG as Homebrew
(`vX.Y.Z` release assets). Unsigned builds may still need `xattr -cr` once after replace.

### Telegram Desktop fix

1. Start Zapret (power button → status «Работает»).
2. **Settings → Telegram MTProto proxy** (credit: Flowseal / tg-ws-proxy) — leave **«Включён с Zapret»** on.
3. Click **«Открыть в Telegram»** (`open tg://proxy?…`, same as upstream TG WS Proxy tray).
4. Fallback: **«Копировать tg:// proxy»** and open the link inside Telegram, or add an MTProto
   proxy manually (Advanced → Connection type).

Helps **Telegram Desktop** only. Does not fix `web.telegram.org` when the ISP blackholes Telegram
IPs at TCP level. Config/logs: `~/Library/Application Support/Zapret/tg-ws-proxy/`.

## Install with Homebrew

```bash
brew tap nikitaSobolev2/zapret2 https://github.com/nikitaSobolev2/zapret2
brew install --cask zapret
```

Upgrade:

```bash
brew update
brew upgrade --cask zapret
```

Uninstall app only (leaves `/opt/zapret2` unless you remove it from the app):

```bash
brew uninstall --cask zapret
```

First launch (unsigned build): right-click → Open, or:

```bash
xattr -cr /Applications/Zapret.app
```

## Install from DMG

1. Download `Zapret-<version>.dmg` from the latest GitHub release
   (e.g. [v1.1.0](https://github.com/nikitaSobolev2/zapret2/releases/latest)).
2. Open the DMG, drag `Zapret.app` to Applications.
3. Launch and use the power button to install zapret2 (administrator password).

## Release checklist

1. Tag `vX.Y.Z` and push the tag.
2. Workflow **`build`** creates the GitHub Release and uploads source/binary archives.
3. Workflow **`macOS app`** builds the DMG, attaches `Zapret-<version>.dmg` to the **same**
   `vX.Y.Z` release, and bumps [`Casks/zapret.rb`](../Casks/zapret.rb) `version` / `sha256`.
4. Homebrew URL shape:
   `https://github.com/nikitaSobolev2/zapret2/releases/download/v<version>/Zapret-<version>.dmg`
5. `workflow_dispatch` on `macOS app` only builds a CI artifact — it does **not** publish or bump the cask.

Manual local DMG:

```bash
make app-dmg
# → app/build/compose/binaries/main/dmg/Zapret-<version>.dmg
```

### Gatekeeper (unsigned builds)

The DMG is not Apple-notarized. First launch may need right-click → Open, or:

```bash
xattr -cr /Applications/Zapret.app
```

Cask token: `zapret` (`Casks/zapret.rb`).
