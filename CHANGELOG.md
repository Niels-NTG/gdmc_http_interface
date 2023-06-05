# GDMC-HTTP 1.3.2 (Minecraft 1.19.2)

- NEW: Add `GET /heightmap` to get heightmap data of a given [type](https://minecraft.fandom.com/wiki/Heightmap) of the currently set build area. Thanks to [cmoyates](https://github.com/cmoyates)!
- NEW: Add custom heightmap types `MOTION_BLOCKING_NO_PLANTS` and `OCEAN_FLOOR_NO_PLANTS`.
- NEW: Add `withinBuildArea` flag to `GET /BLOCKS`. If set to true it skips over positions outside of the build area.
- NEW: Add `withinBuildArea` flag to `PUT /BLOCKS`. If set to true it does not place blocks outside of the build area.

# GDMC-HTTP 1.2.3 (Minecraft 1.19.2)

- FIX: With `PUT /blocks`, allow for changing the block entity (NBT) data of a block even if the target block matches the block state and block ID of the placement instruction. This makes it possible to do things such as changing the text on an existing sign or changing the items of an already placed chest.
- FIX: Reworked the algorithm for changing the shape of a block to fit with directly adjacent blocks (eg. fences) to be more efficient.

# GDMC-HTTP 1.2.2 (Minecraft 1.19.2)

- FIX: Ensure blocks placed via `POST /structure` always update on the client side to reflect its block entity data (eg. text on signs, pieces of armor on armor stands, etc.). Prior to this fix the data was correctly parsed, but only became visible in-game if the relevant chunks were reloaded.
- FIX: Remove support for `pivotY` URL query parameter in the `POST /structure` endpoint since it wasn't implemented in the first place. Minecraft does not support it. This is not a breaking change since unknown query parameters will be ignored by GDMC-HTTP.
- FIX: Add clarification on the transformation order of structures in the `POST /structure` endpoint to documentation. 

# GDMC-HTTP 1.2.1 (Minecraft 1.19.2)

- FIX: Issue where NBT file returned by `GET /structure` wasn't GZIP-compressed even if the `"Content-Encoding"` request header contained the word "gzip".
- FIX: Improve error handling when an empty file is submitted to `POST /structure`.

# GDMC-HTTP 1.2.0 (Minecraft 1.19.2)

- NEW: Add `GET /players` endpoint to get all players on the server. Thanks to [cmoyates](https://github.com/cmoyates)!
- NEW: Add support for [target selector](https://minecraft.fandom.com/wiki/Target_selectors) for entities in the `GET /entities` endpoint using the `selector` query parameter.
- NEW: Add support for [target selector](https://minecraft.fandom.com/wiki/Target_selectors) for players in the `GET /players` endpoint using the `selector` query parameter.
- NEW: Unset build area by entering the `/setbuildarea` command without arguments.

# GDMC-HTTP 1.1.1 (Minecraft 1.19.2)

- FIX: Undo explicitly setting HTTP interface address to the "loop back" address.

# GDMC-HTTP 1.1.0 (Minecraft 1.19.2)

- NEW: `POST /command` now accepts `x`, `y`, `z` parameters, usefull for when using commands with [relative coordinates](https://minecraft.fandom.com/wiki/Coordinates#Relative_world_coordinates) or commands such as [/locate](https://minecraft.fandom.com/wiki/Coordinates#Relative_world_coordinates).
- NEW: Add `OPTIONS /` to get version of Minecraft and version of GDMC-HTTP interface.
- NEW: Port number of the HTTP interface can be changed using the `/sethttpport <port>` Minecraft console command. This value will be saved to a config file and therefore will be persistent.
- NEW: Get port number of the HTTP interface via the `/gethttpport` Minecraft console command.
- FIX: The `/setbuildarea` console command can now also accept coordinates that aren't loaded yet.
- FIX: Explicitly set HTTP interface address to that of the "loop back" address, which is usually "localhost".

# GDMC-HTTP 1.0.0 (Minecraft 1.19.2)

- BREAKING: JSON-formatted NBT-like data is no longer supported in request bodies. Use [SNBT notation](https://minecraft.fandom.com/wiki/NBT_format#SNBT_format) instead.
- BREAKING: Properties containing NBT values in JSON responses are no longer formatted as JSON, but as [SNBT strings](https://minecraft.fandom.com/wiki/NBT_format#SNBT_format).
- BREAKING: Plain-text formatted responses have been removed in favour of JSON.
- BREAKING: Consistent error messages.
- BREAKING: Plain-text request bodies are no longer accepted (except for `POST /command`). JSON-formatted request bodies are expected instead.
- FIX: Improved performance!

# GDMC-HTTP 0.7.6 (Minecraft 1.19.2)

- FIX: `GET /biomes` now returns an empty string for the biome ID if the requested position is outside of the vertical boundaries of the world.
- FIX: Typo in error message `POST /structure` handler.

# GDMC-HTTP 0.7.5 (Minecraft 1.19.2)

- NEW: `GET /entities` for reading entities within a certain area.
- NEW: `PUT /entities` for creating any number of entities.
- NEW: `PATCH /entities` for editing existing entities in the world.
- NEW: `DELETE /entities` for removing existing entities from the world.
- NEW: Add parameters `doBlockUpdates`, `spawnDrops` and `customFlags` from the `PUT /blocks` endpoint to the `POST /structure` endpoint as well.
- FIX: Issue where output with the response header `Content-Encoding: gzip` didn't actually return gzipped response for the endpoints `GET /chunks` and `GET /structures`.
- FIX: Partial refactor for improved readability and decreased branching.

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
