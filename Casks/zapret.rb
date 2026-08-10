cask "zapret" do
  version "2.1.2"
  sha256 "7a4015fec2b4f9228dea7f70ac7590e59c78c8098f46bfc822f4af6ecf4c2f65"

  url "https://github.com/nikitaSobolev2/zapret2/releases/download/v#{version}/Zapret-#{version}.dmg"
  name "Zapret"
  desc "macOS control app for zapret (DPI bypass via utunws + PF)"
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

  # Nested Mach-O often loses +x when the DMG is copied into /Applications.
  postflight do
    [
      "#{appdir}/Zapret.app/Contents/app/resources/tg-ws-proxy/tg-ws-proxy",
      "#{appdir}/Zapret.app/Contents/app/resources/engine/bin/utunws",
    ].each do |binary|
      File.chmod(0o755, binary) if File.exist?(binary)
    end
  end

  zap trash: [
    "~/Library/Caches/org.zapret.macos.control",
    "~/Library/Preferences/org.zapret.macos.control.plist",
    "~/Library/Saved Application State/org.zapret.macos.control.savedState",
  ]

  caveats <<~EOS
    Zapret needs administrator rights to install utunws
    (/Library/Application Support/Zapret, PF, LaunchDaemon).

    The app is not notarized. On first launch use right-click → Open, or:
      xattr -cr "#{appdir}/Zapret.app"

    Needs a physical WAN with gateway ARP. Keep corporate VPN in split-tunnel
    mode (do not send all traffic over VPN).

    Editable lists: ~/Library/Application Support/Zapret/lists/
  EOS
end
