package aaaa0ggmc.ulmodpack.dna;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/**
 * DNA 重组机 TileEntity：
 * <ul>
 *   <li>标准 Forge Energy（FE/RF）受电缓存：{@code 100,000 FE}，最大输入 {@code 500 FE/t}；</li>
 *   <li>5 个物品槽位：槽 0 为采样棉签，槽 1–4 为必选/可选原料；</li>
 *   <li>服务端权威校验原料、电量与可选修饰项（例如绵羊缺少羊毛时附加 {@code {Sheared:1b}}）。</li>
 * </ul>
 */
public class TileEntityDnaRecombinator extends TileEntity {

    public static final int SLOT_SWAB = 0;
    public static final int SLOT_MAT_START = 1;
    public static final int SLOT_COUNT = 5;

    public static final int ENERGY_CAPACITY = 100_000;
    public static final int MAX_RECEIVE = 500;

    private final RecombinatorEnergy energy = new RecombinatorEnergy(ENERGY_CAPACITY, MAX_RECEIVE);
    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_SWAB) {
                return stack.getItem() instanceof ItemDnaSwab;
            }
            return true;
        }
    };

    private static final class RecombinatorEnergy extends EnergyStorage {
        RecombinatorEnergy(int capacity, int maxReceive) {
            super(capacity, maxReceive, 0, 0);
        }

        void setEnergy(int value) {
            this.energy = Math.max(0, Math.min(this.capacity, value));
        }

        boolean consume(int amount) {
            if (this.energy < amount) {
                return false;
            }
            this.energy -= amount;
            return true;
        }
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public int getEnergyStored() {
        return energy.getEnergyStored();
    }

    public int getMaxEnergyStored() {
        return energy.getMaxEnergyStored();
    }

    public void setClientEnergy(int value) {
        energy.setEnergy(value);
    }

    @Nullable
    public DnaRecipe getActiveRecipe() {
        ItemStack swab = inventory.getStackInSlot(SLOT_SWAB);
        String entityId = ItemDnaSwab.getSampledEntityId(swab);
        return DnaRecipeRegistry.get(entityId);
    }

    public int countMatching(DnaRecipe.ItemRequirement req) {
        int total = 0;
        for (int i = SLOT_MAT_START; i < SLOT_COUNT; i++) {
            ItemStack s = inventory.getStackInSlot(i);
            if (req.matches(s)) {
                total += s.getCount();
            }
        }
        return total;
    }

    public int findFirstMatchedMeta(DnaRecipe.ItemRequirement req) {
        for (int i = SLOT_MAT_START; i < SLOT_COUNT; i++) {
            ItemStack s = inventory.getStackInSlot(i);
            if (req.matches(s)) {
                return s.getMetadata();
            }
        }
        return -1;
    }

    public boolean canStartRecombine() {
        DnaRecipe recipe = getActiveRecipe();
        if (recipe == null || energy.getEnergyStored() < recipe.energyFe) {
            return false;
        }
        for (DnaRecipe.ItemRequirement req : recipe.required) {
            if (countMatching(req) < req.count) {
                return false;
            }
        }
        return true;
    }

    public boolean tryStartRecombine(EntityPlayerMP player) {
        DnaRecipe recipe = getActiveRecipe();
        if (recipe == null) {
            player.sendStatusMessage(new TextComponentTranslation("ul.dna.err.no_swab"), true);
            return false;
        }
        if (energy.getEnergyStored() < recipe.energyFe) {
            player.sendStatusMessage(new TextComponentTranslation("ul.dna.err.no_energy",
                    energy.getEnergyStored(), recipe.energyFe), true);
            return false;
        }
        for (DnaRecipe.ItemRequirement req : recipe.required) {
            if (countMatching(req) < req.count) {
                player.sendStatusMessage(new TextComponentTranslation("ul.dna.err.missing_req",
                        req.displayName(), req.count), true);
                return false;
            }
        }

        // 先评估可选修饰项（如羊毛 -> 颜色 / 缺羊毛 -> 没毛羊）
        NBTTagCompound extraNbt = new NBTTagCompound();
        boolean[] optMatched = new boolean[recipe.optional.size()];
        for (int i = 0; i < recipe.optional.size(); i++) {
            DnaRecipe.OptionalModifier opt = recipe.optional.get(i);
            boolean hasOpt = countMatching(opt.requirement) >= opt.requirement.count;
            int meta = hasOpt ? findFirstMatchedMeta(opt.requirement) : -1;
            optMatched[i] = hasOpt;
            opt.applyToNbt(extraNbt, hasOpt, meta);
        }

        // 扣除必选原料、已满足的可选原料、棉签与电量
        for (DnaRecipe.ItemRequirement req : recipe.required) {
            consumeMatching(req);
        }
        for (int i = 0; i < recipe.optional.size(); i++) {
            if (optMatched[i]) {
                consumeMatching(recipe.optional.get(i).requirement);
            }
        }
        inventory.extractItem(SLOT_SWAB, 1, false);
        energy.consume(recipe.energyFe);
        markDirty();

        player.closeScreen();
        DnaRecombineHandler.scheduleRecombine(player, recipe.entityId, extraNbt, recipe.weaknessTicks);
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.ENCHANTMENT_TABLE,
                    pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D,
                    30, 0.4D, 0.6D, 0.4D, 0.05D);
            world.playSound(null, pos, SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 0.6F, 1.3F);
        }
        return true;
    }

    private void consumeMatching(DnaRecipe.ItemRequirement req) {
        int left = req.count;
        for (int i = SLOT_MAT_START; i < SLOT_COUNT && left > 0; i++) {
            ItemStack s = inventory.getStackInSlot(i);
            if (req.matches(s)) {
                int take = Math.min(left, s.getCount());
                inventory.extractItem(i, take, false);
                left -= take;
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("Inventory", inventory.serializeNBT());
        compound.setInteger("Energy", energy.getEnergyStored());
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("Inventory")) {
            inventory.deserializeNBT(compound.getCompoundTag("Inventory"));
        }
        energy.setEnergy(compound.getInteger("Energy"));
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY || capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return (T) energy;
        }
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) inventory;
        }
        return super.getCapability(capability, facing);
    }
}
