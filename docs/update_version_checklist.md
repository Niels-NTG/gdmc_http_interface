
## checklist for updating to newer forge version / making a release

Just a simple checklist for myself, when I want to update this mod to a newer version.

## update documentation & make a changelog

check your latest commits to see what changed

## update forge version

If version is up to date (see 2.), just jump to 7.

### 1. Have a backup!

### 2. update build.gradle

**update mappings channel**

`29 | mappings channel: 'snapshot', version: '...'`
 
checkout the forge discord server https://discord.gg/UvedJ9m
 
newest mapping is probably pinned in the modder-support-... channel
 
mapping can be a version lower than the minecraft version you are developing for
 
forge devs are currently developing a new system for the mappings, so stuff might change

**update forge version**

`94 | minecraft 'net.minecraftforge:forge:1.16.4-35.1.4'`

find newest (stable) version on https://files.minecraftforge.net/

### 3. change the minecraft version in mods.toml

### 4. Reload gradle changes

### 5. Run the genIntellijRuns gradle task

wait for background tasks to finish (might take a few minutes)

### 6. File > Reload all from disk

wait for background tasks to finish

### 6.1. run script to apply mappings to the code

(replace the mappings id (YYYYMMDD-1.XX.X), see 2. for where to find it)
```
./gradlew -PUPDATE_MAPPINGS="20210309-1.16.5" -PUPDATE_MAPPINGS_CHANNEL="snapshot" updateMappings
```

### 6.2. File > Reload all from disk just in case :p


### 7. Increase version

Increase version id in build.gradle


### 8. Run tasks

Run 'build', 'runClient' and 'publish' tasks and make sure everything works.

Try out the published jar in the mod launcher

### merge / push

commit changes.

merge into master, push to master and make a release on github.

Paste / write changelog

Upload the mod jar (`build/files/gdmchttp-x.x.x.jar`)

### post release

Update the link in the README.md to point to the release

Update the link in the Installation docs

Notify people on discord

Update python scripts