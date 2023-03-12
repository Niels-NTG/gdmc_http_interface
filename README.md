# Minecraft HTTP Interface Mod (Minecraft 1.19.2)

A Minecraft (Java Edition) Forge Mod that implements an HTTP interface for reading and writing blocks.

To install it, download the latest release [here](https://github.com/Niels-NTG/gdmc_http_interface/releases/latest) and follow these [installation instructions](./docs/Installation.md).

## What it's all about

This mod opens an HTTP interface so that other programs (on the same machine) can read and modify the world. It is designed as a tool to be used for the [Generative Design in Minecraft Competition (GDMC)](http://gendesignmc.engineering.nyu.edu/).

When you open a Minecraft world, this mod opens an HTTP Server on `localhost:9000`. I recommend using [Insomnia](https://insomnia.rest/) or a similar application to test out the http interface. For building a structure generator I recommend using the [GDPC](https://github.com/avdstaaij/gdpc) Python library.

This repo is based on a fork of [Niki Gawlik's GDMC HTTP Interface](https://github.com/nilsgawlik/gdmc_http_interface) (Minecraft 1.16.5).

## Features / HTTP Endpoints

The current endpoints of the interface are:

```
GET,PUT                 /blocks     Modify blocks in the world
POST                    /command    Run Minecraft commands
GET                     /chunks     Get raw chunk nbt data
GET                     /biomes     Get biome of position in the world
GET,POST                /structure  Generate NBT structure file from selection or place file into the world
GET,PUT,PATCH,DELETE    /entities   Read, create, edit and remove entities from the world
GET                     /buildarea  Get the build area defined by the /setbuildarea chat command
GET                     /version    Get Minecraft version
GET                     /players    Get names, positions, dimensions and camera rotations of all players
```

A detailed documentation of the endpoints can be found [over here](./docs/Endpoints.md).
