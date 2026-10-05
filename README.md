# BlueMap Minimap

> **もよさば専用のクライアントMODです。一般のBlueMap導入サーバーでは使用できません。**
> Fabricクライアントの `mods` に入れるMODで、サーバープラグインではありません。
>
> **Moyo server only — not a general-purpose BlueMap addon.**
> This is a Fabric client mod for Moyo participants, not a server plugin.
> Other Minecraft servers are not supported.

## Download and release status

This source is the **Moyo-specific RC.4 candidate**, not a published release.
The only currently published GitHub release is the older
[v0.1.0-rc.1](https://github.com/moyokurumi/bluemap-minimap/releases/tag/v0.1.0-rc.1).
RC.3 has also been distributed through the server's Discord.

- [RC.4 Japanese installation guide (pending publication)](docs/download-moyo-ja.md)
- [RC.4 validation and remaining checks](docs/release-notes/v0.1.0-rc.4.md)
- [Published RC.1 instructions](docs/download-ja.md)

The RC.1 binary requires manual configuration. Do not apply the RC.4 zero-configuration
instructions to it. Previous releases remain available.

## Overview

BlueMap Minimap is a lightweight client-side Fabric mod that displays a
Moyo server's already-rendered BlueMap as a heads-up-display minimap.

On a Fabric Loader installation, the mod needs only its own JAR. Fabric API,
Xaero, Map Link, Cloth Config, and Mod Menu are not required. BlueMap Minimap is
an independent community project and is not an official BlueMap mod.

## Features

- Square BlueMap terrain view with north-up and smooth heading-up modes
- Rotation-aware player direction arrow, terrain coverage, and clipping
- Public same-map BlueMap player heads and names
- Optional north indicator and current X/Z coordinates
- BlueMap 5.23 tile and player updates over Server-Sent Events (SSE)
- HTTP polling fallback when SSE is unavailable
- Persistent, bounded tile cache with conditional HTTP requests
- Built-in Moyo public endpoint and world mappings; no first-run JSON editing
- Strict server scope, unknown-world hiding, and preservation of existing custom settings
- Japanese/English vanilla Minecraft settings screen with no
  configuration-library dependency

## Requirements

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.3 (tested; metadata permits newer loaders)
- `bluemap-minimap-<version>.jar`
- Network access to a BlueMap 5.23-compatible web endpoint

The `mods` directory does not need Fabric API or another minimap mod.

## Installation

For the RC.4 candidate, prepare Minecraft 26.2 with Fabric Loader 0.19.3 and Java 25,
put the mod JAR in `mods`, then join **mc.moyokurumi.com**. Use
**Esc → BlueMap Minimap Settings** for HUD options. Fabric API is not required.
Keep only one version of this mod in `mods`.

## Built-in configuration and upgrades

RC.4 is a Moyo-specific distribution. Only `mc.moyokurumi.com` (case-insensitive,
optionally `:25565`) activates it. Single-player, other hosts, IP aliases, and other
ports do not activate the HUD or endpoint discovery. No DNS probing is performed.
The built-in endpoint is `https://mcmap.moyokurumi.com/`.

A clean installation writes these defaults automatically; see `config.example.json`.
An existing file is not rewritten on load. Missing/empty Moyo profiles use the
built-in defaults in memory. Existing custom URLs and explicit dimension mappings
take precedence; only missing mappings for the standard public endpoint are filled.
Unrelated profiles and display preferences are preserved but do not activate this build
on other servers. The legacy `autoDiscoverBlueMap` field is retained for compatibility
and is not used by this distribution.

The first settings save preserves the existing JSON in
`bluemap-minimap.json.pre-rc4.bak`; that backup is never replaced. Malformed JSON is
left intact on load while in-memory defaults are used. Saving from the settings screen
backs it up before writing a valid configuration.

## World selection

Built-in mappings are `minecraft:overworld`/`minecraft:world` → `overworld` and
`minecraft:resource` → `resource`. The selected map must also appear in BlueMap's
published settings. Unknown worlds, absent maps, and stale custom mappings hide the
minimap; there is no fallback to the first or only published map. World changes clear
the previous session and textures, even when the client level object changes without
a dimension-key change. Actual production world transitions still need RC.4 client
acceptance testing; these key mappings alone do not verify a server's wire protocol.

## BlueMap requirements

The configured web endpoint must expose BlueMap's root settings, per-map
settings, low-resolution tile PNGs, player-head PNGs, live player JSON, and live
SSE endpoints. Published map IDs are checked against the selected mapping, and
the low-resolution tile layout is read from BlueMap's settings.

BlueMap 5.23 is the tested target. Reverse proxies must pass the corresponding
`/maps/<map-id>/live/*` endpoints to BlueMap. Authentication headers and cookies
are not supported in v0.1.

## HUD settings

Open **Esc → BlueMap Minimap Settings**. Changes are previewed immediately and
saved to the JSON configuration when the screen closes.

- Enabled: on or off
- Size: 40, 50, 60, 70, 80, 90, or 100 pixels
- Position: top-left, top-right, bottom-left, or bottom-right
- Zoom: 1, 2, or 4 blocks per HUD pixel
- Public players: on or off
- Player names: on or off
- Map orientation: north up or heading up
- North indicator: on or off
- Current X/Z coordinates: on or off

The default is an 80-pixel minimap in the top-right corner with public players
and names, the north indicator, and coordinates enabled. The default orientation
is north up, so existing configurations keep the RC.1 behavior. Player heads and
names scale with the HUD; names use about 45% of the normal GUI font scale at 80
pixels.

In heading-up mode, the terrain rotates while the self arrow stays pointed up.
Player heads and names stay horizontal, and their positions rotate with the map.
The settings screen previews changes immediately, labels cycle controls with a
`▶`, and provides a short explanation when a control is hovered.

## SSE / fallback behavior

One SSE connection is maintained for the selected map. `tile` events refresh
only nearby cached tiles, and `player` events refresh player markers. `marker`
events are parsed but are not drawn in v0.1.

After an SSE disconnect, reconnect attempts use exponential backoff and player
updates fall back to one-second HTTP polling. Polling stops when SSE reconnects.
Leaving the server, changing worlds, or disabling the mod closes the SSE
stream and cancels obsolete queued work. Late metadata results cannot install an old
session. Failed initialization retries after 30 seconds. Nearby tiles are revalidated
at most every 30 seconds while stationary, so recovery does not require walking or
reconnecting. Cached terrain remains usable in the same session; a cold start without
BlueMap metadata stays hidden until initialization succeeds.

## Performance / cache

- Fetches the current LOD 1 neighborhood and adds only tiles needed to cover a
  rotated viewport at larger HUD sizes
- Limits tile downloads to two concurrent requests and uses a bounded queue
- Uses ETag and Last-Modified validators when available
- Writes validated PNGs atomically so failed downloads do not replace good data
- Bounds the disk cache to 256 tiles per map/layer
- Bounds the in-memory/GPU tile cache to 64 textures
- Performs HTTP, disk I/O, SSE waits, and PNG decoding off the client/render
  thread; only final texture registration returns to the client thread
- Does not perform network or disk access on every HUD frame
- Applies yaw changes only to the GUI transform; turning does not regenerate
  textures, decode PNGs, or change the wanted tile set

## Known limitations

- Block changes do not appear until BlueMap has rendered the affected tile.
- The minimap is hidden in dimensions that have no mapped BlueMap map.
- Nether and End travel have not yet been exercised in the production read-only
  E2E environment.
- BlueMap-outage fallback is covered by the mock test suite, not by stopping a
  production BlueMap service.
- The minimap is square; round masks are intentionally not implemented.
- Only the north compass marker is shown; east, south, and west labels are not
  implemented.
- Waypoints, mobs, caves, death points, areas/claims, POIs, and a full-screen map
  are intentionally not implemented.
- This distribution activates only for the public Moyo Minecraft hostname.
- Authenticated BlueMap endpoints are not supported.

## Troubleshooting

**The minimap is not visible**

- Join `mc.moyokurumi.com` using Minecraft 26.2 / Fabric Loader / Java 25.
- Confirm that the HUD is enabled and the public BlueMap site is reachable.
- Unpublished/unsupported worlds remain hidden. Allow 30 seconds for a retry.
- Existing custom endpoint/mapping settings remain authoritative. Preserve the original
  JSON before troubleshooting an old custom profile; do not erase unrelated settings.

**Players are not visible**

- BlueMap must publish those players, and they must be on the same map and inside
  the visible minimap range.
- Confirm that both player and player-name settings are enabled as desired.

**BlueMap is temporarily unavailable**

- Minecraft remains playable. The map retains valid cached tiles and reconnects
  in the background without chat spam.

## Building

Use Java 25 and the included Gradle Wrapper:

```powershell
./gradlew.bat clean test build
```

Binary and source JARs are written to `build/libs/`. The mock acceptance suite
covers settings and RC.1 migration, bounded same-domain BlueMap discovery,
north-up and heading-up transforms, yaw
normalization, player-marker rotation, rotated viewport coverage including
negative coordinates and 45-degree corners, idle yaw changes, map settings,
tile downloads, duplicate suppression, cache behavior, conditional GET, SSE
events, disconnect/reconnect, polling fallback, world-change cancellation,
invalid PNGs, timeouts, oversized responses, and built-in Japanese/English UI
labels.

## License

BlueMap Minimap is released under the [MIT License](LICENSE). See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for third-party attribution.

## Credits

- [BlueMap](https://github.com/BlueMap-Minecraft/BlueMap) by Blue (Lukas Rieger)
  and contributors, for the public MIT-licensed map formats this mod interoperates
  with
- [Fabric example mod](https://github.com/FabricMC/fabric-example-mod) for the
  public-domain Gradle project template

No BlueMap or Map Link source code is included. This project is not affiliated
with or endorsed by BlueMap, Fabric, or Map Link.
