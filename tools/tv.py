#!/usr/bin/env python3
"""Minimal ADB-over-TCP client, no adb binary needed (adapted from github.com/Gathin23/tv-vibehack, MIT).
Works where macOS blocks the stock adb binary from the local network.

Set TV_HOST to the TV's IP address. Usage:
    tv.py --probe                  print model / OS info
    tv.py <shell command...>       run one shell command
    tv.py --batch < cmds.txt       one command per line, same connection
    tv.py --pull <remote> <local>  copy a file from the TV
"""
import os
import sys

from adb_shell.adb_device import AdbDeviceTcp
from adb_shell.auth.keygen import keygen
from adb_shell.auth.sign_pythonrsa import PythonRSASigner

HOST = os.environ.get("TV_HOST")
if not HOST:
    sys.exit("Set TV_HOST to the TV's IP address, e.g. TV_HOST=192.168.1.50 (Settings → Network on the TV).")
PORT = int(os.environ.get("TV_PORT", "5555"))
KEY = os.path.join(os.path.dirname(os.path.abspath(__file__)), "adbkey", "adbkey")


def get_signer():
    os.makedirs(os.path.dirname(KEY), exist_ok=True)
    if not os.path.exists(KEY):
        keygen(KEY)
    with open(KEY) as f:
        priv = f.read()
    with open(KEY + ".pub") as f:
        pub = f.read()
    return PythonRSASigner(pub, priv)


def connect(timeout=120):
    dev = AdbDeviceTcp(HOST, PORT, default_transport_timeout_s=float(timeout))
    dev.connect(rsa_keys=[get_signer()], transport_timeout_s=float(timeout), auth_timeout_s=timeout)
    return dev


def main():
    args = sys.argv[1:]
    dev = connect()
    if not args or args == ["--probe"]:
        for p in ("ro.product.model", "ro.product.device", "ro.product.manufacturer",
                  "ro.build.version.release", "ro.build.version.sdk", "ro.build.version.name",
                  "ro.build.display.id", "ro.build.version.fireos", "ro.build.mktg.fireos"):
            print(f"{p} = {dev.shell(f'getprop {p}').strip()}")
    elif args[0] == "--pull" and len(args) == 3:
        dev.pull(args[1], args[2], transport_timeout_s=300.0, read_timeout_s=300.0)
        print(f"pulled {args[1]} -> {args[2]}")
    elif args[0] == "--batch":
        for line in sys.stdin:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            out = dev.shell(line, read_timeout_s=120.0)
            sys.stdout.write(f"$ {line}\n{out}")
            if out and not out.endswith("\n"):
                sys.stdout.write("\n")
            sys.stdout.flush()
    else:
        sys.stdout.write(dev.shell(" ".join(args), read_timeout_s=120.0))
    dev.close()


if __name__ == "__main__":
    main()
