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
 * 槽 0 = 棉签槽；槽 1–4 = 原料槽；下方为玩家背包（3×9）与快捷栏（1×9）。
 * 通过双 16 位 windowProperty 同步 32 位 FE 电量，避免超过 32767 截断。
 */
public class ContainerDnaRecombinator extends Container {

    private final TileEntityDnaRecombinator tile;
    private int lastEnergy = -1;

    public ContainerDnaRecombinator(InventoryPlayer playerInv, TileEntityDnaRecombinator tile) {
        this.tile = tile;
        ItemStackHandler inv = tile.getInventory();

        // 棉签槽
        addSlotToContainer(new SlotItemHandler(inv, TileEntityDnaRecombinator.SLOT_SWAB, 34, 24) {
            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack.getItem() instanceof ItemDnaSwab;
            }
        });

        // 4 个原料槽（2×2）
        addSlotToContainer(new SlotItemHandler(inv, 1, 25, 50));
        addSlotToContainer(new SlotItemHandler(inv, 2, 43, 50));
        addSlotToContainer(new SlotItemHandler(inv, 3, 25, 68));
        addSlotToContainer(new SlotItemHandler(inv, 4, 43, 68));

        // 玩家背包（3×9）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
            }
        }
        // 玩家快捷栏（1×9）
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 160));
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
