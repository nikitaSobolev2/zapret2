cask "zapret" do
  version "1.1.1"
  sha256 "2376e441eccefc15af72f2bfae7c93bde53b59b5963314d4b3a61e242b17b2dd"

  url "https://github.com/nikitaSobolev2/zapret2/releases/download/v#{version}/Zapret-#{version}.dmg"
  name "Zapret"
  desc "macOS control app for zapret2 (DPI bypass via tpws + PF)"
  homepage "https://github.com/nikitaSobolev2/zapret2"

  livecheck do
    url :homepage
    regex(/Zapret[._-]v?(\d+(?:\.\d+)+)\.dmg/i)
    strategy :github_releases do |json, regex|
      json.filter_map do |release|
        next if release["draft"] || release["prerelease"]

        release["assets"]&.filter_map do |asset|
          match = asset["name"]&.match(regex)
          match[1] if match
        end
      end.flatten
    end
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
