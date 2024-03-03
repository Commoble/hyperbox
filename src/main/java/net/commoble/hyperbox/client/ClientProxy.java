package net.commoble.hyperbox.client;

import net.commoble.hyperbox.ConfigHelper;
import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.RotationHelper;
import net.commoble.hyperbox.blocks.HyperboxBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

public class ClientProxy
{
	public static ClientConfig clientConfig = null; // null until mod constructor

	// called from mod constructor
	public static void doClientModInit(IEventBus modBus, IEventBus forgeBus)
	{
		clientConfig = ConfigHelper.register(ModConfig.Type.CLIENT, ClientConfig::new);

		modBus.addListener(ClientProxy::onRegisterScreens);
		modBus.addListener(ClientProxy::onRegisterDimensionSpecialEffects);
		modBus.addListener(ClientProxy::onRegisterRenderers);
		modBus.addListener(ClientProxy::onRegisterBlockColors);
		modBus.addListener(ClientProxy::onRegisterItemColors);
		forgeBus.addListener(ClientProxy::onHighlightBlock);
	}
	
	// do non-threadsafe stuff on main thread (client setup is off-thread)
	private static void onRegisterScreens(RegisterMenuScreensEvent event)
	{
		event.register(Hyperbox.INSTANCE.hyperboxMenuType.get(), HyperboxScreen::new);
	}
	
	private static void onRegisterRenderers(RegisterRenderers event)
	{
		event.registerBlockEntityRenderer(Hyperbox.INSTANCE.hyperboxBlockEntityType.get(), HyperboxBlockEntityRenderer::new);
	}
	
	private static void onRegisterDimensionSpecialEffects(RegisterDimensionSpecialEffectsEvent event)
	{
		event.register(Hyperbox.HYPERBOX_ID, new HyperboxRenderInfo());
	}
	
	private static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event)
	{
		event.register(ColorHandlers::getHyperboxBlockColor, Hyperbox.INSTANCE.hyperboxBlock.get());
		event.register(ColorHandlers::getHyperboxPreviewBlockColor, Hyperbox.INSTANCE.hyperboxPreviewBlock.get());
		event.register(ColorHandlers::getApertureBlockColor, Hyperbox.INSTANCE.apertureBlock.get());
	}
	
	private static void onRegisterItemColors(RegisterColorHandlersEvent.Item event)
	{
		event.register(ColorHandlers::getHyperboxItemColor, Hyperbox.INSTANCE.hyperboxItem.get());
	}
	
	private static void onHighlightBlock(RenderHighlightEvent.Block event)
	{
		if (clientConfig.showPlacementPreview.get())
		{
			@SuppressWarnings("resource")
			LocalPlayer player = Minecraft.getInstance().player;
			Level level = player.level();
			if (player != null && level != null)
			{
				InteractionHand hand = player.getUsedItemHand();
				Item item = player.getItemInHand(hand == null ? InteractionHand.MAIN_HAND : hand).getItem();
				if (item instanceof BlockItem blockItem)
				{
					Block block = blockItem.getBlock();
					if (block instanceof HyperboxBlock hyperboxBlock)
					{
						BlockHitResult rayTrace = event.getTarget();
						Direction directionAwayFromTargetedBlock = rayTrace.getDirection();
						BlockPos placePos = rayTrace.getBlockPos().relative(directionAwayFromTargetedBlock);

						BlockState existingState = level.getBlockState(placePos);
						if (existingState.isAir() || existingState.canBeReplaced())
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
