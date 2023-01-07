# Installing this mod with the Forge Mod Launcher

You need to own a copy of Minecraft Java Edition and have it installed on your machine.

Get the [Forge Mod Installer](https://files.minecraftforge.net/) and download version 1.1.19.2-43.2.0, which you can find under the "1.19.2" > "Download Recommended" > "Installer". Open the downloaded file to install this version of Forge.

Open your Minecraft Launcher, the Forge Installation should have appeared there.

Open your mod folder. To do this open the Forge Installation you just installed and click the "Mods" button and then the "Open mods folder" button (You can skip this step by just navigating to %APPDATA%/.minecraft/mods).

Download this mod's jar file from [here](https://github.com/Niels-NTG/gdmc_http_interface/releases/latest) and place it in the mod folder.

Restart Minecraft and launch the Forge Installation again. The mod should now appear in the mod list under "Mods".

When you open a world the HTTP Server will be started automatically, and you should now be able to send HTTP requests to it.

# Running this mod from source

If you want to contribute to the development of the http interface mod, or are just interested how it works under the hood, you can also download the source code and run the mod from the source.

These instructions are adapted from the [Forge installation guide](https://docs.minecraftforge.net/en/1.19.x/gettingstarted/#getting-started-with-forge)

## Get the sources

Clone or fork this repository.

## Choose your IDE:

- Forge explicitly supports developing with IntelliJ or Eclipse environments, but any environment, from Netbeans to vi/emacs, can be made to work.
- For both Intellij IDEA and Eclipse their Gradle integration will handle the rest of the initial workspace setup, this includes downloading packages from Mojang, MinecraftForge, and a few other software sharing sites.
You just have to import the build.gradle in the IDE, and the project will be imported.
- For most, if not all, changes to the build.gradle file to take effect Gradle will need to be invoked to re-evaluate the project, this can be done through Refresh buttons in the Gradle panels of both the previously mentioned IDEs.

I would personally recommend using [IntelliJ IDEA](https://www.jetbrains.com/idea/) for development.

## Generating IDE Launch/Run Configurations:

- For IntelliJ, run the genIntellijRuns gradle task (gradlew genIntellijRuns). This will generate the Run Configurations and download any required assets for the game to run. After this has finished, reload the project from disk and click on "Add configuration". Under the "Application" tab, you have a runClient configuration. Select it, and edit your Configurations to fix the “module not specified” error by changing selecting your “main” module. You can now run Minecraft, with the mod loaded in. Make sure to open a Minecraft world before testing the mod.
- For Eclipse, run the genEclipseRuns gradle task (gradlew genEclipseRuns). This will generate the Launch Configurations and download any required assets for the game to run. After this has finished refresh your project.
