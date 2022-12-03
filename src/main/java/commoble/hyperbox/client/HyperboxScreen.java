package commoble.hyperbox.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.blocks.HyperboxMenu;
import commoble.hyperbox.network.C2SSaveHyperboxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Widget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class HyperboxScreen extends AbstractContainerScreen<HyperboxMenu>
{
	public static final Component EDIT_DIMENSION_LABEL = Component.translatable("menu.hyperbox.edit_dimension");
	public static final Component EDIT_NAME_LABEL = Component.translatable("menu.hyperbox.edit_name");
	public static final Component USE_DEFAULT_NAME_LABEL = Component.translatable("menu.hyperbox.use_default_name_checkbox");
	public static final Component SAVE_AND_ENTER_LABEL = Component.translatable("menu.hyperbox.save_and_enter");
	public static final Component SAVE_AND_EXIT_LABEL = Component.translatable("menu.hyperbox.save_and_exit");
	public static final Component CANCEL_LABEL = Component.translatable("gui.cancel");
	public static final Component DIMENSION_ID_IN_USE = Component.translatable("menu.hyperbox.dimension_id_in_use");
	
	private EditBox dimensionEdit;
	private EditBox nameEdit;
	private Checkbox useDefaultNameCheckbox;
	private Button saveAndEnterButton;
	private Button saveAndExitButton;
	private Button cancelButton;

	public HyperboxScreen(HyperboxMenu menu, Inventory playerInventory, Component component)
	{
		super(menu, playerInventory, component);
	}
	
	@Override
	protected void init()
	{
		super.init();
		
		this.dimensionEdit = new EditBox(this.font, this.width/2 - 152, 40, 300, 20, EDIT_DIMENSION_LABEL);
		this.dimensionEdit.setFilter(this::isDimensionIdValid);
		this.dimensionEdit.setResponder(dimensionId -> {
			boolean savable = dimensionId != null && !dimensionId.isBlank() && isDimensionIdFree(dimensionId);
			this.saveAndEnterButton.active = savable;
			this.saveAndExitButton.active = savable;
			if (this.useDefaultNameCheckbox.selected())
			{
				this.nameEdit.setValue(dimensionId);
			}
		});
		this.dimensionEdit.setMaxLength(120);
		this.addRenderableWidget(this.dimensionEdit);
		
		this.useDefaultNameCheckbox = new Checkbox(this.width/2 - 152, 80, 20, 20, USE_DEFAULT_NAME_LABEL, true)
		{
			@Override
			public void onPress()
			{
				super.onPress();
				HyperboxScreen.this.nameEdit.setEditable(!this.selected());
				if (this.selected())
				{
					HyperboxScreen.this.nameEdit.setValue(HyperboxScreen.this.dimensionEdit.getValue());
				}
			}
		};
		this.addRenderableWidget(this.useDefaultNameCheckbox);
		
		this.nameEdit = new EditBox(this.font, this.width/2 - 152, 120, 300, 20, EDIT_NAME_LABEL);
		this.nameEdit.setMaxLength(120);
		this.nameEdit.setEditable(false);
		this.addRenderableWidget(this.nameEdit);
		
		this.saveAndEnterButton = new Button(this.width/2 -152, 160, 300, 20, SAVE_AND_ENTER_LABEL, b ->
		{
			this.onSave(true);
		});
		this.saveAndEnterButton.active = false;
		this.addRenderableWidget(this.saveAndEnterButton);
		
		this.saveAndExitButton = new Button(this.width/2 -152, 185, 300, 20, SAVE_AND_EXIT_LABEL, b ->
		{
			this.onSave(false);
		});
		this.saveAndExitButton.active = false;
		this.addRenderableWidget(this.saveAndExitButton);
		
		this.cancelButton = new Button(this.width/2 -152, 210, 300, 20, CANCEL_LABEL, b ->
		{
			this.minecraft.player.closeContainer();
		});
		this.addRenderableWidget(this.cancelButton);
		this.setInitialFocus(this.dimensionEdit);
	}
	
	@Override
	protected void containerTick()
	{
		super.containerTick();
		this.dimensionEdit.tick();
		this.nameEdit.tick();
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int mods)
	{
		// bypass AbstractContainerScreen#keyPressed so we don't close on inventory keybind
		if (key == InputConstants.KEY_ESCAPE && this.shouldCloseOnEsc())
		{
			this.onClose();
			return true;
		}
		else if (key == InputConstants.KEY_TAB)
		{
			boolean flag = !hasShiftDown();
			if (!this.changeFocus(flag))
			{
				this.changeFocus(flag);
			}

			return false;
		}
		else if (this.getFocused() != null && this.getFocused().keyPressed(key, scanCode, mods))
		{
			return true;
		}
		else if (key != InputConstants.KEY_RETURN && key != InputConstants.KEY_NUMPADENTER)
		{
			return false;
		}
		else
		{
			if (this.dimensionEdit.getValue().isBlank())
			{
				this.onClose();
			}
			else if (this.isDimensionIdValid(this.dimensionEdit.getValue()))
			{
				this.onSave(true);
			}
			return true;
		}
	}
	
	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks)
	{
		this.renderBackground(poseStack);
		drawCenteredString(poseStack, this.font, this.title, this.width / 2, 10, 16777215);
		drawString(poseStack, this.font, EDIT_DIMENSION_LABEL, this.width/2 - 152, 30, 10526880);
		drawString(poseStack, this.font, EDIT_NAME_LABEL, this.width/2 - 152, 110, 10526880);
		if (!isDimensionIdFree(this.dimensionEdit.getValue()))
		{
			drawString(poseStack, this.font, DIMENSION_ID_IN_USE, this.width/2 - 152, 65, 0xFF0000);
		}
		
		for (Widget widget : this.renderables)
		{
			widget.render(poseStack, mouseX, mouseY, partialTicks);
		}
	}

	@Override
	protected void renderBg(PoseStack poseStack, float partialTicks, int mouseX, int mouseY)
	{
		
	}
	
	protected void onSave(boolean enterImmediate)
	{
        this.minecraft.setScreen((Screen)null);
		this.saveAndEnterButton.active = false;
		this.saveAndExitButton.active = false;
		String dimensionId = this.dimensionEdit.getValue();
		String name = this.nameEdit.getValue();
		Hyperbox.CHANNEL.sendToServer(new C2SSaveHyperboxPacket(dimensionId, name, enterImmediate));
	}
	
	protected boolean isDimensionIdValid(String dimensionId)
	{
		return ResourceLocation.isValidPath(dimensionId);
	}
	
	@SuppressWarnings("resource")
	public static boolean isDimensionIdFree(String dimensionId)
	{
		return !Minecraft.getInstance().player.connection.levels()
			.contains(ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(Hyperbox.MODID, dimensionId)));
	}
}
