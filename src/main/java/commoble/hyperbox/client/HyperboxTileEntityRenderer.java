package commoble.hyperbox.client;

import com.mojang.blaze3d.matrix.MatrixStack;

import commoble.hyperbox.blocks.HyperboxTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.text.ITextComponent;

public class HyperboxTileEntityRenderer extends TileEntityRenderer<HyperboxTileEntity>
{

	public HyperboxTileEntityRenderer(TileEntityRendererDispatcher rendererDispatcherIn)
	{
		super(rendererDispatcherIn);
	}

	@Override
	public void render(HyperboxTileEntity hyperbox, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int combinedLightIn, int combinedOverlayIn)
	{
		Minecraft mc = Minecraft.getInstance();
		ClientPlayerEntity player = mc.player;
		ITextComponent customName = hyperbox.getCustomName();
		if (player != null && customName != null && shouldRenderNameplate(hyperbox, player))
		{
			renderName(mc, hyperbox, customName, matrixStackIn, bufferIn, combinedLightIn);
		}
	}

	public static boolean shouldRenderNameplate(HyperboxTileEntity hyperbox, ClientPlayerEntity player)
	{
		double radius = player.isCrouching() ? ClientProxy.clientConfig.nameplateSneakingRenderDistance.get() : ClientProxy.clientConfig.nameplateRenderDistance.get();

		if (radius <= 0)
			return false;

		BlockPos boxPos = hyperbox.getPos();
		double boxX = boxPos.getX() + 0.5D;
		double boxY = boxPos.getY() + 0.5D;
		double boxZ = boxPos.getZ() + 0.5D;
		double distanceSquared = player.getDistanceSq(boxX, boxY, boxZ);
		return distanceSquared < radius * radius;
	}

	public static void renderName(Minecraft mc, HyperboxTileEntity hyperbox, ITextComponent displayNameIn, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn)
	{
		FontRenderer fontRenderer = mc.fontRenderer;
		Quaternion cameraRotation = mc.getRenderManager().getCameraOrientation();
		
		matrixStackIn.push();
		matrixStackIn.translate(0.5F, 1.4F, 0.5F);
		matrixStackIn.rotate(cameraRotation);
		matrixStackIn.scale(-0.025F, -0.025F, 0.025F);
		Matrix4f matrix4f = matrixStackIn.getLast().getMatrix();
		float backgroundOpacity = mc.gameSettings.getTextBackgroundOpacity(0.25F);
		int alpha = (int) (backgroundOpacity * 255.0F) << 24;
		float textOffset = -fontRenderer.getStringPropertyWidth(displayNameIn) / 2;
		fontRenderer.func_243247_a(displayNameIn, textOffset, 0F, 553648127, false, matrix4f, bufferIn, false, alpha, packedLightIn);
		fontRenderer.func_243247_a(displayNameIn, textOffset, 0F, -1, false, matrix4f, bufferIn, false, 0, packedLightIn);
		
		matrixStackIn.pop();
	}

}
