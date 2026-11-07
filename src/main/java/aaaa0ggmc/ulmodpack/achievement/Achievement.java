package aaaa0ggmc.ulmodpack.achievement;

/**
 * 成就定义：稳定 id + 本地化标题/描述 lang 键（内容，非强度数值，不走 ul-strength）。
 */
public final class Achievement {

    /** 稳定 id（持久化与查询键，勿改名）。 */
    public final String id;
    /** 标题 lang 键（zh/en）。 */
    public final String nameKey;
    /** 描述 lang 键（zh/en）。 */
    public final String descKey;

    public Achievement(String id, String nameKey, String descKey) {
        this.id = id;
        this.nameKey = nameKey;
        this.descKey = descKey;
    }
}
