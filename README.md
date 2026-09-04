# MapArtist

MapArtist turns Minecraft's item-frame maps into **full pixel-art editors** — in your browser.

Convert any normal map into a *drawing map*, then open it in a clean web editor with brushes, shapes, text, bucket fill, shading, an eyedropper, and more. What you paint in the browser updates the map in-game the moment you submit. Multiple maps can even be joined into a single **wall-sized canvas** for multi-map art.

## Features

- **Browser-based editor** — no client mods. Tools include pixel brush, line, square/circle, text (BMFonts), bucket fill, shade (darken/lighten chains), and eraser — plus undo/redo, mirroring, rotation, centering, zoom, and draft autosaves.
- **Easy map creation** — use the *paintbrush* on any normal map to convert it into a drawing map (no more command-wrangling).
- **Map locking** — a map can be locked so only its owner can edit it, enforced both in the editor and server-side.
- **Multi-map walls** — a grid of drawing maps becomes one big canvas; the editor slices your work back into each map automatically.
- **Backups** — export `.dat` files (or a `.zip` of an entire wall) and re-import them anytime.
- **Protections** — maps survive fire/lava/cactus/explosions as dropped items, and locked maps can't be broken out of their frames.
- **Logging** — every edit is written to a readable log for moderation.

## Requirements

- **Minecraft 1.21+** — Spigot / Paper (or forks)
- **Java 21**
- A web browser on the machine where the map is drawn (the plugin runs its own small web server)
- Recommended: `1.21.4+` so the paintbrush's vanilla `item_model` renders correctly

> The web editor runs over plain HTTP on your configured `host:port`. For a private/friend server on a LAN this is fine. If you expose it publicly, put it behind HTTPS or restrict access (`host`, firewall) — details in the wiki.

## Installation

1. **Download** the latest `MapArtist-*.jar` from the [Releases](https://github.com/willits/mapartist/releases) page.
2. Drop the jar into your server's `plugins/` folder.
3. Restart the server (or run `/reload`).
4. Make sure the configured `host` and `port` (defaults: `localhost`, `8080`) are reachable from the browser you'll draw with. On the same machine this works out of the box; for remote drawing, set `host` to your server's address and forward/open the port.
5. Optional: tweak `plugins/MapArtist/config.yml` to taste, then run `/mapartist reload` (no restart needed).

## Quickstart

### Get a paintbrush

`/mapartist brush` — as a player with `mapartist.admin` (OP by default). The brush is a configurable stick (`paintbrush` section in `config.yml`).

> Paintbrushes are ordinary items, so admins can also give them out, drop them, or vendor them — however your server likes.

### Make your first drawing map

1. Hold a **normal filled map** in your main hand.
2. Hold the **paintbrush in your off hand**.
3. **Sneak + right-click.** MapArtist asks you to confirm the conversion — type `/mapartist confirm` (or `/mapartist cancel` to back out).

The map is now a drawing map. Vanilla maps that haven't been converted keep their normal behaviour.

### Open the editor

- **Sneak + right-click** the drawing map while holding the paintbrush in your off hand, **or**
- hold the map in your hand and run `/mapartist draw`.

You'll get a link to the web editor. Open it in a browser, draw, and hit **Submit to Game**. Your art appears on the map in-game immediately.

### Multi-map walls

Arrange drawing maps in a neat grid of item frames, then **sneak-right-click** one with the paintbrush to detect the wall. The editor opens one large canvas spanning the whole grid and slices your submission back into the individual maps. See the wiki for building, locking, and exporting walls.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/mapartist draw` | `mapartist.use` | Open the editor for the filled map in your hand |
| `/mapartist confirm` | `mapartist.use` | Confirm converting the map you were just holding |
| `/mapartist cancel` | `mapartist.use` | Cancel a pending conversion |
| `/mapartist unlock [mapId]` | `mapartist.use` | Unlock a map you own |
| `/mapartist brush` | `mapartist.admin` | Spawn a paintbrush |
| `/mapartist give` | `mapartist.admin` | Spawn a fresh drawable map |
| `/mapartist setowner <mapId> <player>` | `mapartist.admin` | Lock a map to a player |
| `/mapartist reload` | `mapartist.admin` | Reload `config.yml` without a restart |

Aliases: `/ma`. All commands auto-complete.

## Permissions

| Node | Default | Purpose |
| --- | --- | --- |
| `mapartist.use` | everyone | Use the plugin, open the editor, own maps |
| `mapartist.admin` | operators | Brush/map spawning, ownership, reload |

## Configuration

All options live in `plugins/MapArtist/config.yml`. Highlights:

- `host` / `port` — where the web editor is served
- `rate-limit-per-minute` — drawing link generation limit
- `require-holding-to-submit` — only allow submits while the player holds the map
- `map-protection-*` — item destruction / locked-frame protections
- `log-edits` / `log-entry-limit` — edit logging
- `paintbrush` — brush base item, name, and model

A commented copy is generated on first run. The wiki has the full reference.

## Building from source

```sh
mvn clean package
```

The shaded jar is written to `target/`.

## Support

Report issues and request features on the [issue tracker](https://github.com/willits/mapartist/issues). In-depth guides live in the [wiki](https://github.com/willits/mapartist/wiki).

## License

MapArtist is released under the MIT License.
