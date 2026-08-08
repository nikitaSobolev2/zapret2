# Zapret macOS app

Compose Desktop control app for zapret2: install, start/stop, settings, uninstall.
Works with a split-tunnel corporate VPN when `IFACE_WAN` is the physical interface (usually `en0`).

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

1. Download [Zapret-1.0.0.dmg](https://github.com/nikitaSobolev2/zapret2/releases/download/MacOS/Zapret-1.0.0.dmg)
   (release tag [MacOS](https://github.com/nikitaSobolev2/zapret2/releases/tag/MacOS)).
2. Open the DMG, drag `Zapret.app` to Applications.
3. Launch and use the power button to install zapret2 (administrator password).

## Release / maintainers

Upload the DMG to the GitHub release tagged **`MacOS`** as `Zapret-<version>.dmg`, then bump
`version` / `sha256` in [`Casks/zapret.rb`](../Casks/zapret.rb).

URL pattern:

```text
https://github.com/nikitaSobolev2/zapret2/releases/download/MacOS/Zapret-<version>.dmg
```

Manual local DMG:

```bash
make app-dmg
# → app/build/compose/binaries/main/dmg/Zapret-<version>.dmg
```

Cask token: `zapret` (`Casks/zapret.rb`).
