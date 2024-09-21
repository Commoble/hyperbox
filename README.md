Hyperbox is a minecraft forge mod that adds a box that's bigger on the inside than it is on the outside. To use this mod in singleplayer, install the latest forge build for minecraft and place the built mod jar into the mods folder of your minecraft root folder. To use this mod in multiplayer, the built mod jar must be placed in the mods folder of both the server and the clients who are playing on that server. Alternatively, this mod can be used by installing any modpack that includes it according to the instructions for installing that modpack.

Built jars are available here:
* https://www.curseforge.com/minecraft/mc-mods/hyperbox

A client config is available in `<your minecraft root folder>/config/hyperbox-client.toml` -- this config file allows several options to be set by the client player. This config file will be generated the first time minecraft runs with this mod installed; the default config file is as follows:

```toml

[block_rendering]
	#Whether to preview the state a hyperbox block will be placed as.
	#Can be disabled if another placement preview mod is causing compatibility issues with this feature.
	show_placement_preview = true
	#Opacity (alpha value) of hyperbox placement preview
	#Range: 0.0 ~ 1.0
	placement_preview_opacity = 0.4

[nameplate_rendering]
	#Minimum distance from a hyperbox to the player to render hyperbox nameplates from while sneaking.
	#If negative, nameplates will not be rendered while sneaking.
	nameplate_sneaking_render_distance = 8.0
	#Minimum distance from a hyperbox to the player to render hyperbox nameplates from while not sneaking.
	#If negative, nameplates will not be rendered while not sneaking.
	nameplate_render_distance = -1.0
```

A common config is also available in `<your minecraft root folder>/config/hyperbox-common.toml`:

```
[world_management]
	#Enable automatic forceloading of hyperbox chunks.
	#While this is enabled, the primary chunks of hyperbox worlds will be kept loaded while the
	#parent hyperbox's chunk is loaded, and will be kept unloaded while the parent hyperbox's chunk
	#is not loaded.
	#If this is disabled, no automatic enabling or disabling of forceloading will be done. In this case,
	#hyperbox's interiors will only tick while occupied by a player, or while forceloaded through
	#other means.
	#Be aware that if this option is changed from true to false while any hyperbox chunks are currently
	#forceloaded, they will continue to be forceloaded until those chunks are manually un-forceloaded.
	auto_force_hyperbox_chunks = true

```
