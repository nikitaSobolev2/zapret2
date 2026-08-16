cask "zapret" do
  version "2.2.1"
  sha256 "6b667e437133d8622e0008478658a28588b88a32e7f015952a43ebe8cb514e53"

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

    sidecar = "#{appdir}/Zapret.app/Contents/app/resources/tg-ws-proxy"
    if File.directory?(sidecar)
      Dir.glob("#{sidecar}/**/*.{so,dylib}").each { |lib| File.chmod(0o755, lib) }
      python = "#{sidecar}/_internal/Python"
      File.chmod(0o755, python) if File.exist?(python)
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
