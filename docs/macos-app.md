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

1. Download `Zapret-<version>.dmg` from [Releases](https://github.com/nikitaSobolev2/zapret2/releases).
2. Open the DMG, drag `Zapret.app` to Applications.
3. Launch and use the power button to install zapret2 (administrator password).

## Release / maintainers

Tag a version and push — CI builds the DMG, attaches it to the GitHub Release, and updates [`Casks/zapret.rb`](../Casks/zapret.rb):

```bash
git tag v1.0.1
git push origin v1.0.1
```

Manual local DMG:

```bash
make app-dmg
# → app/build/compose/binaries/main/dmg/Zapret-<version>.dmg
```

Cask token: `zapret` (`Casks/zapret.rb`).
