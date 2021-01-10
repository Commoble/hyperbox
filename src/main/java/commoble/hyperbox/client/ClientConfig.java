package commoble.hyperbox.client;

import commoble.hyperbox.ConfigHelper;
import commoble.hyperbox.ConfigHelper.ConfigValueListener;
import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig
{
	public final ConfigValueListener<Boolean> showPlacementPreview;
	public final ConfigValueListener<Double> placementPreviewOpacity;
	public final ConfigValueListener<Double> nameplateRenderDistance;
	public final ConfigValueListener<Double> nameplateSneakingRenderDistance;

	public ClientConfig(ForgeConfigSpec.Builder builder, ConfigHelper.Subscriber subscriber)
	{
		builder.push("block_rendering");
		this.showPlacementPreview = subscriber.subscribe(builder
			.comment("Whether to preview the state a hyperbox block will be placed as.",
				"Can be disabled if another placement preview mod is causing compatibility issues with this feature.")
			.define("show_placement_preview", true));
		this.placementPreviewOpacity = subscriber.subscribe(builder
			.comment("Opacity (alpha value) of hyperbox placement preview")
			.defineInRange("placement_preview_opacity", 0.4D, 0D, 1D));
		builder.pop();
		
		builder.push("nameplate_rendering");
		this.nameplateRenderDistance = subscriber.subscribe(builder
			.comment("Minimum distance from a hyperbox to the player to render hyperbox nameplates from while not sneaking.",
				"If negative, nameplates will not be rendered while not sneaking.")
			.define("nameplate_render_distance", -1D));
		this.nameplateSneakingRenderDistance = subscriber.subscribe(builder
			.comment("Minimum distance from a hyperbox to the player to render hyperbox nameplates from while sneaking.",
				"If negative, nameplates will not be rendered while sneaking.")
			.define("nameplate_sneaking_render_distance", 8D));
		builder.pop();
	}
}