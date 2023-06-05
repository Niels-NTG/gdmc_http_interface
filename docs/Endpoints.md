[TOC]

# General

By default all endpoints are located at `localhost:9000`. So for example a call to place blocks would have the URL `http://localhost:9000/blocks?x=-82&y=1&z=40`.

An instance of Minecraft with the GDMC-HTTP mod installed needs to be active at the given domain with a world loaded for the endpoints to be available.

The JSON schemas in this page are made with the help of [quicktype.io](https://app.quicktype.io/#l=schema).

## Error codes

The following error codes can occur at any endpoint:

- `400`: "Could not parse query parameters"
- `405`: "Method not allowed"
- `500`: "Internal server error"

## Request headers

The requests headers for all endpoints are the following, unless stated otherwise.

| key          | valid values       | defaults to        | description                                                                                               |
|--------------|--------------------|--------------------|-----------------------------------------------------------------------------------------------------------|
| Content-Type | `application/json` | `application/json` | Request body content type is expected to have correct JSON formatting. Otherwise a `400` error is thrown. |

## Response headers

The responses for all endpoints return with the following headers, unless stated otherwise.

| key                         | value                             | description |
|-----------------------------|-----------------------------------|-------------|
| Access-Control-Allow-Origin | `*`                               |             |
| Content-Disposition         | `inline`                          |             |
| Content-Type                | `application/json; charset=UTF-8` |             |

# Send Commands `POST /commands`

Send one or more Minecraft console commands to the server. For the full list of all commands consult the [Minecraft commands documentation](https://minecraft.fandom.com/wiki/Commands#List_and_summary_of_commands).

## URL parameters

| key       | valid values                                          | required | defaults to | description                                                                    |
|-----------|-------------------------------------------------------|----------|-------------|--------------------------------------------------------------------------------|
| x         | integer                                               | no       | `0`         | X coordinate of command source. For commands that work with relative position. |
| y         | integer                                               | no       | `0`         | Y coordinate of command source. For commands that work with relative position. |
| z         | integer                                               | no       | `0`         | Z coordinate of command source. For commands that work with relative position. |
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Sets in which dimension of the world the commands will be executed in.         |

## Request headers

| key                         | value                       | description |
|-----------------------------|-----------------------------|-------------|
| Content-Type                | `text/plain; charset=UTF-8` |             |

## Request body

The request body should be formatted as plain-text and can contain multiple commands at the time, each on a new line.

## Response headers

[Default](#Response-headers)

## Response body

A JSON array with an entry on the result of each command.

## Example

When posting following body to `POST /command`

```
say start
tp @p 0 70 0
setblock 0 69 0 stone
fill -8 68 -8 8 68 8 oak_planks replace
say end
```

each command will be executed line by line in the context of the overworld dimension. When complete a response is returned with return values for each command on separate lines. A return value can either be an integer or an error message. For example the request above might return:

```json
[
	{
		"status": 1
	},
	{
		"status": 1
	},
	{
		"status": 1
	},
	{
		"status": 1,
		"message": "289"
	},
	{
		"status": 1
	}
]
```

And on a subsequent call, two of the commands will fail, so the return text will be:

```json
[
	{
		"status": 1
	},
	{
		"status": 1
	},
	{
		"status": 0,
		"message": "Could not set the block"
	},
	{
		"status": 0,
		"message": "No blocks were filled"
	},
	{
		"status": 1
	}
]
```

# Read blocks `GET /blocks`

Get information for one or more blocks in a given area.

## URL parameters

| key             | valid values                                          | required | defaults to | description                                                                                                            |
|-----------------|-------------------------------------------------------|----------|-------------|------------------------------------------------------------------------------------------------------------------------|
| x               | integer                                               | yes      | `0`         | X coordinate                                                                                                           |
| y               | integer                                               | yes      | `0`         | Y coordinate                                                                                                           |
| z               | integer                                               | yes      | `0`         | Z coordinate                                                                                                           |
| dx              | integer                                               | no       | `1`         | Range of blocks to get counting from x (can be negative)                                                               |
| dy              | integer                                               | no       | `1`         | Range of blocks to get counting from y (can be negative)                                                               |
| dz              | integer                                               | no       | `1`         | Range of blocks to get counting from z (can be negative)                                                               |
| includeState    | `true`, `false`                                       | no       | `false`     | If `true`, include [block state](https://minecraft.fandom.com/wiki/Block_states) in response                           |
| includeData     | `true`, `false`                                       | no       | `false`     | If `true`, include [block entity data](https://minecraft.fandom.com/wiki/Chunk_format#Block_entity_format) in response |
| withinBuildArea | `true`, `false`                                       | no       | `false`     | If `true`, skip over positions that are outside the build area                                                         |
| dimension       | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to read blocks from                                                                       |

## Request headers

[Default](#Request-headers)

## Request body

N/A

## Response headers

[Default](#Response-headers)

## Response body

Response body follows this [schema](./schema.blocks.get.json).

## Example

To get a the block at position x=28, y=67 and z=-73, request `GET /blocks?x=5525&y=62&z=4381`, which could return:

```json
[
  {
	"id": "minecraft:grass_block",
	"x": 5525,
	"y": 62,
	"z": 4381
  }
]
```

To get all block within a 2x2x2 area, request `GET /blocks?x=5525&y=62&z=4381&dx=2&dy=2&dz=2`, which returns a list with each block on a seperate line:

```json
[
	{
		"id": "minecraft:grass_block",
		"x": 5525,
		"y": 62,
		"z": 4381
	},
	{
		"id": "minecraft:dirt",
		"x": 5525,
		"y": 62,
		"z": 4382
	},
	{
		"id": "minecraft:air",
		"x": 5525,
		"y": 63,
		"z": 4381
	},
	{
		"id": "minecraft:birch_log",
		"x": 5525,
		"y": 63,
		"z": 4382
	},
	{
		"id": "minecraft:grass_block",
		"x": 5526,
		"y": 62,
		"z": 4381
	},
	{
		"id": "minecraft:grass_block",
		"x": 5526,
		"y": 62,
		"z": 4382
	},
	{
		"id": "minecraft:air",
		"x": 5526,
		"y": 63,
		"z": 4381
	},
	{
		"id": "minecraft:air",
		"x": 5526,
		"y": 63,
		"z": 4382
	}
]
```

To include the [block state](https://minecraft.fandom.com/wiki/Block_states), request `GET /blocks?x=5525&y=64&z=4382&includeState=true`:

```json
[
	{
		"id": "minecraft:birch_log",
		"x": 5525,
		"y": 64,
		"z": 4382,
		"state": {
			"axis": "y"
		}
	}
]
```

To get information such as the contents of a chest, use `includeData=true` as part of the request; `GET /blocks?x=-300y=66&z=26&includeState=true&includeData=true`:

```json
[
  {
	"id": "minecraft:chest",
	"x": -300,
	"y": 66,
	"z": 26,
	"state": {
	  "facing": "west",
	  "type": "single",
	  "waterlogged": "false"
	},
	"data": "{Items:[{Count:1b,Slot:0b,id:\"minecraft:flint_and_steel\",tag:{Damage:0}},{Count:3b,Slot:2b,id:\"minecraft:lantern\"},{Count:7b,Slot:4b,id:\"minecraft:dandelion\"}]}"
  }
]
```
Note that that block data such as the contents of a chest are formatted as an [SNBT string](https://minecraft.fandom.com/wiki/NBT_format#SNBT_format).

# Place blocks `PUT /blocks`

Place one or more blocks into the world.

## URL parameters

| key             | valid values                                          | required | defaults to | description                                                                                                                                            |
|-----------------|-------------------------------------------------------|----------|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| x               | integer                                               | yes      | `0`         | X coordinate                                                                                                                                           |
| y               | integer                                               | yes      | `0`         | Y coordinate                                                                                                                                           |
| z               | integer                                               | yes      | `0`         | Z coordinate                                                                                                                                           |
| doBlockUpdates  | `true`, `false`                                       | no       | `true`      | If `true`, tell neighbouring blocks to reach to placement, see [Controlling block update behavior](#controlling-block-update-behavior).                |
| spawnDrops      | `true`, `false`                                       | no       | `false`     | If `true`, drop items if existing blocks are destroyed by this placement, see [Controlling block update behavior](#controlling-block-update-behavior). |
| customFlags     | bit string                                            | no       | `0100011`   | Force certain behaviour when placing blocks, see [Controlling block update behavior](#controlling-block-update-behavior).                              |
| withinBuildArea | `true`, `false`                                       | no       | `false`     | If `true`, do not place blocks at positions outside the build area.                                                                                    |
| dimension       | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to place blocks in                                                                                                        |

### Controlling block update behavior

In Minecraft destroying or placing a block will cause a 'block update'. A block update tells neighboring blocks to react to the change. An example would be for water to flow in to the newly created space, a torch to pop off after the block it was on has been destroyed or a fence to connect to a newly placed fence post. If for performance or stability reasons you want to avoid block updates you can set the query parameter `doBlockUpdates` to false (`PUT /blocks?x=10&y=64&z=-87&doBlockUpdates=false`). But be warned, this can cause cosmetic issues such as fences not connecting automatically!

By default blocks placed through the interface will not cause any items to be dropped. In the example of the torch, if a block is destroyed, and attached torch will be destroyed too, but not drop as an item. If for some reason you do want items to drop you can set the `spawnDrops` query parameter to true (`PUT /blocks?x=10&y=64&z=-87&spawnDrops=true`).

Both of these query parameters set certain 'block update flags' internally. If you know what you are doing you can also set the block update behavior manually. But be careful, because this can cause glitchy behavior! You can set the block update flags with the query parameter `customFlags`. It is a bit string consisting of 7 bits and it will override the behavior set by `doBlockUpdates` and `spawnDrops`. For example `PUT /blocks?x=10&y=64&z=-87&customFlags=0000010`.

The flags are as follows:

| bit string | effect                                                                       |
|------------|------------------------------------------------------------------------------|
| `0000001`  | will cause a block update.                                                   |
| `0000010`  | will send the change to clients.                                             |
| `0000100`  | will prevent the block from being re-rendered.                               |
| `0001000`  | will force any re-renders to run on the main thread instead                  |
| `0010000`  | will prevent neighbour reactions (e.g. fences connecting, observers pulsing) |
| `0100000`  | will prevent neighbour reactions from spawning drops.                        |
| `1000000`  | will signify the block is being moved.                                       |

You can combine these flags as you wish, for example 0100011 will cause a block update _and_ send the change to clients _and_ prevent neighbor reactions. You should always have the `0000010` flag active, otherwise you will get invisible block glitches.

The following list shows which block update flags `doBlockUpdates` and `spawnDrops` get evaluated to internally:

```
doBlockUpdates=False, spawnDrops=False -> 0110010
doBlockUpdates=False, spawnDrops=True  -> 0110010
doBlockUpdates=True,  spawnDrops=False -> 0100011    (default behavior)
doBlockUpdates=True,  spawnDrops=True  -> 0000011
```

## Request headers

[Default](#Request-headers)

## Request body

Request body should be a single JSON array of JSON objects according to this [schema](./schema.blocks.put.json).

After receiving the request, GDMC-HTTP will first to attempt to parse the whole request body into valid JSON. If this fails it will return a response with HTTP status `400`.

## Response headers

[Default](#Response-headers)

## Response body

Returns a status for each block placement instruction given in the request body. The order of these corresponds to the order the placement instruction was listed.

## Example

We can place a chest containing a few items and a quartz block next to it by sending the following request body to `PUT /blocks?x=-43&y=2&z=23`:

```json
[
  {
	"id": "minecraft:chest",
	"x": -55,
	"y": "~2",
	"z": 77,
	"state": {
	  "facing": "east",
	  "type": "single",
	  "waterlogged": "false"
	},
	"data": "{Items:[{Count:48b,Slot:0b,id:\"minecraft:lantern\"},{Count:1b,Slot:1b,id:\"minecraft:golden_axe\",tag:{Damage:0}}]}"
  },
  {
	"id": "minecraft:quartz_block",
	"x": -56,
	"y": "~2",
	"z": 77
  }
]
```

This returns:

```json
[
  {
	"status": 1
  },
  {
	"status": 1
  }
]
```

Where each entry corresponds to a placement instruction, where `"status": 1` indicates a success, `"status": 0` that a block of that type is already there. This zero status may also appear when something else went wrong, such as when an invalid block ID was given. In such cases there also be a `"message"` attribute with an error message.

# Read biomes `GET /biomes`

Get [biome](https://minecraft.fandom.com/wiki/Biome#List_of_biomes) information in a given area.

## URL parameters

| key       | valid values                                          | required | defaults to | description                                              |
|-----------|-------------------------------------------------------|----------|-------------|----------------------------------------------------------|
| x         | integer                                               | yes      | `0`         | X coordinate                                             |
| y         | integer                                               | yes      | `0`         | Y coordinate                                             |
| z         | integer                                               | yes      | `0`         | Z coordinate                                             |
| dx        | integer                                               | no       | `1`         | Range of blocks to get counting from x (can be negative) |
| dy        | integer                                               | no       | `1`         | Range of blocks to get counting from y (can be negative) |
| dz        | integer                                               | no       | `1`         | Range of blocks to get counting from z (can be negative) |
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to read blocks from         |

## Request headers

[Default](#Request-headers)

## Request body

N/A

## Response headers

[Default](#Response-headers)

## Response body

The response should follow this [schema](./schema.biomes.get.json).

## Example

For getting the biomes of a row of blocks, request `GET /biomes?x=2350&y=64&z=-77&dx=-6`:

```json
[
	{
		"id": "minecraft:river",
		"x": 2344,
		"y": 64,
		"z": -77
	},
	{
		"id": "minecraft:river",
		"x": 2345,
		"y": 64,
		"z": -77
	},
	{
		"id": "minecraft:river",
		"x": 2346,
		"y": 64,
		"z": -77
	},
	{
		"id": "minecraft:river",
		"x": 2347,
		"y": 64,
		"z": -77
	},
	{
		"id": "minecraft:river",
		"x": 2348,
		"y": 64,
		"z": -77
	},
	{
		"id": "minecraft:forest",
		"x": 2349,
		"y": 64,
		"z": -77
	}
]
```

# Read chunk data `GET /chunks`

Read [chunks](https://minecraft.fandom.com/wiki/Chunk) within a given range and return it as [chunk data](https://minecraft.fandom.com/wiki/Chunk_format).

## URL parameters

| key       | valid values                                          | required | defaults to | description                                                            |
|-----------|-------------------------------------------------------|----------|-------------|------------------------------------------------------------------------|
| x         | integer                                               | yes      | `0`         | X chunk coordinate                                                     |
| z         | integer                                               | yes      | `0`         | Z chunk coordinate                                                     |
| dx        | integer                                               | no       | `1`         | Range of chunks (not blocks!) to get counting from x (can be negative) |
| dz        | integer                                               | no       | `1`         | Range of chunks (not blocks!) to get counting from z (can be negative) |
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to read chunks from                       |

## Request headers

| key             | valid values                             | defaults to                | description                                                                                                                                                                                                                                               |
|-----------------|------------------------------------------|----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Accept          | `text/plain`, `application/octet-stream` | `application/octet-stream` | Response data type. By default returns as raw bytes of a [NBT](https://minecraft.fandom.com/wiki/NBT_format) file. Use `text/plain` for the same data, but in the human-readable [SNBT](https://minecraft.fandom.com/wiki/NBT_format#SNBT_format) format. |
| Accept-Encoding | `gzip`, `*`                              | `*`                        | If set to `gzip`, any raw bytes NBT file is compressed using GZIP.                                                                                                                                                                                        |

## Request body

N/A

## Response headers

Table below only applies if the request header `Accept: application/octet-stream` is present. Otherwise it returns with the [Default](#Response-headers) response headers.

| key                         | value                      | description                                                                                   |
|-----------------------------|----------------------------|-----------------------------------------------------------------------------------------------|
| Access-Control-Allow-Origin | `*`                        |                                                                                               |
| Content-Disposition         | `attachment`               | Allows some clients automatically treat the output as a file instead of displaying it inline. |
| Content-Type                | `application/octet-stream` |                                                                                               |
| Content-Encoding            | `gzip`                     | Only if same `Accept-Encoding: gzip` was present in the request header.                       |

## Response body

Response should be encoded as an [NBT](https://minecraft.fandom.com/wiki/NBT_format), [SNBT](https://minecraft.fandom.com/wiki/NBT_format#SNBT_format) or JSON-formatted data structure depending what value has been set for `Accept` in the request header. The data always contains the following properties:

- `ChunkX`: Same value as URL parameter x
- `ChunkZ`: Same value as URL parameter z
- `ChunkDX`: Same value as URL parameter dx
- `ChunkDZ`: Same value as URL parameter dz
- `Chunks`: List of chunks, where each chunk is in the [NBT Chunk format](https://minecraft.fandom.com/wiki/Chunk_format#NBT_structure) encoded as raw NBT or SNBT.

## Example

Get a single chunk at position x=0, z=8 in the Nether with the request `GET /chunks?x=0&z=8&dimension=nether` with the header `Accept: text/plain` to get something that is human-readable:

```
{ChunkDX:1,ChunkDZ:1,ChunkX:0,ChunkZ:8,Chunks:[{DataVersion:3120,Heightmaps:{MOTION_BLOCKING:[L;2310355422147575936L,2310355422147575936L,2310355422147575936L,2310355422147575936L, ...
```

# Place NBT structure file `POST /structure`

Place an [NBT](https://minecraft.fandom.com/wiki/NBT_format) structure file into the world. These files can be created by the [Structure Block](https://minecraft.fandom.com/wiki/Structure_Block) as well as other means.

## URL parameters
| key            | valid values                                          | required | defaults to | description                                                                                                                              |
|----------------|-------------------------------------------------------|----------|-------------|------------------------------------------------------------------------------------------------------------------------------------------|
| x              | integer                                               | yes      | `0`         | X coordinate                                                                                                                             |
| y              | integer                                               | yes      | `0`         | Y coordinate                                                                                                                             |
| z              | integer                                               | yes      | `0`         | Z coordinate                                                                                                                             |
| mirror         | `x`, `y`                                              | no       | `0`         | `x` = mirror structure front to back; `y` = mirror structure left to right                                                               |
| rotate         | `0`, `1`, `2`, `3`                                    | no       | `0`         | `0` = apply no rotation; `1` = rotate structure 90° clockwise; `2` = rotate structure 180°; `3` = rotate structure 90° counter-clockwise |
| pivotX         | integer                                               | no       | `0`         | relative X coordinate to use as pivot for rotation                                                                                       |
| pivotZ         | integer                                               | no       | `0`         | relative Z coordinate to use as pivot for rotation                                                                                       |
| entities       | `true`, `false`                                       | no       | `false`     | `true` = also place all [entities](https://minecraft.fandom.com/wiki/Entity) (mobs, villagers, etc.) saved with the file                 |
| doBlockUpdates | `true`, `false`                                       | no       | `true`      | See doBlockUpdates in [`PUT /blocks` URL parameters](#url-parameters-2)                                                                  |
| spawnDrops     | `true`, `false`                                       | no       | `false`     | See spawnBlocks in [`PUT /blocks` URL parameters](#url-parameters-2)                                                                     |
| customFlags    | bit string                                            | no       | `0100011`   | See customFlags in [`PUT /blocks` block placement flags](#controlling-block-update-behavior)                                             |
| dimension      | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Sets in which dimension of the world to place the structure in                                                                           |

Note that the _mirror_ transformation is applied first, the _rotation_ second. And the pivot point applies to both.

## Request headers

| key              | valid values                     | defaults to  | description                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
|------------------|----------------------------------|--------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Content-Encoding | `gzip`, `*`                      | `gzip`       | If set to `gzip`, input NBT file is assumed to be compressed using GZIP. This is enabled by default since files generated by the [Structure Block](https://minecraft.fandom.com/wiki/Structure_Block) are compressed this way. Setting this header to `*` will make GDMC-HTTP attempt to parse the file as both a compressed and uncompressed file (in that order) and continue with the one that is valid, ideal for when it's unclear if the file is compressed or not. |

## Request body

A valid [NBT file](https://minecraft.fandom.com/wiki/NBT_format).

## Response headers

[Default](#Response-headers)

## Response body

Contains a single `{ "status": 1 }` if the placement was successful or a `{ "status": 0 }` if not.

## Example

Using the [Structure Block](https://minecraft.fandom.com/wiki/Structure_Block), [save](https://minecraft.fandom.com/wiki/Structure_Block#Save) an area of any Minecraft world. Give it a name such as "example:test-structure" and set the Include entities setting to "ON", then hit the "SAVE" button. You will now be able to find the file under `(minecraftFiles)/saves/(worldName)/generated/example/test-structure.nbt`.

Now in Minecraft load the Minecraft world you want to place this structure in, pick a location and place it there using this endpoint. To place the it at location x=102, y=67, z=-21 with entities, include the file as the request body to request `POST /structure?x=102&y=67&z=-21&entities=true`.

# Create NBT structure file `GET /structure`

Create an [NBT](https://minecraft.fandom.com/wiki/NBT_format) structure file from an area of the world.

## URL parameters

| key       | valid values                                          | required | defaults to | description                                                                                                           |
|-----------|-------------------------------------------------------|----------|-------------|-----------------------------------------------------------------------------------------------------------------------|
| x         | integer                                               | yes      | `0`         | X coordinate                                                                                                          |
| y         | integer                                               | yes      | `0`         | Y coordinate                                                                                                          |
| z         | integer                                               | yes      | `0`         | Z coordinate                                                                                                          |
| dx        | integer                                               | no       | `1`         | Range of blocks to get counting from x (can be negative)                                                              |
| dy        | integer                                               | no       | `1`         | Range of blocks to get counting from y (can be negative)                                                              |
| dz        | integer                                               | no       | `1`         | Range of blocks to get counting from z (can be negative)                                                              |
| entities  | `true`, `false`                                       | no       | `false`     | `true` = also save all [entities](https://minecraft.fandom.com/wiki/Entity) (mobs, villagers, etc.) in the given area |
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | overworld   | Which dimension of the world to read blocks from                                                                      |

## Request headers

| key             | valid values                             | defaults to                | description                                                                                                                                                                                                                             |
|-----------------|------------------------------------------|----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Accept          | `text/plain`, `application/octet-stream` | `application/octet-stream` | Response data type. By default returns the contents that makes a real NBT file. Use `text/plain` for a more human readable lossless version of the data in the [SNBT](https://minecraft.fandom.com/wiki/NBT_format#SNBT_format) format. |
| Accept-Encoding | `gzip`, `*`                              | `gzip`                     | If set to `gzip`, compress resulting file using gzip compression.                                                                                                                                                                       |

## Request body

N/A

## Response headers

Table below only applies if the request header `Accept: application/octet-stream` is present. Otherwise it returns with the default headers described [here](#response-headers).

| key                         | value                      | description                                                                               |
|-----------------------------|----------------------------|-------------------------------------------------------------------------------------------|
| Access-Control-Allow-Origin | `*`                        |                                                                                           |
| Content-Disposition         | `attachment`               | Allows some clients automatically treat output as a file instead of displaying it inline. |
| Content-Type                | `application/octet-stream` |                                                                                           |
| Content-Encoding            | `gzip`                     | Only if request header has `Accept-Encoding: gzip`.                                       |

## Response body

An [NBT file](https://minecraft.fandom.com/wiki/NBT_format) the selected area of the world.

## Example

The request `GET /structure?x=87&y=178&z=247&dx=10&dy=10&dz=10&dimension=nether` gets us a 10x10x10 area from The Nether. Entities such as mobs in that cube-shaped area are not included, since the request does not have the `entities=true` parameter. Leaving the request headers to its defaults this yields a gzip-compressed binary-encoded NBT data that we can save to a file, manipulate using an external tool, and place back into the world using the [Structure Block](https://minecraft.fandom.com/wiki/Structure_Block) or the `POST /structure` endpoint.

# Read entities `GET /entities`

Endpoint for reading all [entities](https://minecraft.fandom.com/wiki/Entity) from within a certain area of the world.

## URL parameters

| key         | valid values                                          | required | defaults to                      | description                                                                                                     |
|-------------|-------------------------------------------------------|----------|----------------------------------|-----------------------------------------------------------------------------------------------------------------|
| x           | integer                                               | no       | `0`                              | X coordinate (**deprecated**, use selector instead)                                                             |
| y           | integer                                               | no       | `0`                              | Y coordinate (**deprecated**, use selector instead)                                                             |
| z           | integer                                               | no       | `0`                              | Z coordinate (**deprecated**, use selector instead)                                                             |
| dx          | integer                                               | no       | `1`                              | Range of blocks to get counting from x (can be negative) (**deprecated**, use selector instead)                 |
| dy          | integer                                               | no       | `1`                              | Range of blocks to get counting from y (can be negative) (**deprecated**, use selector instead)                 |
| dz          | integer                                               | no       | `1`                              | Range of blocks to get counting from z (can be negative) (**deprecated**, use selector instead)                 |
| selector    | target selector string                                | no       | `@e[x=0,y=0,z=0,xd=1,yd=1,dz=1]` | [Target selector](https://minecraft.fandom.com/wiki/Target_selectors) string for entities. Must be URL-encoded. |
| includeData | `true`, `false`                                       | no       | `false`                          | If `true`, include [entity data](https://minecraft.fandom.com/wiki/Entity_format#Entity_Format) in response     |
| dimension   | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld`                      | Which dimension of the world to read entities from.                                                             |

## Request headers

[Default](#Request-headers)

## Request body

N/A

## Response headers

[Default](#Response-headers)

## Response body

The response follows this [schema](./schema.entities.get.json).

## Example

Given a pit with 3 cats in it, the request `GET /entities?x=305&y=65&z=26&dx=10&dy=10&dz=10&includeData=true` may return:
```json
[
  {
	"uuid": "26a2bf9a-9dbf-492a-910b-516f4322f3f2",
	"data": "{AbsorptionAmount:0.0f,Age:0,Air:300s,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.08d,Name:\"forge:entity_gravity\"},{Base:40.0d,Modifiers:[{Amount:-0.0076567387992512986d,Name:\"Random spawn bonus\",Operation:1,UUID:[I;868537497,-1023129007,-1268290039,-433935503]}],Name:\"minecraft:generic.follow_range\"},{Base:25.0d,Name:\"minecraft:generic.max_health\"},{Base:0.0d,Name:\"forge:step_height_addition\"},{Base:0.17499999701976776d,Name:\"minecraft:generic.movement_speed\"}],Brain:{memories:{}},Bred:0b,CanPickUpLoot:0b,CanUpdate:1b,ChestedHorse:0b,DeathTime:0s,DespawnDelay:39171,EatingHaystack:0b,FallDistance:0.0f,FallFlying:0b,Fire:-1s,ForcedAge:0,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:25.0f,HurtByTimestamp:0,HurtTime:0s,InLove:0,Invulnerable:0b,LeftHanded:0b,Motion:[0.0d,-0.0784000015258789d,0.0d],OnGround:1b,PersistenceRequired:0b,PortalCooldown:0,Pos:[-296.3192426279384d,67.0d,35.572736569528644d],Rotation:[91.85614f,0.0f],Strength:5,Tame:0b,Temper:0,UUID:[I;648200090,-1648408278,-1861529233,1126364146],Variant:2,id:\"minecraft:trader_llama\"}"
  },
  {
	"uuid": "58c392b0-9eee-4174-a807-3b975a2369f4",
	"data": "{AbsorptionAmount:0.0f,Age:0,Air:300s,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.08d,Name:\"forge:entity_gravity\"},{Base:16.0d,Modifiers:[{Amount:0.026007290323946414d,Name:\"Random spawn bonus\",Operation:1,UUID:[I;110803122,-1164752996,-1083557595,-449135232]}],Name:\"minecraft:generic.follow_range\"},{Base:0.0d,Name:\"forge:step_height_addition\"},{Base:0.699999988079071d,Name:\"minecraft:generic.movement_speed\"}],Brain:{memories:{}},CanPickUpLoot:0b,CanUpdate:1b,DeathTime:0s,DespawnDelay:39172,FallDistance:0.0f,FallFlying:0b,Fire:-1s,ForcedAge:0,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:20.0f,HurtByTimestamp:0,HurtTime:0s,Inventory:[],Invulnerable:0b,LeftHanded:0b,Motion:[0.0d,-0.0784000015258789d,0.0d],Offers:{Recipes:[{buy:{Count:1b,id:\"minecraft:emerald\"},buyB:{Count:1b,id:\"minecraft:air\"},demand:0,maxUses:5,priceMultiplier:0.05f,rewardExp:1b,sell:{Count:2b,id:\"minecraft:small_dripleaf\"},specialPrice:0,uses:0,xp:1},{buy:{Count:5b,id:\"minecraft:emerald\"},buyB:{Count:1b,id:\"minecraft:air\"},demand:0,maxUses:8,priceMultiplier:0.05f,rewardExp:1b,sell:{Count:1b,id:\"minecraft:birch_sapling\"},specialPrice:0,uses:0,xp:1},{buy:{Count:5b,id:\"minecraft:emerald\"},buyB:{Count:1b,id:\"minecraft:air\"},demand:0,maxUses:8,priceMultiplier:0.05f,rewardExp:1b,sell:{Count:1b,id:\"minecraft:jungle_sapling\"},specialPrice:0,uses:0,xp:1},{buy:{Count:1b,id:\"minecraft:emerald\"},buyB:{Count:1b,id:\"minecraft:air\"},demand:0,maxUses:12,priceMultiplier:0.05f,rewardExp:1b,sell:{Count:1b,id:\"minecraft:red_tulip\"},specialPrice:0,uses:0,xp:1},{buy:{Count:1b,id:\"minecraft:emerald\"},buyB:{Count:1b,id:\"minecraft:air\"},demand:0,maxUses:5,priceMultiplier:0.05f,rewardExp:1b,sell:{Count:2b,id:\"minecraft:moss_block\"},specialPrice:0,uses:0,xp:1},{buy:{Count:6b,id:\"minecraft:emerald\"},buyB:{Count:1b,id:\"minecraft:air\"},demand:0,maxUses:6,priceMultiplier:0.05f,rewardExp:1b,sell:{Count:1b,id:\"minecraft:blue_ice\"},specialPrice:0,uses:0,xp:1}]},OnGround:1b,PersistenceRequired:0b,PortalCooldown:0,Pos:[-302.76158910022104d,66.0d,35.324351502361225d],Rotation:[124.32312f,0.0f],UUID:[I;1489212080,-1628552844,-1475921001,1512270324],id:\"minecraft:wandering_trader\"}"
  }
]
```
This area happens to contain a wandering trader and their trusty lama.

For a pen of various different farm animals of the size of 10 blocks wide and 10 blocks deep, using `@e[type=sheep]` as part of the request will return 2 sheep in this area. `GET /entities?includeData=true&selector=%40e%5Btype%3Dsheep,x%3D-20,y%3D0,z%3D-21,dx%3D10,dy%3D10,dz%3D10%5D`:
```json
[
	{
		"uuid": "8dd55c24-6474-409c-8e20-03162bca51a3",
		"data": "{AbsorptionAmount:0.0f,Age:0,Air:300s,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.23000000417232513d,Name:\"minecraft:generic.movement_speed\"},{Base:0.08d,Name:\"forge:entity_gravity\"},{Base:16.0d,Modifiers:[{Amount:-0.07929991095224685d,Name:\"Random spawn bonus\",Operation:1,UUID:[I;1067948072,-308262071,-1111740963,1312757403]},{Amount:-0.03951824343984822d,Name:\"Random spawn bonus\",Operation:1,UUID:[I;740224470,1022511074,-1558286765,872046470]}],Name:\"minecraft:generic.follow_range\"},{Base:0.0d,Name:\"minecraft:generic.armor_toughness\"},{Base:0.0d,Name:\"forge:step_height_addition\"},{Base:0.0d,Name:\"minecraft:generic.attack_knockback\"},{Base:8.0d,Name:\"minecraft:generic.max_health\"},{Base:0.0d,Name:\"minecraft:generic.knockback_resistance\"},{Base:0.0d,Name:\"minecraft:generic.armor\"}],Brain:{memories:{}},CanPickUpLoot:0b,CanUpdate:1b,Color:0b,DeathTime:0s,FallDistance:0.0f,FallFlying:0b,Fire:-1s,ForcedAge:0,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:8.0f,HurtByTimestamp:0,HurtTime:0s,InLove:0,Invulnerable:0b,LeftHanded:0b,Motion:[0.0d,-0.0784000015258789d,0.0d],OnGround:1b,PersistenceRequired:1b,PortalCooldown:0,Pos:[-17.443500798782093d,1.0d,-16.3957606052768d],Rotation:[4.6664124f,0.0f],Sheared:0b,UUID:[I;-1915397084,1685340316,-1910504682,734679459],id:\"minecraft:sheep\"}"
	},
	{
		"uuid": "758a4369-0df1-4784-aea8-3db04970b68c",
		"data": "{AbsorptionAmount:0.0f,Age:0,Air:300s,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.23000000417232513d,Name:\"minecraft:generic.movement_speed\"},{Base:0.08d,Name:\"forge:entity_gravity\"},{Base:16.0d,Modifiers:[{Amount:-0.054645176271711594d,Name:\"Random spawn bonus\",Operation:1,UUID:[I;452273734,1153713910,-1393383184,760955385]},{Amount:0.03129074760589258d,Name:\"Random spawn bonus\",Operation:1,UUID:[I;114520113,359677977,-1350020086,1661730340]}],Name:\"minecraft:generic.follow_range\"},{Base:0.0d,Name:\"minecraft:generic.armor_toughness\"},{Base:0.0d,Name:\"forge:step_height_addition\"},{Base:0.0d,Name:\"minecraft:generic.attack_knockback\"},{Base:8.0d,Name:\"minecraft:generic.max_health\"},{Base:0.0d,Name:\"minecraft:generic.knockback_resistance\"},{Base:0.0d,Name:\"minecraft:generic.armor\"}],Brain:{memories:{}},CanPickUpLoot:0b,CanUpdate:1b,Color:0b,DeathTime:0s,FallDistance:0.0f,FallFlying:0b,Fire:-1s,ForcedAge:0,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:8.0f,HurtByTimestamp:0,HurtTime:0s,InLove:0,Invulnerable:0b,LeftHanded:0b,Motion:[0.0d,-0.0784000015258789d,0.0d],OnGround:1b,PersistenceRequired:1b,PortalCooldown:0,Pos:[-16.02778909851362d,1.0d,-15.411003372310615d],Rotation:[249.61398f,0.0f],Sheared:0b,UUID:[I;1971995497,233916292,-1364705872,1232123532],id:\"minecraft:sheep\"}"
	}
]
```

# Create entities `PUT /entities`

Endpoint for summoning any number of [entities](https://minecraft.fandom.com/wiki/Entity) into the world such as [mobs](https://minecraft.fandom.com/wiki/Mob), [items](https://minecraft.fandom.com/wiki/Item_(entity)), [item frames](https://minecraft.fandom.com/wiki/Item_Frame), [painting](https://minecraft.fandom.com/wiki/Painting) and [projectiles](https://minecraft.fandom.com/wiki/Snowball). This endpoint has feature-parity with the [/summon command](https://minecraft.fandom.com/wiki/Commands/summon), meaning it takes the same options and has the same constraints.

## URL parameters

| key       | valid values                                          | required | defaults to | description                                       |
|-----------|-------------------------------------------------------|----------|-------------|---------------------------------------------------|
| x         | integer                                               | yes      | `0`         | X coordinate                                      |
| y         | integer                                               | yes      | `0`         | Y coordinate                                      |
| z         | integer                                               | yes      | `0`         | Z coordinate                                      |
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to place entities in |

## Request headers

[Default](#Request-headers)

## Request body

The request body should be a single JSON array of JSON objects according to this [schema](./schema.entities.put.json).

After receiving the request, GDMC-HTTP will first to attempt to parse the whole request body into valid JSON. If this fails it will return a response with HTTP status `400`.

## Response headers

[Default](#Response-headers)

## Response body

For each placement instruction in the request, it returns a list with a the entity's UUID if placement was successful or an error code if something else went wrong such as a missing or invalid entity ID or incorrectly formatted entity data.

## Example

For placing a red cat that's invulnerable and permanently on fire, reproduction of the painting *Wanderer above the Sea of Fog* and zombie into the world: `PUT /entities?x=92&y=64&z=-394` with the request body:
```json
[
	{
		"id": "minecraft:cat",
		"x": "~2",
		"y": "~",
		"z": "~-1",
		"data": "{variant:\"minecraft:red\",Invulnerable: true,HasVisualFire: true}"
	},
	{
		"id": "minecraft:painting",
		"x": "~-1",
		"y": 68,
		"z": "~2",
		"data": "{Facing:2,variant:\"wanderer\"}"
	},
	{
		"id": "minecraft:zombie",
		"x": "~1",
		"y": "~",
		"z": "~-4",
        "data": "{CanBreakDoors:true}"
	}
]
```

# Edit entities `PATCH /entities`

Endpoint for changing the properties of [entities](https://minecraft.fandom.com/wiki/Entity) that are already present in the world.

## URL parameters

| key       | valid values                                          | required | defaults to | description                                      |
|-----------|-------------------------------------------------------|----------|-------------|--------------------------------------------------|
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to edit entities in |

## Request headers

[Default](#Request-headers)

## Request body

The submitted properties need to be of the same data type as the target entity. Any property with a mismatching data type will be skipped. See the documentation on the [Entity Format](https://minecraft.fandom.com/wiki/Entity_format#Entity_Format) and entities of a specific type for an overview of properties and their data types.

The response is expected to be valid JSON. It should be a single JSON array of JSON objects according to this [schema](./schema.entities.patch.json).

After receiving the request, GDMC-HTTP will first to attempt to parse the whole request body into valid JSON. If this fails it will return a response with HTTP status `400`.

Refer to [the conversion from JSON table](https://minecraft.fandom.com/wiki/NBT_format#Conversion_from_JSON) to ensure data types of property values match that of the target entity.

## Response headers

[Default](#Response-headers)

## Response body

For each patch instruction in the request, it returns a list with a `{ "status": 1 }` if an existing entity with that UUID has been found *and* if the data has changed after the patch. `{ "status": 0 }` if no entity exists in the world with this UUID, if the patch has no effect on the existing data or if a invalid UUID or patch data has been submitted or if merging the data failed for some other reason.

## Example

When changing a black cat with UUID `"475fb218-68f1-4464-8ac5-e559afd8e00d"` (obtained using the [`GET /entities`](#read-entities-get-entities) endpoint) into a red cat: `PATCH /entities` with the request body:
```json
[
	{
		"uuid": "475fb218-68f1-4464-8ac5-e559afd8e00d",
		"data": "{variant:\"minecraft:red\"}"
	}
]
```

# Remove entities `DELETE /entities`

Endpoint for remove one or more [entities](https://minecraft.fandom.com/wiki/Entity) from the world.

## URL parameters

| key       | valid values                                          | required | defaults to | description                                          |
|-----------|-------------------------------------------------------|----------|-------------|------------------------------------------------------|
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to remove entities from |

## Request headers

[Default](#Request-headers)

## Request body

The request body is expected to be valid JSON. It should be a single JSON array of string-formatted UUIDs.

After receiving the request, GDMC-HTTP will first to attempt to parse the whole request body into valid JSON. If this fails it will return a response with HTTP status `400`.

## Response headers

[Default](#Response-headers)

## Response body

For each patch instruction in the request, it returns a list with a `{ "status": 1 }` if an existing entity with that UUID has been found *and* and is able to be removed, `{ "status": 0 }` if no entity exists in the world with this UUID and an error message if a invalid UUID.

## Example

To remove a cat with UUID `"475fb218-68f1-4464-8ac5-e559afd8e00d"` (obtained using the [`GET /entities`](#read-entities-get-entities) endpoint): `DELETE /entities` with the request body:
```json
[
    "475fb218-68f1-4464-8ac5-e559afd8e00d"
]
```

# Read players `GET /players`

Endpoint for reading all [players](https://minecraft.fandom.com/wiki/Player) from the world.

## URL parameters


| key         | valid values                                          | required | defaults to | description                                                                                                                                                                                      |
|-------------|-------------------------------------------------------|----------|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| includeData | `true`, `false`                                       | no       | `false`     | If `true`, include [player data](https://minecraft.fandom.com/wiki/Player.dat_format#NBT_structure) in response                                                                                  |
| selector    | target selector string                                | no       | `@a`        | [Target selector](https://minecraft.fandom.com/wiki/Target_selectors) string for players. Must be URL-encoded.                                                                                   |
| dimension   | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       |             | Which dimension of the world get the list of players from. This is only relevant when using positional arguments as part of the target selector query. Otherwise this parameter will be ignored. |

## Request headers

[Default](#Request-headers)

## Request body

N/A

## Response headers

[Default](#Response-headers)

## Response body

The response should follow this [schema](./schema.players.get.json).

## Example

Given a world with 1 player named "Dev" in it, request `GET /players?includeData=true`:

```json
[
  {
    "name": "Dev",
	"uuid": "380df991-f603-344c-a090-369bad2a924a",
    "data": "{AbsorptionAmount:0.0f,Air:300s,Attributes:[{Base:0.0d,Name:\"forge:step_height_addition\"},{Base:0.10000000149011612d,Name:\"minecraft:generic.movement_speed\"},{Base:0.08d,Name:\"forge:entity_gravity\"}],Brain:{memories:{}},CanUpdate:1b,DataVersion:3120,DeathTime:0s,Dimension:\"minecraft:overworld\",EnderItems:[],FallDistance:0.0f,FallFlying:0b,Fire:-20s,Health:20.0f,HurtByTimestamp:0,HurtTime:0s,Inventory:[{Count:1b,Slot:0b,id:\"minecraft:obsidian\"},{Count:1b,Slot:1b,id:\"minecraft:flint_and_steel\",tag:{Damage:0}}],Invulnerable:0b,Motion:[0.0d,0.0d,0.0d],OnGround:0b,PortalCooldown:0,Pos:[-3.483559135420974d,-58.74889429576954d,-16.579720966624766d],Rotation:[1.6493444f,24.599985f],Score:0,SelectedItemSlot:1,SleepTimer:0s,UUID:[I;940439953,-167562164,-1601161573,-1389718966],XpLevel:0,XpP:0.0f,XpSeed:-275312302,XpTotal:0,abilities:{flySpeed:0.05f,flying:1b,instabuild:1b,invulnerable:1b,mayBuild:1b,mayfly:1b,walkSpeed:0.1f},foodExhaustionLevel:0.0f,foodLevel:20,foodSaturationLevel:5.0f,foodTickTimer:0,playerGameType:1,recipeBook:{isBlastingFurnaceFilteringCraftable:0b,isBlastingFurnaceGuiOpen:0b,isFilteringCraftable:0b,isFurnaceFilteringCraftable:0b,isFurnaceGuiOpen:0b,isGuiOpen:0b,isSmokerFilteringCraftable:0b,isSmokerGuiOpen:0b,recipes:[\"minecraft:flint_and_steel\",\"minecraft:enchanting_table\"],toBeDisplayed:[\"minecraft:flint_and_steel\",\"minecraft:enchanting_table\"]},seenCredits:0b,warden_spawn_tracker:{cooldown_ticks:0,ticks_since_last_warning:8788,warning_level:0}}"
  }
]
```

# Get build area `GET /buildarea`

This returns the current specified build area. The build area can be set inside Minecraft using the `setbuildarea` command. This is just a convenience command to specify the area, it has no implications to where blocks can be placed or read on the map.

The syntax for the setbuildarea Minecraft command is `/setbuildarea xFrom yFrom zFrom xTo yTo zTo`.

## URL parameters

None

## Request headers

None

## Request body

N/A

## Response headers

| key                         | value                             | description |
|-----------------------------|-----------------------------------|-------------|
| Access-Control-Allow-Origin | `*`                               |             |
| Content-Disposition         | `inline`                          |             |
| Content-Type                | `application/json; charset=UTF-8` |             |

## Response body

A JSON response following this [schema](./schema.buildarea.get.json):

## Example

After having set the build area in game with `/setbuildarea ~ ~ ~ ~200 ~200 ~200`, requesting the build area via `GET /getbuildarea` returns:

```json
{ "xFrom": 2353, "yFrom": 63, "zFrom": -78, "xTo": 2553, "yTo": 263, "zTo": 122 }
```

# Get heightmap `GET /heightmap`

Returns the [heightmap](https://minecraft.fandom.com/wiki/Heightmap) of the set build area of a given type.

## URL parameters

| key       | valid values                                                                                                                         | required | defaults to     | description                                                                                                                                                                            |
|-----------|--------------------------------------------------------------------------------------------------------------------------------------|----------|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| type      | `WORLD_SURFACE`, `OCEAN_FLOOR`, `MOTION_BLOCKING`, `MOTION_BLOCKING_NO_LEAVES`, `MOTION_BLOCKING_NO_PLANTS`, `OCEAN_FLOOR_NO_PLANTS` | no       | `WORLD_SURFACE` | Type of heightmap to get. See [Heightmap](https://minecraft.fandom.com/wiki/Heightmap) wiki page for more information.                                                                 |
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end`                                                                                | no       | `overworld`     | Dimension of the world to get the heightmap for. Do note that heightmaps for The Nether will commonly return `128` for all positions due to there being no open sky in this dimension. |

In addition to the build-in height map types of `WORLD_SURFACE`, `OCEAN_FLOOR`, `MOTION_BLOCKING` and `MOTION_BLOCKING_NO_LEAVES`, this mod also includes the following custom height maps:
- `MOTION_BLOCKING_NO_PLANTS`
  - Same as `MOTION_BLOCKING`, except it also excludes the following blocks
    - Logs
    - Leaves
    - Bee nests
    - Mangrove roots
    - Giant mushroom blocks
    - Pumpkin blocks
    - Melon blocks
    - Moss blocks
    - Nether wart blocks
    - Cactus blocks
    - Farmland
    - Coral blocks
    - Sponges
    - Bamboo plants
    - Cobwebs
    - Sculk
- `OCEAN_FLOOR_NO_PLANTS`
  - Same as `OCEAN_FLOOR`, except it also excludes the following blocks:
    - Everything listed for `MOTION_BLOCKING_NO_PLANTS`

## Request headers

[Default](#Request-headers)

## Request body

N/A

## Response headers

[Default](#Response-headers)

## Response body

A 2D array with integer values representing the heightmap of the x-z dimensions of the build area.

## Example

After having set the build area in game with `/setbuildarea ~ ~ ~ ~20 ~20 ~20`, requesting the heightmap of that ignores water with `GET /heightmap?type=OCEAN_FLOOR` could return:

```json
[
  [68,68,66,65,65,65,72,72,72,74,74,74,71,65,65,65,65,65,68,71,71],
  [67,68,66,65,65,72,72,73,72,72,74,71,71,64,64,64,64,64,68,68,71],
  [66,67,67,65,65,72,73,74,73,72,71,71,63,63,63,63,63,63,63,68,68],
  [66,66,66,66,65,72,72,73,72,72,63,63,63,63,63,63,63,63,63,63,63],
  [65,66,65,65,65,64,72,72,72,63,63,63,63,63,63,63,63,63,63,63,63],
  [64,64,64,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63],
  [63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63],
  [63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63],
  [63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63],
  [63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63],
  [63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63],
  [63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63],
  [63,64,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63],
  [64,64,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63],
  [65,65,65,64,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63],
  [66,66,66,65,65,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63],
  [66,67,66,66,65,65,63,63,63,63,63,63,63,63,63,63,63,63,63,63,63],
  [66,67,66,66,66,65,65,63,63,63,63,63,63,63,63,63,63,63,63,63,63],
  [66,67,67,66,66,65,65,64,64,63,63,64,64,64,64,64,65,65,65,64,64],
  [66,67,67,66,72,72,72,65,64,64,64,64,65,65,65,65,66,66,66,66,66],
  [66,67,67,72,72,75,72,72,65,65,65,65,65,66,66,66,67,67,67,67,67]
]
```

# Read Minecraft version `GET /version`

Get the current version of Minecraft.

## URL parameters

None

## Request headers

None

## Request body

N/A

## Response headers

| key                         | value                       | description |
|-----------------------------|-----------------------------|-------------|
| Content-Type                | `text/plain; charset=UTF-8` |             |

## Response body

Plain-text response with the Minecraft version number.

## Example

`GET /version` returns:
```
1.19.2
```

# Read HTTP interface information `OPTIONS /`

Get the information about GDMC HTTP itself.

## URL parameters

None

## Request headers

None

## Request body

N/A

## Response headers

[Default](#Response-headers)

## Response body

JSON object containing the following:
- `minecraftVersion`
- `interfaceVersion`

## Example

```json
{
  "minecraftVersion": "1.19.2",
  "interfaceVersion": "1.1.0"
}
```
