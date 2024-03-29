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

We would personally recommend using [IntelliJ IDEA](https://www.jetbrains.com/idea/) for development. This is an IDE specialised for Java development that's very advanced while also easy to get started with. Additionally this IDE supports the [Minecraft NBT Support](https://plugins.jetbrains.com/plugin/12839-minecraft-nbt-support) plugin which makes it easy to inspect [NBT files](https://minecraft.wiki/w/NBT_format) as well as [Manifold](https://plugins.jetbrains.com/plugin/10057-manifold) (more on that later).

To aid in debugging the HTTP requests we recommend an API development and test tool such as [Insomnia](https://insomnia.rest/).

## Generating IDE Launch/Run Configurations:

- For IntelliJ, run the `genIntellijRuns` gradle task (gradlew `genIntellijRuns`). This will generate the Run Configurations and download any required assets for the game to run. After this has finished, reload the project from disk and click on "Add configuration". Under the "Application" tab, you have a `runClient` configuration. Select it, and edit your Configurations to fix the “module not specified” error by changing selecting your “main” module. You can now run Minecraft, with the mod loaded in. Make sure to open a Minecraft world before testing the mod.
- For Eclipse, run the `genEclipseRuns` gradle task (gradlew `genEclipseRuns`). This will generate the Launch Configurations and download any required assets for the game to run. After this has finished refresh your project.

# Supporting multiple Minecraft versions

We try to make GDMC-HTTP both backwards and forwards compatible such that client programs that interface with GDMC-HTTP from prior GDMC years can work with newer versions of the game and visa versa. Therefore we try to support multiple versions of Minecraft.

One potential way of doing this is creating a branch any time a new feature or patch has been added which backports these to older versions of the game. This can be quite time-consuming and can easily lead to missing features. Not to mention a lot of merge conflicts.

Instead we've taken inspiration from the build setup of the [Distant Horizons mod](https://gitlab.com/jeseibel/distant-horizons). Using this setup we can support multiple Minecraft versions and even multiple modding frameworks (all though we will stick with only supporting Forge for now) on the same branch. The "secret ingredient" here is the [Manifold Preprocessor](https://github.com/manifold-systems/manifold/tree/master/manifold-deps-parent/manifold-preprocessor), a dependency that adds conditional compilation to Java somewhat reminiscent of C. These directives can be placed anywhere in the code. The symbols are set by the `writeBuildGradlePredefine` function in `build.gradle`, which generates the `build.properties` which defines the list of symbols. Here are some examples on how it can be used in the source code:

```java
#if (MC_VER == MC_1_19_2)
import net.minecraft.server.level.ChunkHolder;
#else
import net.minecraft.server.level.FullChunkStatus;
#endif
```

```java
#if (MC_VER == MC_1_19_2)
blockState.getMaterial().blocksMotion()
#else
blockState.blocksMotion()
#endif
```

You may need to install a [plugin](https://plugins.jetbrains.com/plugin/10057-manifold) for your IDE for it to apply syntax highlighting and understand how to evaluate these directives.

All Gradle tasks such as `publish` and `runClient` can only run for one Minecraft version at the time. This target version is defined using the `targetMinecraftVersion` property in the `gradle.properties` file. You may change this value to your target version during development, but please revert it to the newest version supported by GDMC when you're done. The specific versions we support are listed in the `versionProperties` folder, in which you will find a file with properties specific for that version of the game and modding framework. To compile `jar` files for all these versions in a single action, run `buildAll.sh` script. You can find the output files in the `mcmodsrepo/` folder.

# Update version checklist

## Updating GDMC-HTTP

Our version numbers follow follow the [Semantic Versioning schema](https://semver.org/). 

1. When you have picked the appropriate version number set the `mod_version` property in `gradle.properties` to this value. 
2. Find-and-replace the previous version number with the new one in the documentation. This includes the following places
   - In the versions table in `README.md`, which has a link to the release page and the API docs page which includes the version tag in the text and URL.
   - The heading at the top of `docs/Endpoints.md`
   - The example output of the `OPTIONS /` endpoint as documented in `docs/Endpoints.md` needs to be updated to match the new output.
3. Update `CHANGELOG.md` with an list of items that are `NEW:`, `FIX:` and/or `BREAKING:`.
4. When relevant, update documentation.
5. Run the `buildAll.sh` script to build `jar` files for each supported Minecraft version/modding framework.
6. Do some final (manual) tests, preferably on all supported Minecraft/modding framework versions.
7. Commit all relevant changes, including everything we did in the previous steps.
8. Tag this commit with `v<newVersionNumber>` (eg. `v1.4.6`)
9. Push the commit and the tag.
10. Draft a new release on GitHub. Paste the items for this version from the changelog into the release notes section. Upload the jar files for this version for each Minecraft/modding framework we support.
11. Inform the people on the GDMC Discord about the update.

## Updating to a new version of Minecraft/modding framework

Of each minor version of Minecraft we tend to only support one version. Depending on how the timeline of Minecraft's release schedule and GDMC lines up, we pick the most recent version of the game and wait for a few patches to have rolled out. This is then set as the target version for the coming GDMC Settlement Generation Challenge.

To start supporting a new version of Minecraft, first check if there is a stable version of a relevant modding framework (eg. Forge, NeoForged) that supports this version. If so, create a new file in the `versionProperties` folder named `<mcVersion>.properties` (eg.` 1.20.2.properties`). This file contains properties relevant for this specific version of the game, including the version of the modding framework. You can find listings of releases of Forge [here](https://files.minecraftforge.net/net/minecraftforge/forge/). Prefer using the "Recommended" version that matches the relevant version of Minecraft. Don't forget the set the  `targetMinecraftVersion` property in the `gradle.properties` file to your new Minecraft version. After all this is done, reload your Gradle project and run `genIntellijRuns` (or `genEclipseRuns`) so all relevant dependencies are installed. 



