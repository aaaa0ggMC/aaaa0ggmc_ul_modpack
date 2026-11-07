package aaaa0ggmc.ulmodpack.achievement;

import net.minecraft.util.ResourceLocation;

/**
 * 成就定义：稳定 id + 原版 advancement 定位（内容，非强度数值，不走 ul-strength）。
 *
 * <p>2026-11-07 起成就本体走 1.12.2 原版 {@code Advancement}（资源 JSON
 * {@code assets/aaaa0ggmc_ul_modpack/advancements/<name>.json} + 代码 grant），
 * 标题/描述用原版 lang 键 {@code advancements.<modid>.<name>.title/description}，
 * 本类只留 ul 侧的授予/查询定位，不再自带标题键与 toast。</p>
 */
public final class Achievement {

    /** 稳定 id（ul 侧授予/查询键，勿改名）。 */
    public final String id;
    /** 对应原版 advancement id（= JSON 所在路径 {@code <modid>:<name>}）。 */
    public final ResourceLocation advancementId;
    /** criterion 名（JSON criteria 键；授予时按这个名字 grantCriterion）。 */
    public final String criterion;

    public Achievement(String id, ResourceLocation advancementId, String criterion) {
        this.id = id;
        this.advancementId = advancementId;
        this.criterion = criterion;
    }
}
