package commoble.hyperbox.client;

import commoble.hyperbox.ConfigHelper;
import commoble.hyperbox.ConfigHelper.ConfigValueListener;
import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig
{
	public final ConfigValueListener<Boolean> showPlacementPreview;
	public final ConfigValueListener<Double> placementPreviewOpacity;

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
	}
}