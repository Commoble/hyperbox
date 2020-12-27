package commoble.hyperbox.client;

import commoble.hyperbox.ConfigHelper;
import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.ReflectionHelper;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.world.DimensionRenderInfo;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientProxy
{		
	public static ClientConfig clientConfig = null; // null until mod constructor
	
	// called from mod constructor
	public static void doClientModInit(IEventBus modBus, IEventBus forgeBus)
	{
		clientConfig = ConfigHelper.register(ModConfig.Type.CLIENT, ClientConfig::new);
		
//		Consumer<BreakEvent> onBreakEvent = event -> System.out.println(clientConfig.aliases.get().toString());
//		forgeBus.addListener(onBreakEvent);
		
		modBus.addListener(ClientProxy::onClientSetup);
	}
	
	static void onClientSetup(FMLClientSetupEvent event)
	{
		event.enqueueWork(ClientProxy::afterClientSetup);
	}
	
	static void afterClientSetup()
	{
		Object2ObjectMap<ResourceLocation, DimensionRenderInfo> renderInfoMap = ReflectionHelper.getStaticFieldOnce(DimensionRenderInfo.class, "field_239208_a_");
		renderInfoMap.put(Hyperbox.HYPERBOX_ID, new HyperboxRenderInfo());
	}
}
