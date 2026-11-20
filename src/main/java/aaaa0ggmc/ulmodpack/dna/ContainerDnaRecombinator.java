package aaaa0ggmc.ulmodpack.dna;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * DNA 重组机容器：
 * 槽 0 = 棉签槽；槽 1–9 = 3×3 无序原料槽；下方为标准玩家背包（3×9）与快捷栏（1×9）。
 * 通过双 16 位 windowProperty 同步 32 位 FE 电量，避免超过 32767 截断。
 */
public class ContainerDnaRecombinator extends Container {

    private final TileEntityDnaRecombinator tile;
    private int lastEnergy = -1;

    public ContainerDnaRecombinator(InventoryPlayer playerInv, TileEntityDnaRecombinator tile) {
        this.tile = tile;
        ItemStackHandler inv = tile.getInventory();

        // 棉签槽（位于 3×3 网格正上方居中）
        addSlotToContainer(new SlotItemHandler(inv, TileEntityDnaRecombinator.SLOT_SWAB, 40, 20) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack.getItem() instanceof ItemDnaSwab;
            }
        });

        // 9 个无序原料槽（3×3）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIdx = TileEntityDnaRecombinator.SLOT_MAT_START + row * 3 + col;
                addSlotToContainer(new SlotItemHandler(inv, slotIdx, 22 + col * 18, 42 + row * 18));
            }
        }

        // 玩家背包（3×9，居中于 xSize=200）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 19 + col * 18, 114 + row * 18));
            }
        }
        // 玩家快捷栏（1×9）
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 19 + col * 18, 172));
        }
    }

    public TileEntityDnaRecombinator getTile() {
        return tile;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int energy = tile.getEnergyStored();
        if (energy != lastEnergy) {
            lastEnergy = energy;
            int low = energy & 0xFFFF;
            int high = (energy >>> 16) & 0xFFFF;
            for (IContainerListener listener : listeners) {
                listener.sendWindowProperty(this, 0, low);
                listener.sendWindowProperty(this, 1, high);
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int id, int data) {
        int cur = tile.getEnergyStored();
        if (id == 0) {
            tile.setClientEnergy((cur & 0xFFFF0000) | (data & 0xFFFF));
        } else if (id == 1) {
            tile.setClientEnergy((cur & 0x0000FFFF) | ((data & 0xFFFF) << 16));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return tile != null && !tile.isInvalid()
                && playerIn.getDistanceSq(tile.getPos().add(0.5D, 0.5D, 0.5D)) <= 64.0D;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getStack();
        ItemStack copy = stack.copy();

        if (index < TileEntityDnaRecombinator.SLOT_COUNT) {
            if (!mergeItemStack(stack, TileEntityDnaRecombinator.SLOT_COUNT, inventorySlots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.getItem() instanceof ItemDnaSwab) {
                if (!mergeItemStack(stack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!mergeItemStack(stack, TileEntityDnaRecombinator.SLOT_MAT_START,
                    TileEntityDnaRecombinator.SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return copy;
    }
}
