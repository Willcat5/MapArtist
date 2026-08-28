# MapArtist TODO

## High Priority

- [ ] Rework map creation: use a "paintbrush" item in-game to convert a normal map into a MapArtist map (replace /mapartist give)
- [ ] Add map locking: prevent editing by anyone except the lock owner
- [ ] Add multi-map canvas support in the web editor for wall-sized drawings
- [ ] Fix MapCopyListener.onMapInitialize hijacking all new maps
- [ ] Add a README.MD with installation instructions, usage, and config options (and maybe a sick video...)

## Medium Priority

- [ ] Add map splitting: break a multi-map wall into individual carried maps
- [ ] Add protections for map walls against destruction (explosion, breaking, etc.)
- [ ] Add admin tools (list, delete, reset, transfer, info, wipeall, reload, audit log)
- [ ] Add new config options (rate limiting, map caps, protections, logging, etc.)
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
- `require-holding-to-submit`  Toggle the "must be holding the map" check
- `max-upload-size-bytes`  Limit submitted image size
- `allowed-hosts`  Restrict which host values can be configured
- `rate-limit-per-minute`  Limit token generation rate

### Maps
- `max-maps-per-player`  Cap maps per player
- `default-map-gamemode`  Whether new maps start locked or unlocked
- `allow-empty-maps`  Save or discard blank drawings
- `map-protection-explosions`  Protect maps from explosion damage
- `map-protection-fire`  Protect maps from fire/lava
- `map-protection-break`  Prevent breaking frames with MapArtist maps

### Drawings
- `save-on-edit`  Save to disk on every submit vs only on shutdown
- `max-drawings`  Hard cap on total stored drawings server-wide

### Multi-map
- `wall-protection-break`  Prevent breaking frames in a detected wall
- `wall-protection-redstone`  Prevent redstone-activated rotation in walls

### Admin
- `log-edits`  Write edit events to a file
- `log-path`  Where to write the log
- `admin-permission`  Permission node for admin subcommands
