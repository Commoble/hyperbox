package commoble.hyperbox.client;

import commoble.hyperbox.ConfigHelper;
import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.RotationHelper;
import commoble.hyperbox.blocks.HyperboxBlock;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.DrawSelectionEvent;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
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

		modBus.addListener(ClientProxy::onClientSetup);
		modBus.addListener(ClientProxy::onRegisterRenderers);
		modBus.addListener(ClientProxy::onRegisterBlockColors);
		modBus.addListener(ClientProxy::onRegisterItemColors);
		forgeBus.addListener(ClientProxy::onHighlightBlock);
	}

	private static void onClientSetup(FMLClientSetupEvent event)
	{
		event.enqueueWork(ClientProxy::afterClientSetup);
	}
	
	private static void onRegisterRenderers(RegisterRenderers event)
	{
		event.registerBlockEntityRenderer(Hyperbox.INSTANCE.hyperboxBlockEntityType.get(), HyperboxBlockEntityRenderer::new);
	}

	private static void afterClientSetup()
	{
		Object2ObjectMap<ResourceLocation, DimensionSpecialEffects> renderInfoMap = DimensionSpecialEffects.EFFECTS;
		renderInfoMap.put(Hyperbox.HYPERBOX_ID, new HyperboxRenderInfo());
		
		ItemBlockRenderTypes.setRenderLayer(Hyperbox.INSTANCE.hyperboxBlock.get(), RenderType.cutout());
		ItemBlockRenderTypes.setRenderLayer(Hyperbox.INSTANCE.hyperboxPreviewBlock.get(), RenderType.cutout());
		ItemBlockRenderTypes.setRenderLayer(Hyperbox.INSTANCE.apertureBlock.get(), RenderType.cutout());
	}
	
	private static void onRegisterBlockColors(ColorHandlerEvent.Block event)
	{
		BlockColors colors = event.getBlockColors();
		colors.register(ColorHandlers::getHyperboxBlockColor, Hyperbox.INSTANCE.hyperboxBlock.get());
		colors.register(ColorHandlers::getHyperboxPreviewBlockColor, Hyperbox.INSTANCE.hyperboxPreviewBlock.get());
		colors.register(ColorHandlers::getApertureBlockColor, Hyperbox.INSTANCE.apertureBlock.get());
	}
	
	private static void onRegisterItemColors(ColorHandlerEvent.Item event)
	{
		event.getItemColors().register(ColorHandlers::getHyperboxItemColor, Hyperbox.INSTANCE.hyperboxItem.get());
	}
	
	private static void onHighlightBlock(DrawSelectionEvent.HighlightBlock event)
	{
		if (clientConfig.showPlacementPreview.get())
		{
			@SuppressWarnings("resource")
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null && player.level != null)
			{
				InteractionHand hand = player.getUsedItemHand();
				Item item = player.getItemInHand(hand == null ? InteractionHand.MAIN_HAND : hand).getItem();
				if (item instanceof BlockItem blockItem)
				{
					Block block = blockItem.getBlock();
					if (block instanceof HyperboxBlock hyperboxBlock)
					{
						Level level = player.level;
						BlockHitResult rayTrace = event.getTarget();
						Direction directionAwayFromTargetedBlock = rayTrace.getDirection();
						BlockPos placePos = rayTrace.getBlockPos().relative(directionAwayFromTargetedBlock);

						BlockState existingState = level.getBlockState(placePos);
						if (existingState.isAir() || existingState.getMaterial().isReplaceable())
						{
							// only render the preview if we know it would make sense for the block to be
							// placed where we expect it to be
							Vec3 hitVec = rayTrace.getLocation();

							Direction attachmentDirection = directionAwayFromTargetedBlock.getOpposite();
							Vec3 relativeHitVec = hitVec.subtract(Vec3.atLowerCornerOf(placePos));

							Direction outputDirection = RotationHelper.getOutputDirectionFromRelativeHitVec(relativeHitVec, attachmentDirection);
							RotationHelper.getRotationIndexForDirection(attachmentDirection, outputDirection);
							BlockState state = HyperboxBlock.getStateForPlacement(Hyperbox.INSTANCE.hyperboxPreviewBlock.get().defaultBlockState(), placePos, attachmentDirection, relativeHitVec);

							BlockPreviewRenderer.renderBlockPreview(placePos, state, level, event.getCamera().getPosition(), event.getPoseStack(), event.getMultiBufferSource());

						}
					}
				}
			}
		}
	}
}
