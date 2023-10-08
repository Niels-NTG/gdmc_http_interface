# Contribution guidelines

Code contributions are more than welcome. We do however strongly recommend to first create an issue before submitting a pull request in which you explain the bug or feature you're addressing.

# Running this mod from source

If you want to contribute to the development of the HTTP interface mod, or are just interested how it works under the hood, you can also download the source code and run the mod from the source.

These instructions are adapted from the [Forge installation guide](https://docs.minecraftforge.net/en/1.20.x/gettingstarted/#getting-started-with-forge).

## Get the sources

Clone (or fork and clone) this repository to your machine.

## Choose your IDE:

- Forge explicitly supports developing with IntelliJ or Eclipse environments, but any environment, such as Netbeans, Visual Studio Code to vi/emacs, can be made to work.
- For both Intellij IDEA and Eclipse their Gradle integration will handle the rest of the initial workspace setup, this includes downloading packages from Mojang, MinecraftForge, and a few other software sharing sites.
- For most, if not all, changes to the `build.gradle` or `gradle.properties` file to take effect Gradle will need to be invoked to re-evaluate the project, this can be done through Refresh buttons in the Gradle panels of both the previously mentioned IDEs.

We would personally recommend using [IntelliJ IDEA](https://www.jetbrains.com/idea/) for development. This is an IDE specialised for Java development that's very advanced while also easy to get started with. To aid in debugging we recommend an API development and test tool such as [Insomnia](https://insomnia.rest/). For working with [NBT files](https://minecraft.wiki/w/NBT_format) we recommend installing the [Minecraft NBT Support](https://plugins.jetbrains.com/plugin/12839-minecraft-nbt-support) plugin.

## Generating IDE Launch/Run Configurations:

- For IntelliJ, run the `genIntellijRuns` gradle task (gradlew `genIntellijRuns`). This will generate the Run Configurations and download any required assets for the game to run. After this has finished, reload the project from disk and click on "Add configuration". Under the "Application" tab, you have a `runClient` configuration. Select it, and edit your Configurations to fix the “module not specified” error by changing selecting your “main” module. You can now run Minecraft, with the mod loaded in. Make sure to open a Minecraft world before testing the mod.
- For Eclipse, run the `genEclipseRuns` gradle task (gradlew `genEclipseRuns`). This will generate the Launch Configurations and download any required assets for the game to run. After this has finished refresh your project.
