# MapArtist TODO

## High Priority

- [x] Add a paintbrush item (/mapartist brush: stick, max stack 1, custom model "brush", colored name). Required in the off hand to shift-right-click maps open and to interact with map walls/multi-maps
- [x] Rework map creation: use a "paintbrush" item in-game to convert a normal map into a MapArtist map (replace /mapartist give; give is now admin-only, conversion is a chat-confirmed in-place process retaining the vanilla base)
- [x] Add map locking: the player who locks a map (via a web-editor toggle) becomes the only one who can edit it until unlocked; non-owners blocked at open and server-side on submit/import; locked cells skipped on walls; persisted to locks.tsv. Admins don't bypass the lock — instead they can reassign ownership via /mapartist setowner
- [x] Add multi-map canvas support in the web editor for wall-sized drawings (one big canvas spanning the detected grid; locked cells omitted; submit slices back into per-cell maps; wall-proximity-blocks replaces the holding check; requires north-aligned frames)
- [x] Wall locking in the editor: lock toggle locks all unowned wall cells or unlocks all cells owned by the editor; mixed state shows a striped/dashed toggle; others' cells are never touched
- [x] Wall export/import: reuses the existing Export/Import buttons; exporting a wall produces a zip of per-cell .dat files plus an arrangement.json describing the grid; importing restores the wall cells
- [x] Fix MapCopyListener.onMapInitialize hijacking all new maps (new maps now keep vanilla behaviour until opened with the paintbrush; conversion happens on brush use, incl. map walls)
- [ ] Add a README.MD with installation instructions, usage, and config options (and maybe a sick video...)

## Medium Priority

- [ ] Add map splitting: break a multi-map wall into individual carried maps
- [x] Add protections for map walls against destruction (explosion, breaking, etc.)
- [ ] Add admin tools (list, delete, reset, transfer, info, wipeall, reload, audit log)
- [x] Add new config options (rate limiting, map caps, protections, logging, etc.)
- [ ] Add HTTPS/TLS support for the web server

## Low Priority

- [ ] Clean up exception message leaks in web error responses
- [ ] Add CSRF protection to web endpoints

## Admin Tool Ideas

- `/mapartist list`  List all MapArtist maps with IDs, pixel count, last edit time, editor
- `/mapartist delete <mapId>`  Wipe a drawing map (with confirmation)
- `/mapartist reset <mapId>`  Remove MapArtist renderers, restore to vanilla terrain
- `/mapartist transfer <mapId> <player>`  Transfer map ownership
- `/mapartist wipeall`  Delete all stored drawings server-wide
- `/mapartist info <mapId>`  Show metadata: owner, dates, pixel stats, wall membership, lock status
- [x] `/mapartist reload`  Reload config without server restart (mapartist.admin)
- Map audit log — Record edits to a file for grief investigation

## Config Additions

### Web Editor
- [x] `require-holding-to-submit`  Toggle the "must be holding the map" check
- [x] `max-upload-size-megabytes`  Limit submitted image size
- [ ] `allowed-hosts`  Restrict which host values can be configured
- [x] `rate-limit-per-minute`  Limit token generation rate

### Maps
- [ ] `max-maps-per-player`  Cap maps per player
- [ ] `default-map-gamemode`  Whether new maps start locked or unlocked
- [ ] `allow-empty-maps`  Save or discard blank drawings
- [x] `map-protection-explosions`  Protect maps from explosion damage (REMOVED - unused/broken)
- [x] `map-protection-itemdestruction`  Protect maps from fire/lava/cactus/explosions (like netherite)
- [x] `map-protection-anti-break-when-locked`  Prevent breaking frames holding LOCKED maps (owner + admins bypass)

### Drawings
- `save-on-edit`  Save to disk on every submit vs only on shutdown
- `max-drawings`  Hard cap on total stored drawings server-wide

### Multi-map
- `wall-protection-break`  Prevent breaking frames in a detected wall
- `wall-protection-redstone`  Prevent redstone-activated rotation in walls

### Admin
- [x] `log-edits`  Write edit events to a file (edits.log, UTC timestamps, multimap support, lock/unlock events)
- [x] `log-entry-limit`  Cap log lines, delete oldest; -1 = unlimited
- [ ] `log-path`  Where to write the log
- [ ] `admin-permission`  Permission node for admin subcommands
