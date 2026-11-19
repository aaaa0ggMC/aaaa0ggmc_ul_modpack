package aaaa0ggmc.ulmodpack.dna;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 单种白名单生物的 DNA 重组配方定义（数据驱动，自 {@code dna_recipes.json} 加载）。
 */
public final class DnaRecipe {

    public static final class ItemRequirement {
        public final String itemId;
        /** -1 表示任意 metadata（通配）。 */
        public final int meta;
        public final int count;

        public ItemRequirement(String itemId, int meta, int count) {
            this.itemId = itemId;
            this.meta = meta;
            this.count = Math.max(1, count);
        }

        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            ResourceLocation reg = stack.getItem().getRegistryName();
            if (reg == null || !reg.toString().equals(itemId)) {
                return false;
            }
            return meta < 0 || stack.getMetadata() == meta;
        }

        public String displayName() {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item == null) {
                return itemId;
            }
            ItemStack sample = new ItemStack(item, 1, Math.max(0, meta));
            return sample.getDisplayName();
        }
    }

    public static final class OptionalModifier {
        public final ItemRequirement requirement;
        public final Map<String, Object> whenMissing;
        public final Map<String, Object> whenPresent;
        @Nullable
        public final String copyMetaToByte;
        @Nullable
        public final String missingHintKey;
        @Nullable
        public final String presentHintKey;

        public OptionalModifier(ItemRequirement requirement,
                                Map<String, Object> whenMissing,
                                Map<String, Object> whenPresent,
                                @Nullable String copyMetaToByte,
                                @Nullable String missingHintKey,
                                @Nullable String presentHintKey) {
            this.requirement = requirement;
            this.whenMissing = whenMissing == null ? Collections.emptyMap() : whenMissing;
            this.whenPresent = whenPresent == null ? Collections.emptyMap() : whenPresent;
            this.copyMetaToByte = copyMetaToByte;
            this.missingHintKey = missingHintKey;
            this.presentHintKey = presentHintKey;
        }

        public void applyToNbt(NBTTagCompound target, boolean present, int matchedMeta) {
            Map<String, Object> map = present ? whenPresent : whenMissing;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Boolean) {
                    target.setBoolean(e.getKey(), (Boolean) v);
                } else if (v instanceof Number) {
                    target.setInteger(e.getKey(), ((Number) v).intValue());
                } else if (v instanceof String) {
                    target.setString(e.getKey(), (String) v);
                }
            }
            if (present && copyMetaToByte != null && !copyMetaToByte.isEmpty() && matchedMeta >= 0) {
                target.setByte(copyMetaToByte, (byte) matchedMeta);
            }
        }
    }

    public final String entityId;
    public final int energyFe;
    public final int weaknessTicks;
    public final List<ItemRequirement> required;
    public final List<OptionalModifier> optional;

    public DnaRecipe(String entityId, int energyFe, int weaknessTicks,
                     List<ItemRequirement> required, List<OptionalModifier> optional) {
        this.entityId = entityId;
        this.energyFe = Math.max(0, energyFe);
        this.weaknessTicks = Math.max(20, weaknessTicks);
        this.required = Collections.unmodifiableList(required);
        this.optional = Collections.unmodifiableList(optional);
    }
}
