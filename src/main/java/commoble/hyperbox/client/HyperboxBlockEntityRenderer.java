package commoble.hyperbox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;

import commoble.hyperbox.blocks.HyperboxBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class HyperboxBlockEntityRenderer implements BlockEntityRenderer<HyperboxBlockEntity>
{

	public HyperboxBlockEntityRenderer(BlockEntityRendererProvider.Context context)
	{
	}

	@Override
	public void render(HyperboxBlockEntity hyperbox, float partialTicks, PoseStack matrixStackIn, MultiBufferSource bufferIn, int combinedLightIn, int combinedOverlayIn)
	{
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		Component customName = hyperbox.getCustomName();
		if (player != null && customName != null && shouldRenderNameplate(hyperbox, player))
		{
			renderName(mc, hyperbox, customName, matrixStackIn, bufferIn, combinedLightIn);
		}
	}

	public static boolean shouldRenderNameplate(HyperboxBlockEntity hyperbox, LocalPlayer player)
	{
		double radius = player.isCrouching() ? ClientProxy.clientConfig.nameplateSneakingRenderDistance.get() : ClientProxy.clientConfig.nameplateRenderDistance.get();

		if (radius <= 0)
			return false;

		BlockPos boxPos = hyperbox.getBlockPos();
		double boxX = boxPos.getX() + 0.5D;
		double boxY = boxPos.getY() + 0.5D;
		double boxZ = boxPos.getZ() + 0.5D;
		double distanceSquared = player.distanceToSqr(boxX, boxY, boxZ);
		return distanceSquared < radius * radius;
	}

	public static void renderName(Minecraft mc, HyperboxBlockEntity hyperbox, Component displayNameIn, PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn)
	{
		Font fontRenderer = mc.font;
		Quaternion cameraRotation = mc.getEntityRenderDispatcher().cameraOrientation();
		
		matrixStackIn.pushPose();
		matrixStackIn.translate(0.5F, 1.4F, 0.5F);
		matrixStackIn.mulPose(cameraRotation);
		matrixStackIn.scale(-0.025F, -0.025F, 0.025F);
		Matrix4f matrix4f = matrixStackIn.last().pose();
		float backgroundOpacity = mc.options.getBackgroundOpacity(0.25F);
		int alpha = (int) (backgroundOpacity * 255.0F) << 24;
		float textOffset = -fontRenderer.width(displayNameIn) / 2;
		fontRenderer.drawInBatch(displayNameIn, textOffset, 0F, 553648127, false, matrix4f, bufferIn, false, alpha, packedLightIn);
		fontRenderer.drawInBatch(displayNameIn, textOffset, 0F, -1, false, matrix4f, bufferIn, false, 0, packedLightIn);
		
		matrixStackIn.popPose();
	}

}
