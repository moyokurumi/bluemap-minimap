# BlueMap Minimap

## Overview

BlueMap Minimap is a lightweight client-side Fabric mod that displays a
server's already-rendered BlueMap as a heads-up-display minimap.

On a Fabric Loader installation, the mod needs only its own JAR. Fabric API,
Xaero, Map Link, Cloth Config, and Mod Menu are not required. BlueMap Minimap is
an independent community project and is not an official BlueMap mod.

## Features

- Square, north-up BlueMap terrain view centered on the local player
- Player direction arrow
- Public same-map BlueMap player heads and names
- BlueMap 5.23 tile and player updates over Server-Sent Events (SSE)
- HTTP polling fallback when SSE is unavailable
- Persistent, bounded tile cache with conditional HTTP requests
- Per-server BlueMap URL and Minecraft-dimension mapping
- Vanilla Minecraft settings screen with no configuration-library dependency

## Requirements

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.3 or newer in the 0.19 line
- `bluemap-minimap-<version>.jar`
- Network access to a BlueMap 5.23-compatible web endpoint

The `mods` directory does not need Fabric API or another minimap mod.

## Installation

1. Prepare Minecraft 26.2 with Fabric Loader.
2. Put the BlueMap Minimap JAR in the instance's `mods` directory.
3. Start Minecraft once to create `config/bluemap-minimap.json`.
4. Add the Minecraft server address and BlueMap URL to that file, using
   [config.example.json](config.example.json) as a template.
5. Join the server, then use **Esc → BlueMap Minimap Settings** for HUD options.

Server addresses and BlueMap URLs are currently configured in JSON; the in-game
screen controls HUD behavior only.

## Configuration

```json
{
  "enabled": true,
  "size": 80,
  "position": "TOP_RIGHT",
  "zoom": 2.0,
  "showPlayers": true,
  "showNames": true,
  "servers": {
    "play.example.com": {
      "blueMapUrl": "https://map.example.com/",
      "dimensions": {
        "minecraft:overworld": "example-overworld"
      }
    }
  }
}
```

Use the same address that appears in the Minecraft multiplayer server entry.
The default Minecraft port (`:25565`) is normalized automatically. Prefer HTTPS;
URLs containing embedded user information are rejected.

## Server mapping

Each key under `servers` identifies one Minecraft server. Its `blueMapUrl`
points to that server's BlueMap web root. `dimensions` maps a Minecraft
dimension key to an exact published BlueMap map ID.

The Overworld can be detected automatically when BlueMap publishes it as
`overworld` or `world`. Other dimensions should be mapped explicitly. If no
matching BlueMap map exists, the HUD background stays hidden and the mod does
not retry every game tick.

## BlueMap requirements

The configured web endpoint must expose BlueMap's root settings, per-map
settings, low-resolution tile PNGs, player-head PNGs, live player JSON, and live
SSE endpoints. BlueMap's map ID and low-resolution tile size are discovered from
its settings; they are not hard-coded.

BlueMap 5.23 is the tested target. Reverse proxies must pass the corresponding
`/maps/<map-id>/live/*` endpoints to BlueMap. Authentication headers and cookies
are not supported in v0.1.

## HUD settings

Open **Esc → BlueMap Minimap Settings**. Changes are previewed immediately and
saved to the JSON configuration when the screen closes.

- Enabled: on or off
- Size: 80, 112, 144, 176, 208, or 240 pixels
- Position: top-left, top-right, bottom-left, or bottom-right
- Zoom: 1, 2, or 4 blocks per HUD pixel
- Public players: on or off
- Player names: on or off

The default is an 80-pixel minimap in the top-right corner with public players
and names enabled. Player heads and names scale with the HUD; names use about
45% of the normal GUI font scale at 80 pixels.

## SSE / fallback behavior

One SSE connection is maintained for the selected map. `tile` events refresh
only nearby cached tiles, and `player` events refresh player markers. `marker`
events are parsed but are not drawn in v0.1.

After an SSE disconnect, reconnect attempts use exponential backoff and player
updates fall back to one-second HTTP polling. Polling stops when SSE reconnects.
Leaving the server, changing dimensions, or disabling the mod closes the SSE
stream and cancels obsolete queued work.

## Performance / cache

- Fetches only the current LOD 1 tile and its eight neighbors
- Limits tile downloads to two concurrent requests and uses a bounded queue
- Uses ETag and Last-Modified validators when available
- Writes validated PNGs atomically so failed downloads do not replace good data
- Bounds the disk cache to 256 tiles per map/layer
- Bounds the in-memory/GPU tile cache to 64 textures
- Performs HTTP, disk I/O, SSE waits, and PNG decoding off the client/render
  thread; only final texture registration returns to the client thread
- Does not perform network or disk access on every HUD frame

## Known limitations

- Block changes do not appear until BlueMap has rendered the affected tile.
- The minimap is hidden in dimensions that have no mapped BlueMap map.
- Nether and End travel have not yet been exercised in the production read-only
  E2E environment.
- BlueMap-outage fallback is covered by the mock test suite, not by stopping a
  production BlueMap service.
- The map is north-up and cannot rotate.
- Waypoints, mobs, caves, death points, areas/claims, POIs, and a full-screen map
  are intentionally not implemented.
- Automatic BlueMap URL discovery and authenticated BlueMap endpoints are not
  supported.

## Troubleshooting

**The minimap is not visible**

- Confirm that the multiplayer server address matches a key under `servers`.
- Confirm that `blueMapUrl` reaches BlueMap's `settings.json`.
- Check that the current dimension maps to a published BlueMap map ID.
- Confirm that the HUD is enabled in the settings screen.

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
covers settings, map settings, positive and negative tile coordinates, tile
downloads, duplicate suppression, cache behavior, conditional GET, SSE events,
disconnect/reconnect, polling fallback, world-change cancellation, invalid PNGs,
timeouts, and oversized responses.

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
