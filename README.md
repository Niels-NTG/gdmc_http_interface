# Minecraft HTTP Interface Mod (Minecraft 1.19.2)

This repo is based on the [GDMC example mod](https://github.com/Lasbleic/gdmc_java_mod) which is based on the Forge MDK.

The latest release is [0.5.1 for Minecraft version 1.19.2](https://github.com/Niels-NTG/gdmc_http_interface/releases/tag/v0.5.1)

## What it's all about

(Disclaimer: This mod is in active development, and things might still change)

This mod opens an HTTP interface so that other programs (on the same machine) can read and modify the world. It is meant as a tool to be used for the [Generative Design in Minecraft Competition](http://gendesignmc.engineering.nyu.edu/).

When you open a Minecraft world, this mod opens an HTTP Server on localhost:9000. I recommend using Postman or a similar application to test out the http interface. A Python example of how to use the interface can be found [here](https://github.com/avdstaaij/gdpc).

## Features / HTTP Endpoints

The current endpoints of the interface are 

```
GET,PUT /blocks     Modify blocks in the world
POST    /command    Run Minecraft commands
GET     /chunks     Get raw chunk nbt data
GET     /buildarea  Get the build area defined by the /setbuildarea chat command
GET     /version    Get Minecraft version
```

A detailed documentation of the endpoints can be found [over here](https://github.com/Niels-NTG/gdmc_http_interface/wiki/Interface-Endpoints).

## Installation

Install instructions are [over here](https://github.com/Niels-NTG/gdmc_http_interface/wiki/Installation).
You need to own a copy of Minecraft to use the http interface!
