# aaaa0ggmc_ul_modpack —— Unlimited Life 整合包内容主模组

**UL = Unlimited Life。** 本模组是整合包的“内容大箱子”：整合包的**额外内容**——
物品、机制、玩法系统、第三方联动——全部收在这里，按包（package）分区组织，
随整合包持续扩充。双实例（Forge+OptiFine / Cleanroom+Actinium）同一 jar；
1.12.2 Forge API（开发 2847 userdev，运行 2860 通用）· Java 8 字节码。

**往里加内容**：在 `src/main/java/aaaa0ggmc/ulmodpack/` 下按主题开新包；
物品注册挂进 `ItemRegistryHandler`，事件在 `CommonProxy.preInit`、
指令在 `UlModpack.serverStarting` 挂上；第三方模组适配只放本模组，
morphext 核心保持通用。下面是当前已装的内容模块。

## 当前内容

### 安卡护符（`aaaa0ggmc_ul_modpack:ankh_amulet`）

- **槽位**：Baubles `AMULET`（脖子）。佩戴时 `+2` 护甲（属性修饰符，摘下即失效）。
- **配方**（暂定，待细化）：

  ```
  O#O      O = 金粒 (gold_nugget)
  #1#      # = 金锭 (gold_ingot)
  O#O      1 = 腐肉 (rotten_flesh)
  ```

- **感染变身**：佩戴时被**僵尸一族**（`EntityZombie` 及其子类：僵尸、尸壳、
  僵尸村民、僵尸猪人、巨人）杀死——
  1. 取消死亡：无死亡界面、无掉落、无死亡消息（LivingDeathEvent 取消，1.12.2 语义）；
  2. 化身为击杀者的形态（Metamorph `MorphAPI.acquire` + `morph(force)`，与 Metamorph
     击杀获取形态同一条构建路径）；
  3. 回满生命（形态会改写最大生命）；
  4. 护符碎裂：金色尘屑粒子 + 图腾光效 + 玻璃碎裂/尸毒感染音效 + 文案；
  5. 解锁进度「被感染」（`aaaa0ggmc_ul_modpack:infected`，challenge 弹窗）；
  6. 在 morphext 玩家数据记录感染状态（`aaaa0ggmc_ul_modpack:infection`，失败不影响主流程）。

### FTBQuests 形态检测任务注入

任务类型 `aaaa0ggmc_ul_modpack:morph`：检测玩家「变成了什么」，每 10 tick 自动判定。

| 字段 | 说明 |
| --- | --- |
| `morphs` | 形态 id 列表（任一命中即完成；`zombie` 自动补 `minecraft:` 前缀） |
| `acquired` | `true` = 检查“曾获得过的形态”；缺省 = 检查“当前形态” |

任务源（`quests/*.json`）写法：

```jsonc
"morph:minecraft:zombie,minecraft:husk"                          // 简写
{ type: "morph", morphs: ["minecraft:zombie"], acquired: true }  // 对象
```

形态真值查询（questlive 后端）：`{"q":"morph","id":"zombie"}`、`{"q":"morphes"}`；
questshell 里 `morph <id>` / `morph -l`，`check` 会对 morph 引用做运行时真值校验。

**分层说明**：按 morphext 的边界约定，第三方模组适配不进 morphext 核心。FTBQuests 联动
落在本模组；若日后体量变大，可整体迁往 `morphext-patch-ftbquests`（形态检测只依赖
Metamorph 公共 API，不依赖本模组其它部分）。

## 调试指令（OP）

```
/ulpack status            查看护符槽位、当前形态、已获形态数
/ulpack equip             把安卡护符放进 AMULET 槽（测试免 GUI）
/ulpack infect [entity]   立刻走一次感染变身（默认 minecraft:zombie）
/ulpack killby [entity]   让指定实体“杀死”自己（完整死亡流程，验收死亡取消）
```

## 构建与安装

```bash
cd dev/aaaa0ggmc_ul_modpack
./gradlew build                      # FG2.3 + Gradle 4.9 + JDK8（gradle.properties 指定）
cp build/libs/aaaa0ggmc_ul_modpack-1.0.0.jar ../../_shared/mods/common/
cd ../.. && ./scripts/sync-mods.sh   # 硬链接同步两个实例（并转换任务书）
```

## 依赖

| modid | 用途 |
| --- | --- |
| `baubles` | AMULET 槽位（1.12.2 的 baubles/trinkets 标准） |
| `metamorph` | 形态引擎（MorphAPI / MorphManager） |
| `aaaa0ggmc_morph_extension` | morphext 玩家数据层（感染状态） |
| `ftbquests` | 形态检测任务类型注册 |

## 文案（待用户细化，当前为初稿）

见 `assets/aaaa0ggmc_ul_modpack/lang/{zh_CN,en_US}.lang`：物品名与 5 行 lore、
进度标题/描述、碎裂与感染事件文案、任务显示名。贴图为 16x16 自绘（`textures/items/`）。
