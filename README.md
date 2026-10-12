# BlueMap Minimap

A Fabric client mod that displays a server's public BlueMap as an in-game minimap.
No server address or map URL is built into the mod. An independent community
project, not an official BlueMap addon.

**Current source: 0.1.0-rc.7 for Minecraft 26.3.** This is a generic beta/release
candidate. Earlier server-specific releases do not contain the generic setup
described here. Minecraft 26.2 users must keep a 26.2 build (RC.6 or earlier).

**[Download RC.7 for Minecraft 26.3](https://github.com/moyokurumi/bluemap-minimap/releases/tag/v0.1.0-rc.7)**
and choose `bluemap-minimap-0.1.0-rc.7.jar` under Assets. Source archives are for
developers, not installation. [Modrinth](https://modrinth.com/mod/bluemap-minimap)
is intended for unlisted distribution and remains subject to moderation.

- [日本語の導入・設定手順](docs/setup-ja.md)
- [RC.7 changes and validation](docs/release-notes/v0.1.0-rc.7.md)

## Requirements

- Minecraft Java Edition 26.3, Java 25, and Fabric Loader 0.19.5 or newer
- A reachable public BlueMap with compatible low-resolution tiles and metadata
  (BlueMap 5.23 is the protocol target)
- Mod Menu is optional. Install its own dependencies if you use it.

BlueMap Minimap itself does not require Fabric API, Cloth Config, Xaero, Map Link,
or a companion server plugin. It cannot generate maps for servers without a
reachable BlueMap, and does not bypass access restrictions.

## Installation and setup

1. Close Minecraft. Move the old BlueMap Minimap JAR out of `mods` and put the new
   binary JAR there. Keep only one version installed; retain your existing config.
2. Join a server. Automatic discovery is enabled by default.
3. If discovery succeeds and the world has one unambiguous conventional map name,
   the HUD appears automatically.
4. Otherwise open **Esc → BlueMap Minimap → Server setup…**, or **Mods → BlueMap
   Minimap → Configure → Server setup…** with Mod Menu.
5. The current server address and dimension key are prefilled. Enter the public
   BlueMap **root URL**, click **Load maps**, then click **Map** to choose one.
6. Click **Apply and return**, then **Done (save)**. Repeat for custom worlds as
   needed. Settings are stored locally per server, so this is first-time setup.

From the title screen, Mod Menu opens the same settings. Enter a server address
and dimension key manually there, or configure just the global discovery toggle.
Use the root URL without login information, query parameters, or a `#` fragment;
for example `https://map.example.com/` or `https://example.com/bluemap/`.

Done/Esc on the main screen saves; Cancel discards the draft. Esc/Cancel on the
server form discards un-applied edits. Existing profiles and display preferences
are retained when changing one server's map selection.

## Discovery and map selection

Discovery uses a bounded list derived from the Minecraft address: HTTPS on the
exact hostname at `/` and `/bluemap/`, then `mcmap`, `map`, and `bluemap` subdomains
of its parent domain, respecting public/private suffix boundaries. It also tries
HTTP port 8100 at `/` and `/bluemap/` on the exact Minecraft host. It does not search
the web, scan ports/subnets, or use reverse DNS. IPv6 and unusual hosting layouts
can be configured manually.

The endpoint must serve valid BlueMap settings and low-resolution metadata.
Discovery results stay in memory; saving them in the setup screen creates a
persistent profile. An explicit URL takes precedence. The global discovery toggle
disables probing for servers without a configured URL.

Automatic selection recognizes exactly one matching conventional name:

| Minecraft dimension | Candidate BlueMap map IDs |
|---|---|
| `minecraft:overworld` | `overworld`, `world` |
| `minecraft:the_nether` | `the_nether`, `world_nether`, `nether` |
| `minecraft:the_end` | `the_end`, `world_the_end`, `end` |

Names are conventions, not proof of a server's world layout. Choose maps explicitly
for custom/multiple worlds. Ambiguous matches, custom dimensions, and missing
explicit maps hide the HUD instead of picking the first map. Servers that reuse
dimension keys across worlds cannot be distinguished reliably by this client alone
and need future server cooperation.

## Features and limitations

- Square terrain view with north-up or heading-up rotation
- Sizes from 40 to 100 pixels, four screen corners, and three zoom levels
- Public same-map player heads and optional names
- Optional north indicator and X/Z coordinates
- BlueMap SSE updates with HTTP polling fallback
- Bounded downloads and disk/GPU caches; conditional HTTP requests
- Japanese/English settings and optional Mod Menu integration

Terrain changes appear after BlueMap renders them. There is no cave scanner,
entity radar, waypoint system, or full-screen map. Login-required endpoints and
cross-host redirects are not supported. A cold start without metadata stays hidden;
valid cached terrain can remain visible during an established session's outage.

## Local data and network behavior

The distribution has no built-in server profile. User profiles are in
`config/bluemap-minimap.json`; images are in `config/bluemap-minimap-cache/`,
separated by endpoint and map. Neither belongs in a release upload. Earlier
versions' saved profiles remain local and usable.

The client downloads settings, tiles, and public player data from configured or
discovered endpoints. It does not upload coordinates or collect analytics. As with
any web request, the endpoint can observe the client's IP address. Requests have
size/time limits and do not send authentication headers or cookies.

Loading an existing config does not rewrite it. On first save, the previous file
is preserved in `bluemap-minimap.json.pre-rc4.bak` for compatibility with existing
backups; it is never overwritten. Malformed JSON remains intact until an explicit
settings save creates the backup and writes a valid file.

## Building

Use Java 25 and the included Gradle wrapper:

```powershell
./gradlew.bat test build
```

Binary and source JARs are in `build/libs/`. The optional integration compiles
against Mod Menu 21.0.0 using its
[official API](https://github.com/TerraformersMC/ModMenu#java-api).
Mod Menu is compile-only and is not bundled in the JAR.

The in-mod icon source is `art/icon.svg`; regenerate its PNG with
`java art/RenderIcon.java`. It was generated with AI assistance and must not be
reused as a Modrinth page icon without resolving their asset policy.

## License and credits

[MIT](LICENSE). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

- [BlueMap](https://github.com/BlueMap-Minecraft/BlueMap) by Blue (Lukas Rieger)
  and contributors, for the public formats this mod interoperates with
- [Fabric example mod](https://github.com/FabricMC/fabric-example-mod), for the
  public-domain Gradle project template

No BlueMap or Map Link source code is included. This project is not affiliated
with or endorsed by BlueMap, Fabric, or Map Link. Generative AI assisted with code,
the in-mod icon, translations, and documentation. Publication eligibility and
disclosure requirements must be checked with the destination platform.
