cask "zapret" do
  version "1.0.0"
  sha256 "dcf9b0cd245751a2334f93f233caa40bda76264424b4d5a59505ec0d0be5f952"

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
