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

## Response headers
The responses for all endpoints return with the following headers, unless stated otherwise.

| key                         | value                       | description                                                  |
| --------------------------- | --------------------------- | ------------------------------------------------------------ |
| Access-Control-Allow-Origin | `*`                         |                                                              |
| Content-Disposition         | `inline`                    |                                                              |
| Content-Type                | `text/plain; charset=UTF-8` | If the `Accept: application/json` is present in the request header, the value will be `application/json; charset=UTF-8` instead. |

# Send Commands `POST /commands`
Send one or more Minecraft console commands to the server. For the full list of all commands consult the [Minecraft commands documentation](https://minecraft.fandom.com/wiki/Commands#List_and_summary_of_commands).

## URL parameters
| key       | valid values                                          | required | defaults to | description                                                  |
| --------- | ----------------------------------------------------- | -------- | ----------- | ------------------------------------------------------------ |
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Sets in which dimension of the world the commands will be executed in. |

## Request headers

None

## Request body

The request body should be formatted as plain-text and can contain multiple commands at the time, each on a new line.

## Response headers

[Default](#Response headers)

## Response body

A plain-text response, where the result of each command is displayed on separate lines.

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
```
1
1
1
289
1
```
And on a subsequent call, two of the commands will fail, so the return text will be:
```
1
1
Could not set the block
No blocks were filled
1
```

# Read blocks `GET /blocks`
Get information for one or more blocks in a given area.

## URL parameters
| key          | valid values                                          | required | defaults to | description                                                  |
| ------------ | ----------------------------------------------------- | -------- | ----------- | ------------------------------------------------------------ |
| x            | integer                                               | yes      | `0`         | X coordinate                                                 |
| y            | integer                                               | yes      | `0`         | Y coordinate                                                 |
| z            | integer                                               | yes      | `0`         | Z coordinate                                                 |
| dx           | integer                                               | no       | `1`         | Range of blocks to get counting from x (can be negative)     |
| dy           | integer                                               | no       | `1`         | Range of blocks to get counting from y (can be negative)     |
| dz           | integer                                               | no       | `1`         | Range of blocks to get counting from z (can be negative)     |
| includeState | `true`, `false`                                       | no       | `false`     | If `true`, include [block state](https://minecraft.fandom.com/wiki/Block_states) in response |
| includeData  | `true`, `false`                                       | no       | `false`     | If `true`, include [block entity data](https://minecraft.fandom.com/wiki/Chunk_format#Block_entity_format) in response |
| dimension    | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to read blocks from             |

## Request headers
| key    | valid values                     | defaults to  | description                                                  |
| ------ | -------------------------------- | ------------ | ------------------------------------------------------------ |
| Accept | `application/json`, `text/plain` | `text/plain` | Response data type. If set to `application/json`, response is formatted as a JSON array with objects describing each block. If not, a plain-text with each block description on a separate line |

## Request body

N/A

## Response headers

[Default](#Response headers)

## Response body

### JSON format

If request header has `Accept: application/json` set, the response follows this [schema](./schema.blocks.get.json).

### Plain-text format

If request has request header has `Accept: text/plain`, it will return a list of blocks, each on a separate line.

```
<x> <y> <z> <id>[<blockState>]{<blockData>}
```

- `x`, `y`, `z`: block placement position. Should be negative or positive integer numbers indicating its absolute position and are always present.
- `id`: namespaced block ID. Always required. Examples: `minecraft:stone`, `minecraft:clay`, `minecraft:green_stained_glass`.
- `[blockState]`: If URL parameters have `includeState=true`, this part contains [block state](https://minecraft.fandom.com/wiki/Block_states) for the block, written inside square brackets. Example: `[facing=east,lit=false]`.
- `{blockData}`: If URL parameters have `includeData=true`, this part contains [block entity data](https://minecraft.fandom.com/wiki/Chunk_format#Block_entity_format) for the block, written inside curly brackets. Example: `{Items:[{Count:64b,Slot:0b,id:"minecraft:iron_bars"},{Count:24b,Slot:1b,id:"minecraft:lantern"}]}`

## Example

To get a the block at position x=28, y=67 and z=-73, request `GET /blocks?x=-417&y=63&z=303`, which could return:
```
-417 63 303 minecraft:dirt
```
To get all block within a 2x2x2 area, request `GET /blocks?x=-417&y=63&z=303&dx=2&dy=2&dz=2`, which returns a list with each block on a seperate line:
```
-417 63 303 minecraft:dirt
-417 63 304 minecraft:grass_block
-417 64 303 minecraft:spruce_log
-417 64 304 minecraft:air
-416 63 303 minecraft:grass_block
-416 63 304 minecraft:grass_block
-416 64 303 minecraft:air
-416 64 304 minecraft:air
```
To include the [block state](https://minecraft.fandom.com/wiki/Block_states), request `GET /blocks?x=-417&y=64&z=303&includeState=true`:
```
-417 64 303 minecraft:spruce_log[axis=y]
```
To get a JSON-formatted response, set `Accept: application/json` in the request header:
```json
[
    {
        "id": "minecraft:spruce_log",
        "x": -417,
        "y": 64,
        "z": 303,
        "state": {
            "axis": "y"
        }
    }
]
```
To get information such as the contents of a chest, use `includeData=true` as part of the request; `GET /blocks?x=-446&y=79&z=337&includeState=true&includeData=true`:
```json
[
    {
        "id": "minecraft:chest",
        "x": -446,
        "y": 79,
        "z": 337,
        "state": {
            "facing": "north",
            "type": "single",
            "waterlogged": "false"
        },
        "data": {
            "Items": [
                {
                    "Count": 5,
                    "Slot": 0,
                    "id": "minecraft:poppy"
                },
                {
                    "Count": 1,
                    "Slot": 10,
                    "id": "minecraft:grindstone"
                },
                {
                    "Count": 1,
                    "Slot": 14,
                    "id": "minecraft:copper_ore"
                },
                {
                    "Count": 4,
                    "Slot": 26,
                    "id": "minecraft:wheat_seeds"
                }
            ]
        }
    }
]
```

# Place blocks `PUT /blocks`
Place one or more blocks into the world.

## URL parameters
| key            | valid values                                          | required | defaults to | description                                                  |
| -------------- | ----------------------------------------------------- | -------- | ----------- | ------------------------------------------------------------ |
| x              | integer                                               | yes      | `0`         | X coordinate                                                 |
| y              | integer                                               | yes      | `0`         | Y coordinate                                                 |
| z              | integer                                               | yes      | `0`         | Z coordinate                                                 |
| doBlockUpdates | `true`, `false`                                       | no       | `true`      | If `true`, tell neighbouring blocks to reach to placement, see [Controlling block update behavior](#controlling-block-update-behavior). |
| spawnDrops     | `true`, `false`                                       | no       | `false`     | If `true`, drop items if existing blocks are destroyed by this placement, see [Controlling block update behavior](#controlling-block-update-behavior). |
| customFlags    | bit string                                            | no       | `0100011`   | Force certain behaviour when placing blocks, see [Controlling block update behavior](#controlling-block-update-behavior). |
| dimension      | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to place blocks in              |

### Controlling block update behavior

In Minecraft destroying or placing a block will cause a 'block update'. A block update tells neighboring blocks to react to the change. An example would be for water to flow in to the newly created space, a torch to pop off after the block it was on has been destroyed or a fence to connect to a newly placed fence post. If for performance or stability reasons you want to avoid block updates you can set the query parameter `doBlockUpdates` to false (`PUT /blocks?x=10&y=64&z=-87&doBlockUpdates=false`). But be warned, this can cause cosmetic issues such as fences not connecting automatically!

By default blocks placed through the interface will not cause any items to be dropped. In the example of the torch, if a block is destroyed, and attached torch will be destroyed too, but not drop as an item. If for some reason you do want items to drop you can set the `spawnDrops` query parameter to true (`PUT /blocks?x=10&y=64&z=-87&spawnDrops=true`).

Both of these query parameters set certain 'block update flags' internally. If you know what you are doing you can also set the block update behavior manually. But be careful, because this can cause glitchy behavior! You can set the block update flags with the query parameter `customFlags`. It is a bit string consisting of 7 bits and it will override the behavior set by `doBlockUpdates` and `spawnDrops`. For example `PUT /blocks?x=10&y=64&z=-87&customFlags=0000010`.

The flags are as follows:

| bit string | effect                                                       |
| ---------- | ------------------------------------------------------------ |
| `0000001`  | will cause a block update.                                   |
| `0000010`  | will send the change to clients.                             |
| `0000100`  | will prevent the block from being re-rendered.               |
| `0001000`  | will force any re-renders to run on the main thread instead  |
| `0010000`  | will prevent neighbour reactions (e.g. fences connecting, observers pulsing) |
| `0100000`  | will prevent neighbour reactions from spawning drops.        |
| `1000000`  | will signify the block is being moved.                       |

You can combine these flags as you wish, for example 0100011 will cause a block update _and_ send the change to clients _and_ prevent neighbor reactions. You should always have the `0000010` flag active, otherwise you will get invisible block glitches.

The following list shows which block update flags `doBlockUpdates` and `spawnDrops` get evaluated to internally:

```
doBlockUpdates=False, spawnDrops=False -> 0110010
doBlockUpdates=False, spawnDrops=True  -> 0110010
doBlockUpdates=True,  spawnDrops=False -> 0100011    (default behavior)
doBlockUpdates=True,  spawnDrops=True  -> 0000011
```

## Request headers

| key          | valid values                     | defaults to  | description                  |
| ------------ | -------------------------------- | ------------ | ---------------------------- |
| Accept       | `application/json`, `text/plain` | `text/plain` | Response data type           |
| Content-Type | `application/json`, `text/plain` | `text/plain` | Content type of request body |

## Request body
### JSON format
If request has the header `Content-Type: application/json`, the response is expected to be valid JSON. It should be a single JSON array of JSON objects according to this [schema](./schema.blocks.put.json)

After receiving the request, GDMC-HTTP will first to attempt to parse the whole request body into valid JSON. If this fails it will return a response with HTTP status `400`.

### Plain-text format
If request has the header `Content-Type: text/plain` it will parse the request body as a plain-text, with each block placement instruction on a new line.
```
<x> <y> <z> <id>[<blockState>]{<blockData>}
```
- `x`, `y`, `z`: block placement position. Should be negative or positive integer numbers. Use the `~` or `^` prefix to make these values [relative]((https://minecraft.fandom.com/wiki/Coordinates#Relative_world_coordinates)) to the position set in the request URL. If all are omitted, the corresponding coordinates from the request URL are used instead.
- `id`: namespaced block ID. Always required. Examples: `minecraft:stone`, `minecraft:clay`, `minecraft:green_stained_glass`.
- `[blockState]`: Optional [block state](https://minecraft.fandom.com/wiki/Block_states) for this block, written inside square brackets. Example: `[facing=east,lit=false]`.
- `{blockData}`: Optional [block entity data](https://minecraft.fandom.com/wiki/Chunk_format#Block_entity_format) for this block, written inside curly brackets. Example: `{Items:[{Count:64b,Slot:0b,id:"minecraft:iron_bars"},{Count:24b,Slot:1b,id:"minecraft:lantern"}]}`

## Response headers

[Default](#Response headers)

## Response body

For each placement instruction in the request, it returns a list with a `"1"` if placement was successful, a `"0"` of that specification is already at that position in the world, and an error code if something else went wrong such as a missing or invalid block ID, placement position, etc.

If request header has `Accept: application/json`, these values are listed in a JSON array. Otherwise they are listed in plain-text, each on a separated line. In either format the order of these corresponds to the order the placement instruction was listed.

## Example

For `PUT /blocks?x=-43&y=2&z=23` with the request header `Content-Type: application/json` and `Accept: application/json` and this request body:
```json
[
    {
        "id": "minecraft:chest",
        "x": "~2",
        "y": 0,
        "z": -106,
        "state": {
            "facing": "east",
            "type": "single",
            "waterlogged": "false"
        },
        "data": {
            "Items": [
                {
                    "Count": 48,
                    "Slot": 0,
                    "id": "minecraft:lantern"
                },
                {
                    "Count": 1,
                    "Slot": 1,
                    "id": "minecraft:golden_axe",
                    "tag": {
                        "Damage": 0
                    }
                }
            ]
        }
    },
    {
        "id": "minecraft:acacia_sapling",
        "x": "~2",
        "y": 0,
        "z": -104,
        "state": {
            "stage": "0"
        }
    }
]
```
This returns:
```json
[
    "1",
    "1"
]
```
Where each line corresponds to a placement instruction, where "1" indicates a success, "0" that a block of that type is already there and an error message if something else went wrong.

# Read biomes `GET /biomes`
Get [biome](https://minecraft.fandom.com/wiki/Biome#List_of_biomes) information in a given area.

## URL parameters
| key       | valid values                                          | required | defaults to | description                                              |
| --------- | ----------------------------------------------------- | -------- | ----------- | -------------------------------------------------------- |
| x         | integer                                               | yes      | `0`         | X coordinate                                             |
| y         | integer                                               | yes      | `0`         | Y coordinate                                             |
| z         | integer                                               | yes      | `0`         | Z coordinate                                             |
| dx        | integer                                               | no       | `1`         | Range of blocks to get counting from x (can be negative) |
| dy        | integer                                               | no       | `1`         | Range of blocks to get counting from y (can be negative) |
| dz        | integer                                               | no       | `1`         | Range of blocks to get counting from z (can be negative) |
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to read blocks from         |

## Request headers
| key    | valid values                     | defaults to  | description                                                  |
| ------ | -------------------------------- | ------------ | ------------------------------------------------------------ |
| Accept | `application/json`, `text/plain` | `text/plain` | Response data type. If set as `application/json`, response is formatted as a JSON array with objects describing each biome. If not, a plain-text with each biome description on a separate line. |

## Request body

N/A

## Response headers

[Default](#Response headers)

## Response body

### JSON format

If request header has `Accept: application/json`, the response should follow this [schema](./schema.biomes.get.json).

### Plain-text response

If request has request header has `Accept: text/plain`, it will return a list of biomes, each on a separate line.

```
<x> <y> <z> <id>
```

- `x`, `y`, `z`: block position. Should be negative or positive integer numbers indicating its absolute position and are always present.
- `id`: namespaced biome ID. Always required. Examples: `minecraft:plains`, `minecraft:wooded_badlands`, `minecraft:dripstone_caves`.

## Example

For getting the biomes of a row of blocks, request `GET /biomes?x=2350&y=64&z=-77&dx=-6`:
```
2344 64 -77 minecraft:river
2345 64 -77 minecraft:river
2346 64 -77 minecraft:river
2347 64 -77 minecraft:river
2348 64 -77 minecraft:river
2349 64 -77 minecraft:forest
```
Setting the request header with `Accept: application/json` returns this data in JSON format:
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
| key       | valid values                                          | required | defaults to | description                                                  |
| --------- | ----------------------------------------------------- | -------- | ----------- | ------------------------------------------------------------ |
| x         | integer                                               | yes      | `0`         | X chunk coordinate                                           |
| z         | integer                                               | yes      | `0`         | Z chunk coordinate                                           |
| dx        | integer                                               | no       | `1`         | Range of chunks (not blocks!) to get counting from x (can be negative) |
| dz        | integer                                               | no       | `1`         | Range of chunks (not blocks!) to get counting from z (can be negative) |
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Which dimension of the world to read chunks from             |

## Request headers
| key             | valid values                                                 | defaults to                | description                                                  |
| --------------- | ------------------------------------------------------------ | -------------------------- | ------------------------------------------------------------ |
| Accept          | `application/json`, `text/plain`, `application/octet-stream` | `application/octet-stream` | Response data type. By default returns as raw bytes of a [NBT](https://minecraft.fandom.com/wiki/NBT_format) file. Use `text/plain` for the same data, but in the human-readable [SNBT](https://minecraft.fandom.com/wiki/NBT_format#SNBT_format) format. And use `application/json` for better readable data at the cost of losing some data type precision, refer to [JSON and NBT](https://minecraft.fandom.com/wiki/NBT_format#Conversion_from_JSON) for more information. |
| Accept-Encoding | `gzip`, `*`                                                  | `*`                        | If set to `gzip`, any raw bytes NBT file is compressed using GZIP. |

## Request body

N/A

## Response headers

Table below only applies if the request header `Accept: application/octet-stream` is present. Otherwise it returns with the [Default](#Response headers) response headers.

| key                         | value                      | description                                                  |
| --------------------------- | -------------------------- | ------------------------------------------------------------ |
| Access-Control-Allow-Origin | `*`                        |                                                              |
| Content-Disposition         | `attachment`               | Allows some clients automatically treat the output as a file instead of displaying it inline. |
| Content-Type                | `application/octet-stream` |                                                              |
| Content-Encoding            | `gzip`                     | Only if same `Accept-Encoding: gzip` was present in the request header. |

## Response body

Response should be encoded as an [NBT](https://minecraft.fandom.com/wiki/NBT_format), [SNBT](https://minecraft.fandom.com/wiki/NBT_format#SNBT_format) or JSON-formatted data structure depending what value has been set for `Accept` in the request header. The data always contains the following properties:

- `ChunkX`: Same value as URL parameter x
- `ChunkZ`: Same value as URL parameter z
- `ChunkDX`: Same value as URL parameter dx
- `ChunkDZ`: Same value as URL parameter dz
- `Chunks`: List of chunks, where each chunk is in the [NBT Chunk format](https://minecraft.fandom.com/wiki/Chunk_format#NBT_structure) encoded as raw NBT, SNBT or JSON.

## Example

Get a single chunk at position x=0, z=8 in the Nether with the request `GET /chunks?x=0&z=8&dimension=nether` with the header `Accept: text/plain` to get something that is human-readable:
```
{ChunkDX:1,ChunkDZ:1,ChunkX:0,ChunkZ:8,Chunks:[{DataVersion:3120,Heightmaps:{MOTION_BLOCKING:[L;2310355422147575936L,2310355422147575936L,2310355422147575936L,2310355422147575936L, ...
```

# Place NBT structure file `POST /structure`
Place an [NBT](https://minecraft.fandom.com/wiki/NBT_format) structure file into the world. These files can be created by the [Structure Block](https://minecraft.fandom.com/wiki/Structure_Block) as well as other means.

## URL parameters
| key       | valid values                                          | required | defaults to | description                                                  |
| --------- | ----------------------------------------------------- | -------- | ----------- | ------------------------------------------------------------ |
| x         | integer                                               | yes      | `0`         | X coordinate                                                 |
| y         | integer                                               | yes      | `0`         | Y coordinate                                                 |
| z         | integer                                               | yes      | `0`         | Z coordinate                                                 |
| mirror    | `x`, `y`                                              | no       | `0`         | `x` = mirror structure front to back; `y` = mirror structure left to right |
| rotate    | `0`, `1`, `2`, `3`                                    | no       | `0`         | `0` = apply no rotation; `1` = rotate structure 90° clockwise; `2` = rotate structure 180°; `3` = rotate structure 90° counter-clockwise |
| pivotX    | integer                                               | no       | `0`         | relative X coordinate to use as pivot for rotation           |
| pivotY    | integer                                               | no       | `0`         | relative Y coordinate to use as pivot for rotation           |
| pivotZ    | integer                                               | no       | `0`         | relative Z coordinate to use as pivot for rotation           |
| entities  | `true`, `false`                                       | no       | `false`     | `true` = also place all [entities](https://minecraft.fandom.com/wiki/Entity) (mobs, villagers, etc.) saved with the file |
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | `overworld` | Sets in which dimension of the world to place the structure in |

## Request headers
| key              | valid values                     | defaults to  | description                                                  |
| ---------------- | -------------------------------- | ------------ | ------------------------------------------------------------ |
| Accept           | `application/json`, `text/plain` | `text/plain` | Response data type                                           |
| Content-Encoding | `gzip`, `*`                      | `gzip`       | If set to `gzip`, input NBT file is assumed to be compressed using GZIP. This is enabled by default since files generated by the [Structure Block](https://minecraft.fandom.com/wiki/Structure_Block) are compressed this way. Setting this header to `*` will make GDMC-HTTP attempt to parse the file as both a compressed and uncompressed file (in that order) and continue with the one that is valid, ideal for when it's unclear if the file is compressed or not. |

## Request body

A valid [NBT file](https://minecraft.fandom.com/wiki/NBT_format).

## Response headers

[Default](#Response headers)

## Response body

Contains a single `1` if the placement was successful or a `0` or an error message if not.

If request header has `Accept: application/json` this value is contained in a JSON array.

## Example

Using the [Structure Block](https://minecraft.fandom.com/wiki/Structure_Block), [save](https://minecraft.fandom.com/wiki/Structure_Block#Save) an area of any Minecraft world. Give it a name such as "example:test-structure" and set the Include entities setting to "ON", then hit the "SAVE" button. You will now be able to find the file under `(minecraftFiles)/saves/(worldName)/generated/example/test-structure.nbt`.

Now in Minecraft load the Minecraft world you want to place this structure in, pick a location and place it there using this endpoint. To place the it at location x=102, y=67, z=-21 with entities, include the file as the request body to request `POST /structure?x=102&y=67&z=-21&entities=true`.

# Create NBT structure file `GET /structure`

Create an [NBT](https://minecraft.fandom.com/wiki/NBT_format) structure file from an area of the world.

## URL parameters

| key       | valid values                                          | required | defaults to | description                                                  |
| --------- | ----------------------------------------------------- | -------- | ----------- | ------------------------------------------------------------ |
| x         | integer                                               | yes      | `0`         | X coordinate                                                 |
| y         | integer                                               | yes      | `0`         | Y coordinate                                                 |
| z         | integer                                               | yes      | `0`         | Z coordinate                                                 |
| dx        | integer                                               | no       | `1`         | Range of blocks to get counting from x (can be negative)     |
| dy        | integer                                               | no       | `1`         | Range of blocks to get counting from y (can be negative)     |
| dz        | integer                                               | no       | `1`         | Range of blocks to get counting from z (can be negative)     |
| entities  | `true`, `false`                                       | no       | `false`     | `true` = also save all [entities](https://minecraft.fandom.com/wiki/Entity) (mobs, villagers, etc.) in the given area |
| dimension | `overworld`, `the_nether`, `the_end`, `nether`, `end` | no       | overworld   | Which dimension of the world to read blocks from             |

## Request headers

| key             | valid values                                                 | defaults to                | description                                                  |
| --------------- | ------------------------------------------------------------ | -------------------------- | ------------------------------------------------------------ |
| Accept          | `application/json`, `text/plain`, `application/octet-stream` | `application/octet-stream` | Response data type. By default returns the contents that makes a real NBT file. Use `text/plain` for a more human readable lossless version of the data in the [SNBT](https://minecraft.fandom.com/wiki/NBT_format#SNBT_format) format, and `application/json` for better readable data at the cost of losing some data type precision, refer to [JSON and NBT](https://minecraft.fandom.com/wiki/NBT_format#Conversion_from_JSON) for more information. |
| Accept-Encoding | gzip, *                                                      | `gzip`                     | If set to `gzip`, compress resulting file using gzip compression. |

## Request body

N/A

## Response headers

Table below only applies if the request header `Accept: application/octet-stream` is present. Otherwise it returns with the default headers described [here](#response-headers).

| key                         | value                      | description                                                  |
| --------------------------- | -------------------------- | ------------------------------------------------------------ |
| Access-Control-Allow-Origin | `*`                        |                                                              |
| Content-Disposition         | `attachment`               | Allows some clients automatically treat output as a file instead of displaying it inline. |
| Content-Type                | `application/octet-stream` |                                                              |
| Content-Encoding            | `gzip`                     | Only if request header has `Accept-Encoding: gzip`.          |

## Response body

An [NBT file](https://minecraft.fandom.com/wiki/NBT_format) the selected area of the world.

## Example

The request `GET /structure?x=87&y=178&z=247&dx=10&dy=10&dz=10&dimension=nether` gets us a 10x10x10 area from The Nether. Entities such as mobs in that cube-shaped area are not included, since the request does not have the `entities=true` parameter. Leaving the request headers to its defaults this yields a gzip-compressed binary-encoded NBT data that we can save to a file, manipulate using an external tool, and place back into the world using the [Structure Block](https://minecraft.fandom.com/wiki/Structure_Block) or the `POST /structure` endpoint.

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
| --------------------------- | --------------------------------- | ----------- |
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

# Read Minecraft version `GET /version`
Get the current version of Minecraft.

## URL parameters

None

## Request headers

None

## Request body

N/A

## Response headers

[Default](#Response headers)

## Response body

Plain-text response with the Minecraft version number.


## Example
`GET /version` returns `"1.19.2"`.
