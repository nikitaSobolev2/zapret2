cask "zapret" do
  version "1.0.1"
  sha256 "29c68efd9640741d94087d04569a3c72a2b2f1feddbcd00ddd21cdf1aa443494"

  url "https://github.com/nikitaSobolev2/zapret2/releases/download/MacOS/Zapret-#{version}.dmg"
  name "Zapret"
  desc "macOS control app for zapret2 (DPI bypass via tpws + PF)"
  homepage "https://github.com/nikitaSobolev2/zapret2"

  livecheck do
    url "https://github.com/nikitaSobolev2/zapret2/releases?q=MacOS"
    regex(/Zapret[._-]v?(\d+(?:\.\d+)+)\.dmg/i)
  end

  depends_on macos: :monterey

  app "Zapret.app"

  zap trash: [
    "~/Library/Caches/org.zapret.macos.control",
    "~/Library/Preferences/org.zapret.macos.control.plist",
    "~/Library/Saved Application State/org.zapret.macos.control.savedState",
  ]

  caveats <<~EOS
    Zapret needs administrator rights to install and control zapret2 (/opt/zapret2, PF).

    The app is not notarized. On first launch use right-click → Open, or:
      xattr -cr "#{appdir}/Zapret.app"

    Keep a split-tunnel corporate VPN (do not send all traffic over VPN) so
    transparent mode can bind to the physical WAN (usually en0).
  EOS
end
