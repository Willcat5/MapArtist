# MapArtist TODO

## High Priority

- [x] Add a paintbrush item (/mapartist brush: stick, max stack 1, custom model "brush", colored name). Required in the off hand to shift-right-click maps open and to interact with map walls/multi-maps
- [ ] Rework map creation: use a "paintbrush" item in-game to convert a normal map into a MapArtist map (replace /mapartist give)
- [ ] Add map locking: prevent editing by anyone except the lock owner
- [ ] Add multi-map canvas support in the web editor for wall-sized drawings
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
- `/mapartist reload`  Reload config without server restart
- `/mapartist info <mapId>`  Show metadata: owner, dates, pixel stats, wall membership, lock status
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
- [x] `map-protection-explosions`  Protect maps from explosion damage
- [x] `map-protection-itemdestruction`  Protect maps from fire/lava/cactus/explosions (like netherite)
- [x] `map-protection-anti-break-when-locked`  Prevent breaking frames with MapArtist maps (frame only)

### Drawings
- `save-on-edit`  Save to disk on every submit vs only on shutdown
- `max-drawings`  Hard cap on total stored drawings server-wide

### Multi-map
- `wall-protection-break`  Prevent breaking frames in a detected wall
- `wall-protection-redstone`  Prevent redstone-activated rotation in walls

### Admin
- [x] `log-edits`  Write edit events to a file (edits.log, UTC timestamps, multimap support)
- [x] `log-entry-limit`  Cap log lines, delete oldest; -1 = unlimited
- [ ] `log-path`  Where to write the log
- [ ] `admin-permission`  Permission node for admin subcommands
