package net.commoble.hyperbox.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.blocks.C2SSaveHyperboxPacket;
import net.commoble.hyperbox.blocks.HyperboxMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

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
		
		this.useDefaultNameCheckbox = Checkbox.builder(USE_DEFAULT_NAME_LABEL, this.font)
			.pos(this.width/2 - 152, 80)
			.selected(true)
			.onValueChange((box, selected) -> {
				HyperboxScreen.this.nameEdit.setEditable(!selected);
				if (selected)
				{
					HyperboxScreen.this.nameEdit.setValue(HyperboxScreen.this.dimensionEdit.getValue());
				}
			})
			.build();
		this.addRenderableWidget(this.useDefaultNameCheckbox);
		
		this.nameEdit = new EditBox(this.font, this.width/2 - 152, 120, 300, 20, EDIT_NAME_LABEL);
		this.nameEdit.setMaxLength(120);
		this.nameEdit.setEditable(false);
		this.addRenderableWidget(this.nameEdit);
		
		this.saveAndEnterButton = Button.builder(SAVE_AND_ENTER_LABEL, b -> this.onSave(true))
			.bounds(this.width/2 - 152, 160, 300, 20)
			.build();
		this.saveAndEnterButton.active = false;
		this.addRenderableWidget(this.saveAndEnterButton);
		
		this.saveAndExitButton = Button.builder(SAVE_AND_EXIT_LABEL, b -> this.onSave(false))
			.bounds(this.width/2 -152, 185, 300, 20)
			.build();
		this.saveAndExitButton.active = false;
		this.addRenderableWidget(this.saveAndExitButton);
		
		this.cancelButton = Button.builder(CANCEL_LABEL, b -> this.minecraft.player.closeContainer())
			.bounds(this.width/2 -152, 210, 300, 20)
			.build();
		this.addRenderableWidget(this.cancelButton);
		this.setInitialFocus(this.dimensionEdit);
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
			FocusNavigationEvent tabEvent = new FocusNavigationEvent.TabNavigation(!hasShiftDown());
			ComponentPath nextFocusPath = super.nextFocusPath(tabEvent);
			if (nextFocusPath == null)
			{
				ComponentPath currentFocusPath = this.getCurrentFocusPath();
				if (currentFocusPath != null)
				{
					currentFocusPath.applyFocus(false);
				}
				nextFocusPath = super.nextFocusPath(tabEvent);
			}
			
			if (nextFocusPath != null)
			{
				this.changeFocus(nextFocusPath);
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
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
	{
		super.render(graphics, mouseX, mouseY, partialTicks);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 16777215);
		graphics.drawString(this.font, EDIT_DIMENSION_LABEL, this.width/2 - 152, 30, 10526880);
		graphics.drawString(this.font, EDIT_NAME_LABEL, this.width/2 - 152, 110, 10526880);
		if (!isDimensionIdFree(this.dimensionEdit.getValue()))
		{
			graphics.drawString(this.font, DIMENSION_ID_IN_USE, this.width/2 - 152, 65, 0xFF0000);
		}
		
		for (Renderable renderable : this.renderables)
		{
			renderable.render(graphics, mouseX, mouseY, partialTicks);
		}
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY)
	{
		
	}
	
	protected void onSave(boolean enterImmediate)
	{
        this.minecraft.setScreen((Screen)null);
		this.saveAndEnterButton.active = false;
		this.saveAndExitButton.active = false;
		String dimensionId = this.dimensionEdit.getValue();
		String name = this.nameEdit.getValue();
		PacketDistributor.SERVER.noArg().send(new C2SSaveHyperboxPacket(dimensionId, name, enterImmediate));
	}
	
	protected boolean isDimensionIdValid(String dimensionId)
	{
		return ResourceLocation.isValidPath(dimensionId);
	}
	
	@SuppressWarnings("resource")
	public static boolean isDimensionIdFree(String dimensionId)
	{
		return !Minecraft.getInstance().player.connection.levels()
			.contains(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Hyperbox.MODID, dimensionId)));
	}
}
