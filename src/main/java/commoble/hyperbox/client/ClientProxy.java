package commoble.hyperbox.client;

import commoble.hyperbox.ConfigHelper;
import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.ReflectionHelper;
import commoble.hyperbox.RotationHelper;
import commoble.hyperbox.blocks.HyperboxBlock;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.world.DimensionRenderInfo;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.DrawHighlightEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientProxy
{
	public static ClientConfig clientConfig = null; // null until mod constructor

	// called from mod constructor
	public static void doClientModInit(IEventBus modBus, IEventBus forgeBus)
	{
		clientConfig = ConfigHelper.register(ModConfig.Type.CLIENT, ClientConfig::new);

		modBus.addListener(ClientProxy::onClientSetup);
		modBus.addListener(ClientProxy::onRegisterBlockColors);
		modBus.addListener(ClientProxy::onRegisterItemColors);
		forgeBus.addListener(ClientProxy::onHighlightBlock);
	}

	static void onClientSetup(FMLClientSetupEvent event)
	{
		ClientRegistry.bindTileEntityRenderer(Hyperbox.INSTANCE.hyperboxTileEntityType.get(), HyperboxTileEntityRenderer::new);
		event.enqueueWork(ClientProxy::afterClientSetup);
	}

	static void afterClientSetup()
	{
		Object2ObjectMap<ResourceLocation, DimensionRenderInfo> renderInfoMap = ReflectionHelper.getStaticFieldOnce(DimensionRenderInfo.class, "field_239208_a_");
		renderInfoMap.put(Hyperbox.HYPERBOX_ID, new HyperboxRenderInfo());
		
		RenderTypeLookup.setRenderLayer(Hyperbox.INSTANCE.hyperboxBlock.get(), RenderType.getCutout());
		RenderTypeLookup.setRenderLayer(Hyperbox.INSTANCE.hyperboxPreviewBlock.get(), RenderType.getCutout());
		RenderTypeLookup.setRenderLayer(Hyperbox.INSTANCE.apertureBlock.get(), RenderType.getCutout());
	}
	
	static void onRegisterBlockColors(ColorHandlerEvent.Block event)
	{
		BlockColors colors = event.getBlockColors();
		colors.register(ColorHandlers::getHyperboxBlockColor, Hyperbox.INSTANCE.hyperboxBlock.get());
		colors.register(ColorHandlers::getHyperboxPreviewBlockColor, Hyperbox.INSTANCE.hyperboxPreviewBlock.get());
		colors.register(ColorHandlers::getApertureBlockColor, Hyperbox.INSTANCE.apertureBlock.get());
		
	
	}
	
	static void onRegisterItemColors(ColorHandlerEvent.Item event)
	{
		event.getItemColors().register(ColorHandlers::getHyperboxItemColor, Hyperbox.INSTANCE.hyperboxItem.get());
	}
	
	static void onHighlightBlock(DrawHighlightEvent.HighlightBlock event)
	{
		if (clientConfig.showPlacementPreview.get())
		{
			@SuppressWarnings("resource")
			ClientPlayerEntity player = Minecraft.getInstance().player;
			if (player != null && player.world != null)
			{
				Hand hand = player.getActiveHand();
				Item item = player.getHeldItem(hand == null ? Hand.MAIN_HAND : hand).getItem();
				if (item instanceof BlockItem)
				{
					Block block = ((BlockItem) item).getBlock();
					if (block instanceof HyperboxBlock)
					{
						World world = player.world;
						BlockRayTraceResult rayTrace = event.getTarget();
						Direction directionAwayFromTargetedBlock = rayTrace.getFace();
						BlockPos placePos = rayTrace.getPos().offset(directionAwayFromTargetedBlock);

						BlockState existingState = world.getBlockState(placePos);
						if (existingState.isAir(world, placePos) || existingState.getMaterial().isReplaceable())
						{
							// only render the preview if we know it would make sense for the block to be
							// placed where we expect it to be
							Vector3d hitVec = rayTrace.getHitVec();

							Direction attachmentDirection = directionAwayFromTargetedBlock.getOpposite();
							Vector3d relativeHitVec = hitVec.subtract(Vector3d.copy(placePos));

							Direction outputDirection = RotationHelper.getOutputDirectionFromRelativeHitVec(relativeHitVec, attachmentDirection);
							RotationHelper.getRotationIndexForDirection(attachmentDirection, outputDirection);
							BlockState state = HyperboxBlock.getStateForPlacement(Hyperbox.INSTANCE.hyperboxPreviewBlock.get().getDefaultState(), placePos, attachmentDirection, relativeHitVec);

							BlockPreviewRenderer.renderBlockPreview(placePos, state, world, event.getInfo().getProjectedView(), event.getMatrix(), event.getBuffers());

						}
					}
				}
			}
		}
	}
}
