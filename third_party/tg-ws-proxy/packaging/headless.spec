# PyInstaller spec: headless CLI sidecar for Zapret (no tray / CustomTkinter).
import os

from PyInstaller.utils.hooks import collect_data_files

block_cipher = None
root = os.path.abspath(os.path.join(os.path.dirname(SPEC), os.pardir))
entry = os.path.join(os.path.dirname(SPEC), "run_headless.py")
certifi_datas = collect_data_files("certifi")

a = Analysis(
    [entry],
    pathex=[root],
    binaries=[],
    datas=certifi_datas,
    hiddenimports=[
        "certifi",
        "cryptography.hazmat.primitives.ciphers",
        "cryptography.hazmat.primitives.ciphers.algorithms",
        "cryptography.hazmat.primitives.ciphers.modes",
        "cryptography.hazmat.backends.openssl",
        "proxy",
        "proxy.tg_ws_proxy",
        "proxy.config",
        "proxy.bridge",
        "proxy.raw_websocket",
        "proxy.fake_tls",
        "proxy.balancer",
        "proxy.pool",
        "proxy.stats",
        "proxy.utils",
        "proxy._aes",
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[
        "tkinter",
        "customtkinter",
        "pystray",
        "PIL",
        "objc",
        "AppKit",
        "Foundation",
    ],
    noarchive=False,
    cipher=block_cipher,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="tg-ws-proxy",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=True,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=False,
    name="tg-ws-proxy",
)
