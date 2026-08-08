cask "zapret" do
  version "1.2.1"
  sha256 "8c6905f71bd573c206487dac4f9576e1ccab9fa90679e6aa4178251cf84e1359"

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

  # Nested PyInstaller Mach-O often loses +x when the DMG is copied into /Applications.
  postflight do
    binary = "#{appdir}/Zapret.app/Contents/app/resources/tg-ws-proxy/tg-ws-proxy"
    File.chmod(0o755, binary) if File.exist?(binary)
  end

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
