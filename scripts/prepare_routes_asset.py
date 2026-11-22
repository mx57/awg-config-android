#!/usr/bin/env python3
"""Строит app/src/main/assets/routes.json из каталога донора (ipCatalog/snapshot.json).

Формат ассета:
{
  "builtAt": "...",
  "warpEndpointCidrs24": ["162.159.192.0/24", ...],
  "presets": { "<key>": { "cidrs4": [...], "cidrs6": [...] } }
}
Офлайн-набор = fallback каталога донора; для транзитных пресетов без fallback
(telegram) используется резерв из routePresets.js (legacy.cidrs4).
"""
import json
import sys
from pathlib import Path

DONOR_SNAPSHOT = Path("/home/z/my-project/donor/amnezia-config-gen/src/server/ipCatalog/snapshot.json")
OUT = Path("/home/z/my-project/awg-config-android/app/src/main/assets/routes.json")

# Резерв telegram (транзитный пресет без статического fallback в снапшоте)
TELEGRAM_RESERVE4 = [
    "91.108.4.0/22", "91.108.8.0/22", "91.108.12.0/22", "91.108.16.0/22",
    "91.108.20.0/22", "91.108.56.0/22", "91.105.192.0/23", "149.154.160.0/20",
    "185.76.151.0/24",
]

WARP_ENDPOINT_CIDRS24 = [
    "162.159.192.0/24", "162.159.193.0/24", "162.159.195.0/24",
    "188.114.96.0/24", "188.114.97.0/24", "188.114.99.0/24",
]

def main() -> int:
    snap = json.loads(DONOR_SNAPSHOT.read_text(encoding="utf-8"))
    presets = snap.get("presets", {})
    out_presets = {}
    skipped = []
    for key, data in sorted(presets.items()):
        # catalog-пресеты держат маршруты на верхнем уровне; транзитные — в fallback
        cidrs4 = list(data.get("cidrs4") or [])
        cidrs6 = list(data.get("cidrs6") or [])
        if not cidrs4:
            fallback = data.get("fallback") or {}
            cidrs4 = list(fallback.get("cidrs4") or [])
            cidrs6 = list(fallback.get("cidrs6") or [])
        if not cidrs4 and key == "telegram":
            cidrs4 = TELEGRAM_RESERVE4
        if not cidrs4:
            skipped.append(key)
            continue
        out_presets[key] = {"cidrs4": cidrs4, "cidrs6": cidrs6}
    payload = {
        "builtAt": snap.get("builtAt"),
        "source": "amnezia-config-gen ipCatalog snapshot (AGPL-3.0), fallback reserve",
        "warpEndpointCidrs24": WARP_ENDPOINT_CIDRS24,
        "presets": out_presets,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    total4 = sum(len(v["cidrs4"]) for v in out_presets.values())
    total6 = sum(len(v["cidrs6"]) for v in out_presets.values())
    print(f"presets={len(out_presets)} skipped_no_data={skipped} cidrs4={total4} cidrs6={total6}")
    print(f"size={OUT.stat().st_size / 1024:.0f} KB -> {OUT}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
