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

### 僵尸转化仪式（纯数据，机制在 morphext）

僵尸族形态（僵尸/尸壳/僵尸猪人/僵尸村民）之间的转化靠**仪式**：用特定物品、在特定
群系、以特定方式死一次，死亡会被转化取代（无死亡界面、不掉落）。不限起始形态，
仪式决定目标形态：

| 仪式 | 变成 | 物品 | 群系 | 死法 |
| --- | --- | --- | --- | --- |
| 沙葬 | 尸壳 | 沙子（埋进去） | 沙漠 | 窒息而死 |
| 水祭 | 僵尸 | 水瓶（手持，消耗剩空瓶） | 水域（河流/海洋/沼泽） | 溺水而死 |
| 火祭 | 僵尸猪人 | 金锭（手持） | 任意维度（主世界可完成） | 被烧死 |
| 村祭 | 僵尸村民 | 绿宝石（主手） | 村庄 32 格内 | 被铁傀儡打死 |

- **本模组不含仪式机制**：判定与执行是 morphext 核心的通用「死亡转化管线 + 数据驱动
  规则引擎」（扫全模组 `assets/<modid>/morphext/conversion_rules.json` + config 覆盖）。
  本模组只提供内容：四条规则的配置见 `assets/aaaa0ggmc_ul_modpack/morphext/conversion_rules.json`，
  形态类别（`ul:undead`）见同目录 `form_categories.json`。
- 沙葬成功后破沙而出（清掉规则自己声明的掩埋方块 + 确保头顶净空）。
- **尸壳特性**：免疫窒息伤害。玩家形态侧走 morphext 已有的 `damageImmunities` 数据通道
  （`morphext/morph_abilities.json`），实体侧是线内容 `zombie/ZombieLineTraits`。
  **僵尸猪人特性**：免疫火焰环境伤害（inFire/onFire/lava/hotFloor，形态适配转化环境——
  被火烧成猪人后不再怕火）。
- 仪式未达成且玩家是不死者形态时，服务端日志打
  `ritual miss: src=… form=… categories=[…] best=<规则id> missing=[…]`（条件排障）。

### 尸群领导 / 尸潮（`zombie/horde/`）

招募僵尸随从、带队屠村的玩法线（技能 `ul:horde_leadership`，形态类别 `ul:undead`）。
触发语义（2026-11-03 重做）：

- **对着僵尸使用技能 = 招募**：空手右键僵尸族实体 → 客户端上报目标，服务端校验距离/权限后
  招募它。按**说服度匹配**：说服度 <50 须先打残（生命低于 `persuasion_hurt_below` 比例），
  <20 还要搭腐肉；人数上限 `followCap`。已去掉旧的「必须夜晚」「必须移动」门槛，保留冷却。
- **对着空气潜行使用技能 = 解散当前队伍**（明确动作；进行中的尸潮一并收尾）。
- **B 键**（morphext 状态页 `GuiAbilityStatus`）：「尸群队伍」分节显示随从数量/上限、
  说服度、尸潮状态与逐个随从（名称/形态、血量）。数据来自服务端 S2C 队伍快照
  （`HordeNetwork` TeamMessage，随从变化即时推 + 5 秒节流），界面读客户端缓存。
- **发动屠村 / 震地**：能力轮盘（`horde_launch` / `titan_slam`）或 `/ulpack tide start`；
  屠村成功计数 +1 并涨说服度，玩家死亡或走远则失败/中止。

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
/ulpack status            查看护符槽位、当前/已获形态、群系与 BiomeDictionary 标签
/ulpack equip             把安卡护符放进 AMULET 槽（测试免 GUI）
/ulpack infect [entity]   立刻走一次感染变身（默认 minecraft:zombie）
/ulpack killby [entity]   让指定实体"杀死"自己（完整死亡流程，验收死亡取消）
/ulpack convert [entity]  直接变成指定形态（默认 minecraft:husk，仪式验收用）
/ulpack death <cause>     让自己死于指定伤害（in_wall|drown|fire|lava|cactus，仪式验收用）
/ulpack ritual list       列出全部转化规则（morphext 注册表，数据驱动）
/ulpack ritual test <id>  忽略条件直接触发某条规则（调试）
/ulpack ritual debug      打印当前形态/类别与每条规则缺失的条件
/ulpack ritual reload     重载规则/类别配置
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
