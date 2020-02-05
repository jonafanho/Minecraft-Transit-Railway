package mtr.slot;

import mtr.item.ItemTemplate;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class SlotTemplateOnly extends Slot {

	public SlotTemplateOnly(IInventory inventoryIn, int index, int xPosition, int yPosition) {
		super(inventoryIn, index, xPosition, yPosition);
	}

	@Override
	public boolean isItemValid(ItemStack stack) {
		return isValid(stack);
	}

	public static boolean isValid(ItemStack stack) {
		return stack.getItem() instanceof ItemTemplate;
	}
}