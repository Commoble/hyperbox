package net.commoble.hyperbox.client;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;

public class ClientConfig
{
	public final ConfigValue<Boolean> showPlacementPreview;
	public final ConfigValue<Double> placementPreviewOpacity;
	public final ConfigValue<Double> nameplateRenderDistance;
	public final ConfigValue<Double> nameplateSneakingRenderDistance;

	public ClientConfig(ModConfigSpec.Builder builder)
	{
		builder.push("block_rendering");
		this.showPlacementPreview = builder
			.comment("Whether to preview the state a hyperbox block will be placed as.",
				"Can be disabled if another placement preview mod is causing compatibility issues with this feature.")
			.define("show_placement_preview", true);
		this.placementPreviewOpacity = builder
			.comment("Opacity (alpha value) of hyperbox placement preview")
			.defineInRange("placement_preview_opacity", 0.4D, 0D, 1D);
		builder.pop();
		
		builder.push("nameplate_rendering");
		this.nameplateRenderDistance = builder
			.comment("Minimum distance from a hyperbox to the player to render hyperbox nameplates from while not sneaking.",
				"If negative, nameplates will not be rendered while not sneaking.")
			.define("nameplate_render_distance", -1D);
		this.nameplateSneakingRenderDistance = builder
			.comment("Minimum distance from a hyperbox to the player to render hyperbox nameplates from while sneaking.",
				"If negative, nameplates will not be rendered while sneaking.")
			.define("nameplate_sneaking_render_distance", 8D);
		builder.pop();
	}
}