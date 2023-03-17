# Working with the interface

Since the interface this mod provides uses HTTP, any program or language that has a component capable of doing HTTP requests can be a client. [GDPC](https://github.com/avdstaaij/gdpc) is a Python solution specifically designed for the purpose of generative design in Minecraft and is recommended to beginners, but you can write your own client in any programming language you like. You can find an example on how to provide access to the interface endpoints in your own client [here](https://github.com/avdstaaij/gdpc/blob/master/gdpc/direct_interface.py). For debugging and experimenting without writing your own program an API exploration tool such as [Insomnia](https://insomnia.rest/) or [Postman](https://www.postman.com/) is recommended.

Go [here](./Endpoints.md) for full documentation on how all the endpoints work.

## Commands
This mod adds a few new console commands to Minecraft

- `/setbuildarea <fromX> <fromY> <fromZ> <toX> <toY> <toZ>`
  - Sets a build area which can be referred to using the [GET /buildarea](docs/Endpoints.md:788) endpoint
  - Example `/setbuildarea ~ ~ ~ ~100 ~40 ~100`
  - Using the command without arguments unsets the build area.
- `/sethttpport <number>`
  - Changes the port number the HTTP interface can be reached from. Only comes into effect when Minecraft world is reloaded.
- `/gethttpport`
  - Shows the port the HTTP interface can be reached from.
