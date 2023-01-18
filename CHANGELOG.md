# GDMC-HTTP 0.7.4 (Minecraft 1.19.2)
- FIX: Placement of multi-part blocks such as beds and doors.

# GDMC-HTTP 0.7.3 (Minecraft 1.19.2)
- NEW: Improved user-facing documentation.

# GDMC-HTTP 0.7.2 (Minecraft 1.19.2)
- FIX: Add proper exception handling for malformed JSON input at `PUT /blocks`.

# GDMC-HTTP 0.7.1 (Minecraft 1.19.2)
- FIX: Allow endpoint `PUT /blocks` to process block placement instructions without (valid) coordinates. It places it at the URL query coordinates instead.

# GDMC-HTTP 0.7.0 (Minecraft 1.19.2)
- NEW:`PUT /blocks` endpoint can now accept a JSON-formatted request body as input using the request header `"Content-Type": "application/json"`. The format is identical the response of the `GET /blocks` with the `"Accept": "application/json"` request header, with a few minor additional features:
  - For each placement instruction, `x`, `y` and `z` are optional. If omitted, it uses the coordinate set in the request's URL query as a fallback, or 0 if these aren't set.
  - For each placement instruction, `x`, `y` and `z` can be a integer ("x": `32`) or use tilde (`"x": "~32"`) or caret (`"x": "^32"`) notation in a string.
  - Block state (`"state"`) and block entity data (`"data"`) can be an JSON object, a single string or can be omitted all together.
  - When placing blocks with `PUT /blocks`, the "shapes" of directly adjacent blocks are updated, unless the `doBlockUpdates` flag is explicitly disabled. This means that placing a fence block
    directly adjacent to an existing block changes the shape of the fence
    such that it makes the appropriate connections.
- NEW:`POST /structure` can now accept both GZIP-compressed and uncompressed NBT files.
- NEW:`GET /structure` can now output GZIP-compressed (default) or uncompressed NBT files. The latter can be achieved by setting the request header `"Accept-Encoding": "*"`.
- BREAKING: JSON response format of `PUT /blocks` has changed to a simple array of strings.
- BREAKING: For `GET /chunks`, the binary format is now the default unless the `"Accept"` header is set to `"application/json"` or `"text/plain"`.
- BREAKING:`/version` now returns a 405 is any other HTTP method besides `GET` is used.
- FIX: Improvements to code readability.
- FIX: Minor improvements to performance.

# GDMC-HTTP 0.6.5 (Minecraft 1.19.2)
- NEW: Add `GET /structure` endpoint, for generating an NBT-formatted file of an area of blocks in the world.
- NEW: Add `POST /structure` endpoint, for placing an NBT-formatted file of a structure into the world.

# GDMC-HTTP 0.6.4 (Minecraft 1.19.2)
- NEW: Enable `GET /chunks` to return a JSON-formatted response when the request header `Accept` is set to `application/json`.

# GDMC-HTTP 0.6.3 (Minecraft 1.19.2)
- NEW: Allow negative values for range coordinates in all endpoints that take the `dx`, `dy` and `dz` parameters.

# GDMC-HTTP 0.6.2 (Minecraft 1.19.2)
- FIX: Fixed formatting coordinates in plain-text response for `GET /blocks` endpoint.

# GDMC-HTTP 0.6.1 (Minecraft 1.19.2)
- NEW: Add `GET /biomes` endpoint to request biome of a location in the world.
- FIX: Minor performance improvements.

# GDMC-HTTP 0.6.0 (Minecraft 1.19.2)
- NEW: `PUT /blocks` can now also process block entity data (eg. chest contents, a book on a lectern, armor on an armor stand, etc.).
- NEW: `GET /blocks` can return multiple blocks within a given area.
- NEW: Response of `GET /blocks` now always includes a block's position in front of its material.
- BREAKING: JSON response format of `GET /blocks` is now a JSON array instead of a single object.
- BREAKING: Response of `GET /blocks` now always includes a block's position in front of its material.
- BREAKING: Endpoint `OPTIONS /blocks` has been removed.
- BREAKING: Upgrade to Forge 1.19.2-43-2.0.
- FIX: Minor performance improvements.

# GDMC-HTTP 0.5.3 (Minecraft 1.19.2)
- NEW: Implement parameter for specify target dimension (overworld, nether, end, etc.) for the `/blocks`, `/command` and `/chunks` endpoints. For example `PUT /blocks?dimension=nether` places blocks in the nether instead of the overworld. If parameter is omitted it defaults to the overworld.
- NEW: Implement `OPTIONS` method for the `/blocks` endpoint.
- FIX: Relative position placement for blocks with a `~<int>` position when placing blocks with the `PUT /blocks` endpoint.

# GDMC-HTTP 0.5.2 (Minecraft 1.19.2)
- BREAKING: Renamed `GET /info` endpoint to `GET /version`.

# GDMC-HTTP 0.5.1 (Minecraft 1.19.2)
- NEW: Add `GET /info` endpoint, to get the current Minecraft version.

# GDMC-HTTP 0.5.0 (Minecraft 1.19.2)
- NEW: Compatibility with Minecraft version 1.19.2.
- BREAKING: No longer compatible with versions of Minecraft other than 1.19.2.

For older versions of GDMC-HTTP, refer to [nilsgawlik/gdmc_http_interface](https://github.com/nilsgawlik/gdmc_http_interface/releases)
