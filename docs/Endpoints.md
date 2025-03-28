# Endpoints GDMC-HTTP 1.6.0 (Minecraft 1.19.2 + 1.20.2 + 1.21.4)

[TOC]

# General

By default, all endpoints are located at `localhost:9000`. So for example a call to place blocks would have the URL `http://localhost:9000/blocks?x=-82&y=1&z=40`.

An instance of Minecraft with the GDMC-HTTP mod installed needs to be active at the given domain with a world loaded for the endpoints to be available.

The JSON schemas in this page are made with the help of [quicktype.io](https://app.quicktype.io/#l=schema).

## Error codes

The following error status codes are shared across multiple endpoints:

- `400`: "Could not parse query parameters"
  - Possible causes:
    - A required parameter is missing from the request URL
    - A parameter value cannot be parsed to the expected type

- `400`: "Malformed JSON"
  - Only relevant for requests that use the `PUT`, `POST`, `PATCH` or `DELETE` method

  - Possible causes:
    - Request body is empty
    - Request body is missing a closing brace somewhere
    - A comma is missing between properties
    - Or another syntax typo in the JSON

- `403`: "Requested area is outside of build area"
  - Error is thrown at endpoints that support the`withinBuildArea` query parameter if all the following conditions are true:
    - `withinBuildArea` is set to `true`
    - A build area is set
    - Area in the request is completely outside the build area
- `404`: "No build area is specified. Use the /setbuildarea command inside Minecraft to set a build area."
  - Error is thrown if no build area is set when a request requires it

- `405`: "Method not allowed"
  - Current endpoint does not support the method. See the methods listed in this documentation or the 405 error message to see what methods are supported.

- `408`: "Parsing of request payload took too long"
  - GDMC-HTTP could not parse the request body within a 10-minute time limit.

- `500`: "Internal server error"
  - This type of error is unintended behaviour and could be a bug in either GDMC-HTTP or Minecraft itself. Feel free to submit an [issue](https://github.com/Niels-NTG/gdmc_http_interface/issues) with steps explaining how to reproduce this error.


When an error is thrown GDMC-HTTP will return a JSON-formatted response containing the status code itself and an error message. For example:

```json
{
	"status": 400,
	"message": "Malformed JSON: Not a JSON Array: null"
}
```

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

# 📜 Send Commands `POST /commands`

Send one or more Minecraft console commands to the server. For the full list of all commands consult the [Minecraft commands documentation](https://minecraft.wiki/w/Commands#List_and_summary_of_commands).

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


A JSON array with an entry of the result of each command in the order of input. An entry contains the following properties:

- `status`: 1 meaning successful, zero meaning nothing happened
- `message`: Feedback chat message as it would appear in-game. Please note is only intended to give human-friendly debug information and is subject to the localisation setting of Minecraft.
- `data`: structured data version of the information that appears in the `message`. Please note that the structure of this data may vary depending on the type of result, even for the same command.

## Example

When posting following body to `POST /command`

```
say hi
locate structure minecraft:village_plains
fill 20 -61 42 22 -48 40 minecraft:dirt
kill @e[type=item]
give @p minecraft:acacia_button
```

each command will be executed line by line in the context of the overworld dimension. For example the request above might return:

```json
[
	{
		"status": 1,
	},
	{
		"status": 1,
		"message": "The nearest minecraft:village_plains is at [112, ~, 208] (121 blocks away)",
		"data": {
			"chat.coordinates": [
				112,
				"~",
				208
			],
			"commands.locate.structure.success": [
				"minecraft:village_plains",
				121
			]
		}
	},
	{
		"status": 1,
		"message": "Successfully filled 126 block(s)",
		"data": {
			"commands.fill.success": [
				126
			]
		}
	},
	{
		"status": 1,
		"message": "Killed 4 entities",
		"data": {
			"commands.kill.success.multiple": [
				4
			]
		}
	},
	{
		"status": 1,
		"message": "Gave 1 [Acacia Button] to Dev",
		"data": {
			"commands.give.success.single": [
				1
			]
		}
	}
]
```

# 🧱 Read blocks `GET /blocks`

Get information for one or more blocks in a given area.

## URL parameters

| key             | valid values                                          | required | defaults to | description                                                                                                   |
|-----------------|-------------------------------------------------------|----------|-------------|---------------------------------------------------------------------------------------------------------------|
| x               | integer                                               | yes      | `0`         | X coordinate                                                                                                  |
| y               | integer                                               | yes      | `0`         | Y coordinate                                                                                                  |
| z               | integer                                               | yes      | `0`         | Z coordinate                                                                                                  |
| dx              | integer                                               | no       | `1`         | Range of blocks to get counting from x (can be negative)                                                      |
| dy              | integer                                               | no       | `1`         | Range of blocks to get counting from y (can be negative)                                                      |
| dz              | integer                                               | no       | `1`         | Range of blocks to get counting from z (can be negative)                                                      |
| includeState    | `true`, `false`                                       | no       | `false`     | If `true`, include [block state](https://minecraft.wiki/w/Block_states) in response                           |
| includeData     | `true`, `false`                                       | no       | `false`     | If `true`, include [block entity data](https://minecraft.wiki/w/Chunk_format#Block_entity_format) in response |
| withinBuildArea | `true`, `false`                                       | no       | `false`     | If `true`, skip over positions that are outside the build area                                                |
| dimension       | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to read blocks from                                                              |

## Request headers

[Default](#Request-headers)

## Request body

N/A

## Response headers

[Default](#Response-headers)

## Response body

Response body follows this [schema](./schema.blocks.get.json).

## Example

To get the block at position x=28, y=67 and z=-73, request `GET /blocks?x=5525&y=62&z=4381`, which could return:

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

When requesting a position that's outside the vertical limits of the world, the block ID will always be `"minecraft:void_air"`.

To get all block within a 2x2x2 area, request `GET /blocks?x=5525&y=62&z=4381&dx=2&dy=2&dz=2`, which returns a list with each block on a separate line:

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

To include the [block state](https://minecraft.wiki/w/Block_states), request `GET /blocks?x=5525&y=64&z=4382&includeState=true`:

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
Note that that block data such as the contents of a chest are formatted as an [SNBT string](https://minecraft.wiki/w/NBT_format#SNBT_format).

# 🧱 Place blocks `PUT /blocks`

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

By default, blocks placed through the interface will not cause any items to be dropped. In the example of the torch, if a block is destroyed, and attached torch will be destroyed too, but not drop as an item. If for some reason you do want items to drop you can set the `spawnDrops` query parameter to true (`PUT /blocks?x=10&y=64&z=-87&spawnDrops=true`).

Both of these query parameters set certain 'block update flags' internally. If you know what you are doing you can also set the block update behavior manually. But be careful, because this can cause glitchy behavior! You can set the block update flags with the query parameter `customFlags`. It is a bit string consisting of 7 bits, and it will override the behavior set by `doBlockUpdates` and `spawnDrops`. For example `PUT /blocks?x=10&y=64&z=-87&customFlags=0000010`.

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

You can combine these flags as you wish, for example 0100011 will cause a block update _and_ send the change to clients _and_ prevent neighbor reactions. You should always have the `0000010` flag active, otherwise the placed blocks will remain invisible until the world is reloaded.

Note that if `doBlockUpdates=false` or the block update flag is set to `0` some other way, GDMC-HTTP will place blocks faster than if block updates were enabled.

The following list shows which block update flags `doBlockUpdates` and `spawnDrops` get evaluated internally to:

```
doBlockUpdates=False, spawnDrops=False -> 0110010
doBlockUpdates=False, spawnDrops=True  -> 0110010
doBlockUpdates=True,  spawnDrops=False -> 0100011    (default behavior)
doBlockUpdates=True,  spawnDrops=True  -> 0000011
```

## Request headers

[Default](#Request-headers)

## Request body

Request body should be a single JSON array of JSON objects according to this [schema](./schema.blocks.put.json), where each JSON object is for a single to-be-placed block.

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

# 🏜️ Read biomes `GET /biomes`

Get [biome](https://minecraft.wiki/w/Biome#List_of_biomes) information in a given area.

## URL parameters

| key             | valid values                                          | required | defaults to | description                                                    |
|-----------------|-------------------------------------------------------|----------|-------------|----------------------------------------------------------------|
| x               | integer                                               | yes      | `0`         | X coordinate                                                   |
| y               | integer                                               | yes      | `0`         | Y coordinate                                                   |
| z               | integer                                               | yes      | `0`         | Z coordinate                                                   |
| dx              | integer                                               | no       | `1`         | Range of blocks to get counting from x (can be negative)       |
| dy              | integer                                               | no       | `1`         | Range of blocks to get counting from y (can be negative)       |
| dz              | integer                                               | no       | `1`         | Range of blocks to get counting from z (can be negative)       |
| withinBuildArea | `true`, `false`                                       | no       | `false`     | If `true`, skip over positions that are outside the build area |
| dimension       | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to read blocks from               |

## Request headers

[Default](#Request-headers)

## Request body

N/A

## Response headers

[Default](#Response-headers)

## Response body

The response follows this [schema](./schema.biomes.get.json). Note that when requesting the biome at a position outside the vertical limit of the world, the biome ID is an empty string.

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

# ⛏ Read chunk data `GET /chunks`

Read [chunks](https://minecraft.wiki/w/Chunk) within a given range and return it as [chunk data](https://minecraft.wiki/w/Chunk_format).

## URL parameters

| key             | valid values                                          | required | defaults to | description                                                            |
|-----------------|-------------------------------------------------------|----------|-------------|------------------------------------------------------------------------|
| x               | integer                                               | no       | `0`         | X chunk coordinate                                                     |
| z               | integer                                               | no       | `0`         | Z chunk coordinate                                                     |
| dx              | integer                                               | no       | `1`         | Range of chunks (not blocks!) to get counting from x (can be negative) |
| dz              | integer                                               | no       | `1`         | Range of chunks (not blocks!) to get counting from z (can be negative) |
| withinBuildArea | `true`, `false`                                       | no       | `false`     | If `true` and a build area is set, skip chunks outside the build area  |
| dimension       | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to read chunks from                       |

Tip: to easily and efficiently convert from block coordinates to chunk coordinates in the client that interfaces with this endpoint, bit-shift the value of x and z by 4 places to the right. For example:
```python
def blockPosToChunkPos(x, y, z):
    return (
        x >> 4,
        z >> 4
    )
```

Note that if a build area is set and the parameters `x`, `z`, `dx` or `dz` are not, the values of these missing parameters will default to that of the build area.

## Request headers

| key             | valid values                             | defaults to                | description                                                                                                                                                                                                                             |
|-----------------|------------------------------------------|----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Accept          | `text/plain`, `application/octet-stream` | `application/octet-stream` | Response data type. By default returns as raw bytes of a [NBT](https://minecraft.wiki/w/NBT_format) file. Use `text/plain` for the same data, but in the human-readable [SNBT](https://minecraft.wiki/w/NBT_format#SNBT_format) format. |
| Accept-Encoding | `gzip`, `*`                              | `*`                        | If set to `gzip`, any raw bytes NBT file is compressed using GZIP.                                                                                                                                                                      |

## Request body

N/A

## Response headers

Table below only applies if the request header `Accept: application/octet-stream` is present. Otherwise, it returns with the [Default](#Response-headers) response headers.

| key                         | value                      | description                                                                                   |
|-----------------------------|----------------------------|-----------------------------------------------------------------------------------------------|
| Content-Disposition         | `attachment`               | Allows some clients automatically treat the output as a file instead of displaying it inline. |
| Content-Type                | `application/octet-stream` |                                                                                               |
| Content-Encoding            | `gzip`                     | Only if same `Accept-Encoding: gzip` was present in the request header.                       |

## Response body

Response should be encoded as an [NBT](https://minecraft.wiki/w/NBT_format) or [SNBT](https://minecraft.wiki/w/NBT_format#SNBT_format) data structure depending on what value has been set for `Accept` in the request header. The data always contains the following properties:

- `ChunkX`: X-coordinate of the origin chunk
- `ChunkZ`: Z-coordinate of the origin chunk
- `ChunkDX`: Size of the selection of chunks in the x-direction 
- `ChunkDZ`: Size of the selection of chunks in the z-direction
- `Chunks`: List of chunks, where each chunk is in the [NBT Chunk format](https://minecraft.wiki/w/Chunk_format#NBT_structure) encoded as raw NBT or SNBT.

## Example

Get a single chunk at position x=0, z=8 in the Nether with the request `GET /chunks?x=0&z=8&dimension=nether` with the header `Accept: text/plain` to get something that is human-readable:

```
{ChunkDX:1,ChunkDZ:1,ChunkX:0,ChunkZ:8,Chunks:[{DataVersion:4189,Heightmaps:{MOTION_BLOCKING:[L;2310355422147575936L,2310355422147575936L,2310355422147575936L,2310355422147575936L,2310355422147575936L,2310355422147575936L, …
```

# 🏗️ Create NBT structure file `GET /structure`

Create an [NBT](https://minecraft.wiki/w/NBT_format) structure file from an area of the world.

## URL parameters

| key             | valid values                                          | required | defaults to | description                                                                                                  |
|-----------------|-------------------------------------------------------|----------|-------------|--------------------------------------------------------------------------------------------------------------|
| x               | integer                                               | yes      | `0`         | X coordinate                                                                                                 |
| y               | integer                                               | yes      | `0`         | Y coordinate                                                                                                 |
| z               | integer                                               | yes      | `0`         | Z coordinate                                                                                                 |
| dx              | integer                                               | no       | `1`         | Range of blocks to get counting from x (can be negative)                                                     |
| dy              | integer                                               | no       | `1`         | Range of blocks to get counting from y (can be negative)                                                     |
| dz              | integer                                               | no       | `1`         | Range of blocks to get counting from z (can be negative)                                                     |
| withinBuildArea | `true`, `false`                                       | no       | `false`     | If `true`, skips positions that are outside the build area                                                   |
| entities        | `true`, `false`                                       | no       | `false`     | `true` = also save all [entities](https://minecraft.wiki/w/Entity) (mobs, villagers, etc.) in the given area |
| dimension       | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | overworld   | Which dimension of the world to read blocks from                                                             |

## Request headers

| key             | valid values                             | defaults to                | description                                                                                                                                                                                                                    |
|-----------------|------------------------------------------|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Accept          | `text/plain`, `application/octet-stream` | `application/octet-stream` | Response data type. By default returns the contents that makes a real NBT file. Use `text/plain` for a more human readable lossless version of the data in the [SNBT](https://minecraft.wiki/w/NBT_format#SNBT_format) format. |
| Accept-Encoding | `gzip`, `*`                              | `gzip`                     | If set to `gzip`, compress resulting file using gzip compression.                                                                                                                                                              |

## Request body

N/A

## Response headers

Table below only applies if the request header `Accept: application/octet-stream` is present. Otherwise, it returns with the default headers described [here](#response-headers).

| key                         | value                      | description                                                                               |
|-----------------------------|----------------------------|-------------------------------------------------------------------------------------------|
| Content-Disposition         | `attachment`               | Allows some clients automatically treat output as a file instead of displaying it inline. |
| Content-Type                | `application/octet-stream` |                                                                                           |
| Content-Encoding            | `gzip`                     | Only if request header has `Accept-Encoding: gzip`.                                       |

## Response body

An [NBT file](https://minecraft.wiki/w/NBT_format) the selected area of the world.

Note that the response returns a 403 error code if the `withinBuilArea` flag is `true` and the selected area is completely outside the build area.

## Example

The request `GET /structure?x=87&y=178&z=247&dx=10&dy=10&dz=10&dimension=nether` gets us a 10x10x10 area from The Nether. Entities such as mobs in that cube-shaped area are not included, since the request does not have the `entities=true` parameter. Leaving the request headers to its defaults this yields a gzip-compressed binary-encoded NBT data that we can save to a file, manipulate using an external tool, and place back into the world using the [Structure Block](https://minecraft.wiki/w/Structure_Block) or the `POST /structure` endpoint.

# 🏗️ Place NBT structure file `POST /structure`

Place an [NBT](https://minecraft.wiki/w/NBT_format) structure file into the world. These files can be created by the [Structure Block](https://minecraft.wiki/w/Structure_Block), the `GET /structure` endpoint, as well as other means.

## URL parameters

| key             | valid values                                          | required | defaults to | description                                                                                                                              |
|-----------------|-------------------------------------------------------|----------|-------------|------------------------------------------------------------------------------------------------------------------------------------------|
| x               | integer                                               | yes      | `0`         | X coordinate                                                                                                                             |
| y               | integer                                               | yes      | `0`         | Y coordinate                                                                                                                             |
| z               | integer                                               | yes      | `0`         | Z coordinate                                                                                                                             |
| mirror          | `x`, `y`                                              | no       | `0`         | `x` = mirror structure front to back; `y` = mirror structure left to right                                                               |
| rotate          | `0`, `1`, `2`, `3`                                    | no       | `0`         | `0` = apply no rotation; `1` = rotate structure 90° clockwise; `2` = rotate structure 180°; `3` = rotate structure 90° counter-clockwise |
| pivotX          | integer                                               | no       | `0`         | relative X coordinate to use as pivot for rotation                                                                                       |
| pivotZ          | integer                                               | no       | `0`         | relative Z coordinate to use as pivot for rotation                                                                                       |
| entities        | `true`, `false`                                       | no       | `false`     | `true` = also place all [entities](https://minecraft.wiki/w/Entity) (mobs, villagers, etc.) saved with the file                          |
| keepLiquids     | `true`, `false`                                       | no       | `true`      | If `false`, remove water sources already present at placement location of structure.                                                     |
| doBlockUpdates  | `true`, `false`                                       | no       | `true`      | See doBlockUpdates in [`PUT /blocks` URL parameters](#url-parameters-2)                                                                  |
| spawnDrops      | `true`, `false`                                       | no       | `false`     | See spawnBlocks in [`PUT /blocks` URL parameters](#url-parameters-2)                                                                     |
| customFlags     | bit string                                            | no       | `0100011`   | See customFlags in [`PUT /blocks` block placement flags](#controlling-block-update-behavior)                                             |
| withinBuildArea | `true`, `false`                                       | no       | `false`     | If `true` and build area is set, a structure cannot be placed (partially) outside of the build area                                      |
| dimension       | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Sets in which dimension of the world to place the structure in                                                                           |

Note that the _mirror_ transformation is applied first, the _rotation_ second. And the pivot point applies to both.

## Request headers

| key              | valid values              | defaults to | description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
|------------------|---------------------------|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Content-Encoding | `gzip`, `text/plain`, `*` | `*`         | If set to `gzip`, input NBT file is assumed to be compressed using GZIP. This is enabled by default since files generated by the [Structure Block](https://minecraft.wiki/w/Structure_Block) are compressed this way. If set to `text/plain` the input file is assumed to contain [SNBT](https://minecraft.wiki/w/NBT_format#SNBT_format)-formatted text. If the header is missing, GDMC-HTTP attempts to parse the file as both a compressed and uncompressed file binary NBT file (in that order) and continue with the one that is valid, ideal for when it's unclear if the file is compressed or not. |

## Request body

A valid [NBT file](https://minecraft.wiki/w/NBT_format) or plain [SNBT](https://minecraft.wiki/w/NBT_format#SNBT_format)-formatted text.

## Response headers

[Default](#Response-headers)

## Response body

Contains a single `{ "status": 1 }` if the placement was successful or a `{ "status": 0 }` if not.

A `400` error status is returned instead if:

- Request body is empty
- Request body could not be processed because it's not in a NBT or SNBT format

## Example

Using the [Structure Block](https://minecraft.wiki/w/Structure_Block), [save](https://minecraft.wiki/w/Structure_Block#Save) an area of any Minecraft world. Give it a name such as "example:test-structure" and set the Include entities setting to "ON", then hit the "SAVE" button. You will now be able to find the file under `(minecraftFiles)/saves/(worldName)/generated/example/test-structure.nbt`.

Now in Minecraft load the Minecraft world you want to place this structure in, pick a location and place it there using this endpoint. To place it at location x=102, y=67, z=-21 with entities, include the file as the request body to request `POST /structure?x=102&y=67&z=-21&entities=true`.

# 🐷 Read entities `GET /entities`

Endpoint for reading all [entities](https://minecraft.wiki/w/Entity) from within a certain area of the world.

## URL parameters

| key         | valid values                                          | required | defaults to                      | description                                                                                                                                                                                          |
|-------------|-------------------------------------------------------|----------|----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| x           | integer                                               | no       | `0`                              | X coordinate (**deprecated**, use selector instead)                                                                                                                                                  |
| y           | integer                                               | no       | `0`                              | Y coordinate (**deprecated**, use selector instead)                                                                                                                                                  |
| z           | integer                                               | no       | `0`                              | Z coordinate (**deprecated**, use selector instead)                                                                                                                                                  |
| dx          | integer                                               | no       | `1`                              | Range of blocks to get counting from x (can be negative) (**deprecated**, use selector instead)                                                                                                      |
| dy          | integer                                               | no       | `1`                              | Range of blocks to get counting from y (can be negative) (**deprecated**, use selector instead)                                                                                                      |
| dz          | integer                                               | no       | `1`                              | Range of blocks to get counting from z (can be negative) (**deprecated**, use selector instead)                                                                                                      |
| selector    | target selector string                                | no       | `@e[x=0,y=0,z=0,dx=1,dy=1,dz=1]` | [Target selector](https://minecraft.wiki/w/Target_selectors) string for entities. Must be URL-encoded. A `400` status code is returned if this string cannot be parsed into a valid target selector. |
| includeData | `true`, `false`                                       | no       | `false`                          | If `true`, include [entity data](https://minecraft.wiki/w/Entity_format#Entity_format) in response                                                                                                   |
| dimension   | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld`                      | Which dimension of the world to read entities from. This is only relevant when using positional arguments as part of the target selector query. Otherwise this parameter will be ignored.            |

## Request headers

[Default](#Request-headers)

## Request body

N/A

## Response headers

[Default](#Response-headers)

## Response body

The response follows this [schema](./schema.entities.get.json).

## Example

Given a pit with 3 cats in it, the request `GET /entities?x=508&y=60&z=1101&dx=10&dy=10&dz=10&includeData=true` may return:

```json
[
	{
		"uuid": "67374231-a7c8-44e6-8a57-f7a0936dbd0d",
		"data": "{AbsorptionAmount:0.0f,Age:0,Air:300s,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.30000001192092896d,Name:\"minecraft:generic.movement_speed\"},{Base:16.0d,Modifiers:[{Amount:-0.016206825723115378d,Name:\"Random spawn bonus\",Operation:1,UUID:[I;-263880262,1418674414,-1197689885,1924602639]}],Name:\"minecraft:generic.follow_range\"}],Brain:{memories:{}},CanPickUpLoot:0b,CollarColor:14b,DeathTime:0s,FallDistance:0.0f,FallFlying:0b,Fire:-1s,ForcedAge:0,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:10.0f,HurtByTimestamp:0,HurtTime:0s,InLove:0,Invulnerable:0b,LeftHanded:0b,Motion:[0.0d,-0.0784000015258789d,0.0d],OnGround:1b,PersistenceRequired:0b,PortalCooldown:0,Pos:[510.0585177116916d,62.0d,1102.7666980215095d],Rotation:[83.176056f,-40.0f],Sitting:0b,UUID:[I;1731674673,-1480047386,-1973946464,-1821524723],id:\"minecraft:cat\",variant:\"minecraft:ragdoll\"}"
	},
	{
		"uuid": "0dd18c2a-6810-4681-bfad-59594187f240",
		"data": "{AbsorptionAmount:0.0f,Age:0,Air:300s,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.30000001192092896d,Name:\"minecraft:generic.movement_speed\"},{Base:16.0d,Modifiers:[{Amount:0.03283023661792672d,Name:\"Random spawn bonus\",Operation:1,UUID:[I;1817612063,-514438433,-1778849508,-526249086]}],Name:\"minecraft:generic.follow_range\"}],Brain:{memories:{}},CanPickUpLoot:0b,CollarColor:14b,DeathTime:0s,FallDistance:0.0f,FallFlying:0b,Fire:-1s,ForcedAge:0,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:10.0f,HurtByTimestamp:0,HurtTime:0s,InLove:0,Invulnerable:0b,LeftHanded:0b,Motion:[0.0d,-0.0784000015258789d,0.0d],OnGround:1b,PersistenceRequired:0b,PortalCooldown:0,Pos:[509.4391643248058d,62.0d,1102.7072011314476d],Rotation:[194.22131f,0.0f],Sitting:0b,UUID:[I;231836714,1745897089,-1079158439,1099428416],id:\"minecraft:cat\",variant:\"minecraft:red\"}"
	},
	{
		"uuid": "7beee80f-bd69-45cd-bbcb-bdb040eaeff3",
		"data": "{AbsorptionAmount:0.0f,Age:0,Air:300s,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.30000001192092896d,Name:\"minecraft:generic.movement_speed\"},{Base:16.0d,Modifiers:[{Amount:-0.015196366574388526d,Name:\"Random spawn bonus\",Operation:1,UUID:[I;-552088596,-1634056410,-2130902507,341352410]}],Name:\"minecraft:generic.follow_range\"}],Brain:{memories:{}},CanPickUpLoot:0b,CollarColor:14b,DeathTime:0s,FallDistance:0.0f,FallFlying:0b,Fire:-1s,ForcedAge:0,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:10.0f,HurtByTimestamp:0,HurtTime:0s,InLove:0,Invulnerable:0b,LeftHanded:0b,Motion:[0.0d,-0.0784000015258789d,0.0d],OnGround:1b,PersistenceRequired:0b,PortalCooldown:0,Pos:[510.3460244828124d,62.0d,1104.0318802816023d],Rotation:[309.343f,0.0f],Sitting:0b,UUID:[I;2079254543,-1117174323,-1144275536,1089138675],id:\"minecraft:cat\",variant:\"minecraft:all_black\"}"
	}
]
```

For a pen of various different farm animals of the size of 10 blocks wide and 10 blocks deep, using `@e[type=sheep]` as part of the request will return 2 sheep in this area. `GET /entities?includeData=true&selector=%40e%5Btype%3Dsheep,x%3D-20,y%3D0,z%3D-21,dx%3D10,dy%3D10,dz%3D10%5D`:

```json
[
	{
		"uuid": "8dd55c24-6474-409c-8e20-03162bca51a3",
		"data": "{AbsorptionAmount:0.0f,Age:0,Air:300s,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.23000000417232513d,Name:\"minecraft:generic.movement_speed\"},{Base:16.0d,Modifiers:[{Amount:-0.07929991095224685d,Name:\"Random spawn bonus\",Operation:1,UUID:[I;1067948072,-308262071,-1111740963,1312757403]},{Amount:-0.03951824343984822d,Name:\"Random spawn bonus\",Operation:1,UUID:[I;740224470,1022511074,-1558286765,872046470]}],Name:\"minecraft:generic.follow_range\"},{Base:0.0d,Name:\"minecraft:generic.armor_toughness\"},{Base:0.0d,Name:\"minecraft:generic.attack_knockback\"},{Base:8.0d,Name:\"minecraft:generic.max_health\"},{Base:0.0d,Name:\"minecraft:generic.knockback_resistance\"},{Base:0.0d,Name:\"minecraft:generic.armor\"}],Brain:{memories:{}},CanPickUpLoot:0b,CanUpdate:1b,Color:0b,DeathTime:0s,FallDistance:0.0f,FallFlying:0b,Fire:-1s,ForcedAge:0,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:8.0f,HurtByTimestamp:0,HurtTime:0s,InLove:0,Invulnerable:0b,LeftHanded:0b,Motion:[0.0d,-0.0784000015258789d,0.0d],OnGround:1b,PersistenceRequired:1b,PortalCooldown:0,Pos:[-17.443500798782093d,1.0d,-16.3957606052768d],Rotation:[4.6664124f,0.0f],Sheared:0b,UUID:[I;-1915397084,1685340316,-1910504682,734679459],id:\"minecraft:sheep\"}"
	},
	{
		"uuid": "758a4369-0df1-4784-aea8-3db04970b68c",
		"data": "{AbsorptionAmount:0.0f,Age:0,Air:300s,ArmorDropChances:[0.085f,0.085f,0.085f,0.085f],ArmorItems:[{},{},{},{}],Attributes:[{Base:0.23000000417232513d,Name:\"minecraft:generic.movement_speed\"},{Base:16.0d,Modifiers:[{Amount:-0.054645176271711594d,Name:\"Random spawn bonus\",Operation:1,UUID:[I;452273734,1153713910,-1393383184,760955385]},{Amount:0.03129074760589258d,Name:\"Random spawn bonus\",Operation:1,UUID:[I;114520113,359677977,-1350020086,1661730340]}],Name:\"minecraft:generic.follow_range\"},{Base:0.0d,Name:\"minecraft:generic.armor_toughness\"},{Base:0.0d,Name:\"minecraft:generic.attack_knockback\"},{Base:8.0d,Name:\"minecraft:generic.max_health\"},{Base:0.0d,Name:\"minecraft:generic.knockback_resistance\"},{Base:0.0d,Name:\"minecraft:generic.armor\"}],Brain:{memories:{}},CanPickUpLoot:0b,CanUpdate:1b,Color:0b,DeathTime:0s,FallDistance:0.0f,FallFlying:0b,Fire:-1s,ForcedAge:0,HandDropChances:[0.085f,0.085f],HandItems:[{},{}],Health:8.0f,HurtByTimestamp:0,HurtTime:0s,InLove:0,Invulnerable:0b,LeftHanded:0b,Motion:[0.0d,-0.0784000015258789d,0.0d],OnGround:1b,PersistenceRequired:1b,PortalCooldown:0,Pos:[-16.02778909851362d,1.0d,-15.411003372310615d],Rotation:[249.61398f,0.0f],Sheared:0b,UUID:[I;1971995497,233916292,-1364705872,1232123532],id:\"minecraft:sheep\"}"
	}
]
```

# 🐷 Create entities `PUT /entities`

Endpoint for summoning any number of [entities](https://minecraft.wiki/w/Entity) into the world such as [mobs](https://minecraft.wiki/w/Mob), [items](https://minecraft.wiki/w/Item_(entity)), [item frames](https://minecraft.wiki/w/Item_Frame), [painting](https://minecraft.wiki/w/Painting) and [projectiles](https://minecraft.wiki/w/Category:Projectiles). This endpoint has feature-parity with the [/summon command](https://minecraft.wiki/w/Commands/summon), meaning it takes the same options and has the same constraints.

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

For each placement instruction in the request, it returns a list with the entity's UUID if placement was successful or an error code if something else went wrong such as a missing or invalid entity ID or incorrectly formatted entity data.

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

# 🐷 Edit entities `PATCH /entities`

Endpoint for changing the properties of [entities](https://minecraft.wiki/w/Entity) that are already present in the world.

## URL parameters

| key       | valid values                                          | required | defaults to | description                                      |
|-----------|-------------------------------------------------------|----------|-------------|--------------------------------------------------|
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to edit entities in |

## Request headers

[Default](#Request-headers)

## Request body

The submitted properties need to be of the same data type as the target entity. Any property with a mismatching data type will be skipped. See the documentation on the [Entity Format](https://minecraft.wiki/w/Entity_format#Entity_format) and entities of a specific type for an overview of properties and their data types.

The response is expected to be valid JSON. It should be a single JSON array of JSON objects according to this [schema](./schema.entities.patch.json).

After receiving the request, GDMC-HTTP will first to attempt to parse the whole request body into valid JSON. If this fails it will return a response with HTTP status `400`.

Refer to [the conversion from JSON table](https://minecraft.wiki/w/NBT_format#Conversion_from_JSON) to ensure data types of property values match that of the target entity.

## Response headers

[Default](#Response-headers)

## Response body

For each patch instruction in the request, it returns a list with a `{ "status": 1 }` if an existing entity with that UUID has been found *and* if the data has changed after the patch. `{ "status": 0 }` if no entity exists in the world with this UUID, if the patch has no effect on the existing data or if an invalid UUID or patch data has been submitted or if merging the data failed for some other reason.

## Example

When changing a black cat with UUID `"475fb218-68f1-4464-8ac5-e559afd8e00d"` (obtained using the [`GET /entities`](#-read-entities-get-entities) endpoint) into a red cat: `PATCH /entities` with the request body:
```json
[
	{
		"uuid": "475fb218-68f1-4464-8ac5-e559afd8e00d",
		"data": "{variant:\"minecraft:red\"}"
	}
]
```

# 🐷 Remove entities `DELETE /entities`

Endpoint for remove one or more [entities](https://minecraft.wiki/w/Entity) from the world.

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

For each patch instruction in the request, it returns a list with a `{ "status": 1 }` if an existing entity with that UUID has been found *and* can be removed, `{ "status": 0 }` if no entity exists in the world with this UUID and an error message if an invalid UUID.

## Example

To remove a cat with UUID `"475fb218-68f1-4464-8ac5-e559afd8e00d"` (obtained using the [`GET /entities`](#-read-entities-get-entities) endpoint): `DELETE /entities` with the request body:
```json
[
    "475fb218-68f1-4464-8ac5-e559afd8e00d"
]
```

# 👷 Read players `GET /players`

Endpoint for reading all [players](https://minecraft.wiki/w/Player) from the world.

## URL parameters


| key         | valid values                                          | required | defaults to | description                                                                                                                                                                                         |
|-------------|-------------------------------------------------------|----------|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| includeData | `true`, `false`                                       | no       | `false`     | If `true`, include [player data](https://minecraft.wiki/w/Player.dat_format#NBT_structure) in response                                                                                              |
| selector    | target selector string                                | no       | `@a`        | [Target selector](https://minecraft.wiki/w/Target_selectors) string for players. Must be URL-encoded. A `400` status code is returned if this string cannot be parsed into a valid target selector. |
| dimension   | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world get the list of players from. This is only relevant when using positional arguments as part of the target selector query. Otherwise this parameter will be ignored.    |

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
		"data": "{AbsorptionAmount:0.0f,Air:300s,Attributes:[{Base:0.10000000149011612d,Name:\"minecraft:generic.movement_speed\"}],Brain:{memories:{}},CanUpdate:1b,DataVersion:3120,DeathTime:0s,Dimension:\"minecraft:overworld\",EnderItems:[],FallDistance:0.0f,FallFlying:0b,Fire:-20s,Health:20.0f,HurtByTimestamp:0,HurtTime:0s,Inventory:[{Count:1b,Slot:0b,id:\"minecraft:obsidian\"},{Count:1b,Slot:1b,id:\"minecraft:flint_and_steel\",tag:{Damage:0}}],Invulnerable:0b,Motion:[0.0d,0.0d,0.0d],OnGround:0b,PortalCooldown:0,Pos:[-3.483559135420974d,-58.74889429576954d,-16.579720966624766d],Rotation:[1.6493444f,24.599985f],Score:0,SelectedItemSlot:1,SleepTimer:0s,UUID:[I;940439953,-167562164,-1601161573,-1389718966],XpLevel:0,XpP:0.0f,XpSeed:-275312302,XpTotal:0,abilities:{flySpeed:0.05f,flying:1b,instabuild:1b,invulnerable:1b,mayBuild:1b,mayfly:1b,walkSpeed:0.1f},foodExhaustionLevel:0.0f,foodLevel:20,foodSaturationLevel:5.0f,foodTickTimer:0,playerGameType:1,recipeBook:{isBlastingFurnaceFilteringCraftable:0b,isBlastingFurnaceGuiOpen:0b,isFilteringCraftable:0b,isFurnaceFilteringCraftable:0b,isFurnaceGuiOpen:0b,isGuiOpen:0b,isSmokerFilteringCraftable:0b,isSmokerGuiOpen:0b,recipes:[\"minecraft:flint_and_steel\",\"minecraft:enchanting_table\"],toBeDisplayed:[\"minecraft:flint_and_steel\",\"minecraft:enchanting_table\"]},seenCredits:0b,warden_spawn_tracker:{cooldown_ticks:0,ticks_since_last_warning:8788,warning_level:0}}"
	}
]
```

# 📐 Get build area `GET /buildarea`

This returns the current specified build area. The build area can be set inside Minecraft using the `setbuildarea` command. This is just a convenience command to specify the area, it has no implications to where blocks can be placed or read on the map.

The syntax for the setbuildarea Minecraft command is `/setbuildarea xFrom yFrom zFrom xTo yTo zTo`.

## URL parameters

None

## Request headers

None

## Request body

N/A

## Response headers

[Default](#Response-headers)

## Response body

A JSON response following this [schema](./schema.buildarea.get.json):

## Example

After having set the build area in game with `/setbuildarea ~ ~ ~ ~200 ~200 ~200`, requesting the build area via `GET /getbuildarea` returns:

```json
{
	"xFrom": 2353,
	"yFrom": 63,
	"zFrom": -78,
	"xTo": 2553,
	"yTo": 263,
	"zTo": 122
}
```

# 🗺️ Get heightmap `GET /heightmap`

Returns the [heightmap](https://minecraft.wiki/w/Heightmap) of the set build area of a given type.

## URL parameters

| key             | valid values                                                                                                                                         | required | defaults to     | description                                                                                                                                                                                                                                                                                                         |
|-----------------|------------------------------------------------------------------------------------------------------------------------------------------------------|----------|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| blocks          | comma-separated list of block IDs or block tag keys                                                                                                  | no       |                 | List of [block IDs](https://minecraft.wiki/w/Java_Edition_data_values#Blocks) and/or [block tag keys](https://minecraft.wiki/w/Block_tag_(Java_Edition)) and/or [fluid tag keys](https://minecraft.wiki/w/Fluid_tag_(Java_Edition)) of blocks that should be considered transparent when calculating the heightmap. |
| yBounds         | 1 or 2 integer values separated by two dots, following the [minecraft:int_range](https://minecraft.wiki/w/Argument_types#minecraft:int_range) schema | no       |                 | Range of upper and/or lower bounds in which the heightmap is measured. Only applied if `blocks` has a valid value as well. Especially useful in The Nether dimension and caves.                                                                                                                                     |
| type            | `WORLD_SURFACE`, `OCEAN_FLOOR`, `MOTION_BLOCKING`, `MOTION_BLOCKING_NO_LEAVES`, `MOTION_BLOCKING_NO_PLANTS`, `OCEAN_FLOOR_NO_PLANTS`                 | no       | `WORLD_SURFACE` | Heightmap preset to get. This parameter is ignored if `blocks` has a valid value.                                                                                                                                                                                                                                   |
| x               | integer                                                                                                                                              | yes      | `0`             | X coordinate                                                                                                                                                                                                                                                                                                        |
| z               | integer                                                                                                                                              | yes      | `0`             | Z coordinate                                                                                                                                                                                                                                                                                                        |
| dx              | integer                                                                                                                                              | no       | `1`             | Range of blocks to get, counting from `x` (can be negative)                                                                                                                                                                                                                                                         |
| dz              | integer                                                                                                                                              | no       | `1`             | Range of blocks to get, counting from `z` (can be negative)                                                                                                                                                                                                                                                         |
| withinBuildArea | `true`, `false`                                                                                                                                      | no       | `false`         | If `true`, skip over positions that are outside the build area                                                                                                                                                                                                                                                      |
| dimension       | `overworld`, `the_nether`, `the_end`, `nether`, `end`                                                                                                | no       | `overworld`     | Dimension of the world to get the heightmap for. Do note that heightmaps for The Nether will commonly return `128` for all positions due to there being no open sky in this dimension.                                                                                                                              |

Note that if a build area is set and the parameters `x`, `z`, `dx` or `dz` are not, the values of these missing parameters will default to that of the build area.

### Custom block list

When provided with a comma-separated list of [block IDs](https://minecraft.wiki/w/Java_Edition_data_values#Blocks) and/or [block tag keys](https://minecraft.wiki/w/Tag#Block_tags_2) and/or [fluid tag keys](https://minecraft.wiki/w/Tag#Fluid_tags) (these can be combined), a heightmap is calculated where the blocks listed are considered as transparent.

A block ID matches all possible states of that block. For instance, `/heightmap?blocks=air,oak_log` will match vertical and horizontal oak log blocks (`minecraft:oak_log[axis=y]`, `minecraft:oak_log[axis=x]`, `minecraft:oak_log[axis=z]`). To match specific block states, use the [square-bracket block state syntax](https://minecraft.wiki/w/Argument_types#minecraft:block_state). For example: `/heightmap?blocks=air,oak_log[axis=y]` will only match upright oak log blocks. Matching on SNBT data tags isn't supported by GDMC-HTTP.

Block tag keys describe a category of block. `#logs` for instance describe all types of [log blocks](https://minecraft.wiki/w/Log) and [stripped log blocks](https://minecraft.wiki/w/Stripped_Log).

Please note that air (`minecraft:air`) is not included by default.

Please note that for fluids it's best to use the fluid tag keys `#water` and/or `#lava`, since the block ID `minecraft:water`/`minecraft:lava` only includes non-flowing liquids.

Just as with [`PUT /blocks`](#-place-blocks-put-blocks), the `"minecraft:"` namespace doesn't have to be included for every block ID.

### Heightmap preset types

This endpoint supports 4 of [Minecraft's built-in heightmap types](https://minecraft.wiki/w/Heightmap):

- `WORLD_SURFACE`
  - Height of surface ignoring air blocks.
- `OCEAN_FLOOR`
  - Height surface ignoring air, water and lava.
- `MOTION_BLOCKING`
  - Height of surface ignoring blocks that have no movement collision (air, flowers, ferns, etc.) except for water and lava.
- `MOTION_BLOCKING_NO_LEAVES`
  - Same as `MOTION_BLOCKING` but also ignores [leaves](https://minecraft.wiki/w/Leaves).

Additionally, this mod provides 2 extra heightmap types:

- `MOTION_BLOCKING_NO_PLANTS`
  - Same as `MOTION_BLOCKING_NO_LEAVES`, except it also excludes the following blocks
    - [Logs](https://minecraft.wiki/w/Log)
    - [Bee nests](https://minecraft.wiki/w/Bee_Nest)
    - [Mangrove roots](https://minecraft.wiki/w/Mangrove_Roots) + [Muddy mangrove roots](https://minecraft.wiki/w/Muddy_Mangrove_Roots)
    - [Giant mushroom blocks](https://minecraft.wiki/w/Mushroom_Block)
    - [Pumpkin blocks](https://minecraft.wiki/w/Pumpkin) + [Carved pumpkin blocks](https://minecraft.wiki/w/Carved_Pumpkin) 
    - [Melon blocks](https://minecraft.wiki/w/Melon)
    - [Moss blocks](https://minecraft.wiki/w/Moss_Block)
    - [Nether wart blocks](https://minecraft.wiki/w/Nether_Wart_Block)
    - [Cactus blocks](https://minecraft.wiki/w/Cactus)
    - [Farmland](https://minecraft.wiki/w/Farmland)
    - [Coral blocks](https://minecraft.wiki/w/Coral_Block)
    - [Sponges](https://minecraft.wiki/w/Sponge)
    - [Bamboo plants](https://minecraft.wiki/w/Bamboo)
    - [Cobwebs](https://minecraft.wiki/w/Cobweb)
    - [Sculk](https://minecraft.wiki/w/Sculk)
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

A `404` error is returned if no build area has been set.

A `400` is returned if one of the block IDs or block tag keys provided in the `blocks` parameter are invalid.

A `400` status code is returned if heightmap preset type is not recognised.

## Example

### Custom block list example

After having set the build area in game with `/setbuildarea ~ ~ ~ ~10 ~10 ~10`, requesting heightmap data that ignores various "soft" soil and water can be done by calling the endpoint `GET /heightmap?blocks=#air,sand,gravel,dirt,clay,grass_block`, resulting in the following response:

```json
[
	[ 56, 56, 56, 56, 56, 57, 59, 59, 59, 59, 60 ],
	[ 56, 56, 56, 56, 57, 57, 59, 59, 59, 59, 58 ],
	[ 54, 56, 57, 59, 59, 59, 59, 59, 59, 60, 60 ],
	[ 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59 ],
	[ 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59 ],
	[ 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59 ],
	[ 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59 ],
	[ 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59 ],
	[ 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59 ], 
	[ 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59 ],
	[ 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 58 ]
]
```

### yBounds example

The `yBounds` can be useful to take measurements of the surface of underground caves or The Nether dimension. For example: `GET /heightmap?dimension=nether&blocks=#air,#lava,magma_block&yBounds=..100`. This starts measurements at Y=100, below the typical upper ceiling of The Nether dimension. This results in this response:

```json
[
	[ 86, 79, 79, 81, 81, 82, 81, 83, 84, 98, 95 ],
	[ 81, 75, 74, 81, 80, 81, 82, 83, 83, 84, 93 ],
	[ 82, 80, 70, 81, 82, 81, 82, 81, 83, 82, 84 ],
	[ 85, 70, 78, 82, 83, 82, 82, 81, 81, 83, 84 ],
	[ 83, 76, 77, 82, 82, 83, 82, 81, 81, 83, 84 ],
	[ 84, 78, 78, 82, 83, 82, 82, 81, 81, 83, 84 ],
	[ 77, 77, 81, 82, 82, 83, 82, 81, 81, 83, 84 ],
	[ 80, 79, 78, 81, 83, 81, 81, 82, 81, 83, 84 ],
	[ 78, 78, 78, 82, 82, 80, 81, 82, 81, 83, 84 ],
	[ 78, 78, 78, 81, 79, 81, 80, 82, 81, 83, 84 ],
	[ 78, 78, 78, 80, 79, 81, 80, 82, 81, 83, 84 ]
]
```

### Heightmap preset type example

After having set the build area in game with `/setbuildarea ~ ~ ~ ~20 ~20 ~20`, requesting the heightmap of that ignores water with `GET /heightmap?type=OCEAN_FLOOR` could return:

```json
[
	[ 68, 68, 66, 65, 65, 65, 72, 72, 72, 74, 74, 74, 71, 65, 65, 65, 65, 65, 68, 71, 71 ],
	[ 67, 68, 66, 65, 65, 72, 72, 73, 72, 72, 74, 71, 71, 64, 64, 64, 64, 64, 68, 68, 71 ],
	[ 66, 67, 67, 65, 65, 72, 73, 74, 73, 72, 71, 71, 63, 63, 63, 63, 63, 63, 63, 68, 68 ],
	[ 66, 66, 66, 66, 65, 72, 72, 73, 72, 72, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ],
	[ 65, 66, 65, 65, 65, 64, 72, 72, 72, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ],
	[ 64, 64, 64, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ],
	[ 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ],
	[ 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ],
	[ 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ], 
	[ 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ],
	[ 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ],
	[ 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ],
	[ 63, 64, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ],
	[ 64, 64, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ],
	[ 65, 65, 65, 64, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ],
	[ 66, 66, 66, 65, 65, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ],
	[ 66, 67, 66, 66, 65, 65, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ],
	[ 66, 67, 66, 66, 66, 65, 65, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63, 63 ], 
	[ 66, 67, 67, 66, 66, 65, 65, 64, 64, 63, 63, 64, 64, 64, 64, 64, 65, 65, 65, 64, 64 ], 
	[ 66, 67, 67, 66, 72, 72, 72, 65, 64, 64, 64, 64, 65, 65, 65, 65, 66, 66, 66, 66, 66 ],
	[ 66, 67, 67, 72, 72, 75, 72, 72, 65, 65, 65, 65, 65, 66, 66, 66, 67, 67, 67, 67, 67 ]
]
```

### Heightmap in custom area example

Request a heightmap of specified area using the `x`, `y`, `dx` and `dy` parameters, such as `GET /heightmap?type=OCEAN_FLOOR&x-6&z=22&dx=10&dz=10`:

```json
[
	[ 72, 72, 72, 72, 72, 77, 76, 72, 72, 72 ],
	[ 72, 72, 72, 76, 77, 77, 76, 72, 72, 72 ],
	[ 72, 72, 72, 76, 76, 76, 75, 75, 75, 72 ],
	[ 70, 70, 70, 70, 75, 76, 77, 76, 75, 72 ],
	[ 68, 68, 67, 68, 75, 77, 77, 77, 75, 77 ],
	[ 54, 52, 50, 61, 75, 76, 77, 76, 75, 77 ],
	[ 52, 51, 50, 55, 74, 75, 75, 75, 70, 77 ],
	[ 52, 51, 50, 53, 58, 60, 62, 65, 68, 77 ],
	[ 51, 51, 50, 52, 56, 58, 60, 61, 65, 67 ],
	[ 51, 51, 50, 51, 52, 53, 56, 54, 52, 50 ]
]
```


# 🪪 Read Minecraft version `GET /version`

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
1.21.4
```

# 🪪 Read HTTP interface information `OPTIONS /`

Get the information about your instance of GDMC-HTTP and Minecraft.

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
- `minecraftVersion`: String version number of the currently running version of Minecraft
- `DataVersion`: Integer version number of the [Data Version](https://minecraft.wiki/w/Data_version) of the currently running version of Minecraft.
- `interfaceVersion`: String version of the currently loaded version of GDMC-HTTP

## Example

```json
{
	"minecraftVersion": "1.21.4",
	"DataVersion": 4189,
	"interfaceVersion": "1.6.0-1.21.4"
}
```
