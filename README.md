# GDMC HTTP Interface Mod

Forge Mod for Minecraft (Java Edition) that implements an HTTP interface for reading and writing blocks (and more).

To install it, download the latest release [here](https://github.com/Niels-NTG/gdmc_http_interface/releases/latest) and follow these [installation instructions](./docs/Installation.md).

| Minecraft version | GDMC-HTTP version                                                                           | Docs                                                                         |
|-------------------|---------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| 1.20.2            | 📦 [GDMC-HTTP 1.4.0](https://github.com/Niels-NTG/gdmc_http_interface/releases/tag/v1.4.0)  | 📒 [Docs](https://github.com/Niels-NTG/gdmc_http_interface/tree/v1.4.0/docs) |
| 1.19.2            | 📦 [GDMC-HTTP 1.3.2](https://github.com/Niels-NTG/gdmc_http_interface/releases/tag/v1.3.2)  | 📒 [Docs](https://github.com/Niels-NTG/gdmc_http_interface/tree/v1.3.2/docs) |
| 1.16.5            | 📦 [GDMC-HTTP 0.4.2](https://github.com/nikigawlik/gdmc_http_interface/releases/tag/v0.4.2) | 📒 [Docs](https://github.com/nikigawlik/gdmc_http_interface/wiki)            |

## What it's all about

This mod opens an HTTP interface so that other programs (on the same machine) can read and modify a Minecraft world. It is designed as a tool to be used for the [Generative Design in Minecraft Competition (GDMC)](http://gendesignmc.engineering.nyu.edu/).

When you open a Minecraft world, this mod opens an HTTP server on `localhost:9000`. I recommend using [Insomnia](https://insomnia.rest/) or a similar application to test out the http interface. For building a structure generator I recommend using the [GDPC](https://github.com/avdstaaij/gdpc) Python library.

This repo is based on a fork of [Niki Gawlik's GDMC HTTP Interface](https://github.com/nilsgawlik/gdmc_http_interface) (Minecraft 1.16.5).

## Features

### HTTP Endpoints

The current endpoints of the interface are:

```
GET,PUT                 /blocks     Modify blocks in the world
POST                    /command    Run Minecraft commands
GET                     /chunks     Get raw chunk nbt data
GET                     /biomes     Get biome of position in the world
GET,POST                /structure  Generate NBT structure file from selection or place file into the world
GET,PUT,PATCH,DELETE    /entities   Read, create, edit and remove entities from the world
GET                     /buildarea  Get the build area defined by the /setbuildarea chat command
GET                     /players    Read players from the world
GET                     /heightmap  Get heightmap of the set build area of a certain type
GET                     /version    Get Minecraft version
OPTIONS                 /           Get Minecraft version, mod version
```

A detailed documentation of the endpoints can be found [over here](./docs/Endpoints.md).
