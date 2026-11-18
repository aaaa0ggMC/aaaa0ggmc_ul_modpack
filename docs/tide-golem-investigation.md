# 尸潮铁傀儡召唤排查（horde_launch / 4 村民不刷傀儡）

> 调查时间 2026-11-07，只读调查，未改任何代码。范围：`dev/aaaa0ggmc_ul_modpack`（尸潮实现）、
> `dev/morph-extension`（通用机制，只读）、`reference/`（MC 1.12.2 + Forge 2847/2860 反编译源码）、
> 游戏实例日志与存档（`~/Games/Minecraft/shaping_the_world_cr`）。

## 0. 结论（TL;DR）

**真 bug，不是测试条件问题。** 测试者「尸潮期间村庄应刷出铁傀儡」的期望与设计一致（M6 守村傀儡，
`HordeManager.java:74-75/496-500`），「4 村民」只是**发动下限**、与傀儡无关；但整个 `HordeEvents`
**从未生效过**——`ZombieLine.register()` 用 `bus.register(new HordeEvents())` 注册**实例**，而
`HordeEvents` 的 6 个 `@SubscribeEvent` 方法**全是 static**。Forge 1.12.2 `EventBus.register(Object)`
对「实例 + 静态方法」组合**静默跳过**（`EventBus.java:84-86`），于是 `onWorldTick → HordeManager.tick
→ maybeSpawnGolem` 整条链路死掉：尸潮 active 期 18 秒以上 `LastGolem` 纹丝不动（存档实证，见 §5），
不刷傀儡、不判成功/中止、不发作战指令、不掉热忱的腐肉。

测试口径需纠正一处：不存在「村民恐慌/受伤召唤傀儡」的触发器（本项目与原版 1.12.2 都没有，
那是 1.14+ 的语义）；傀儡是**尸潮 active 期间按计时器刷出**的守村力量，与村民数/恐慌无关。

## 1. 尸潮发动完整流程

入口统一为能力 `horde_command`（能力轮盘释放，服务端权威链路）：

1. 客户端射线选目标：`HordeAbilities.CommandAbility.clientPickTarget`（`HordeAbilities.java:79-82`）
   → `HordeManager.findLookedLivingId`（`HordeManager.java:254-257`，任意活体，只交实体 id）。
2. 服务端 `activate`（`HordeAbilities.java:85-150`）按「目标 + 潜行」分派：
   - 不潜行 + 僵尸族目标 → 招募 `tryRecruitAt`（`HordeManager.java:108-163`，说服度/腐肉/上限规则）；
   - 潜行 + 队员目标 → 踢出 `kickFollower`（`HordeManager.java:336-344`）；
   - **潜行 + 无目标 → 发动/解散 `launchOrDisband`（`HordeAbilities.java:139` → `HordeManager.java:307-325`）**；
   - 其它组合 → Actionbar 失败提示。
3. 发动条件（`launchOrDisband`，`HordeManager.java:310-321`，全部满足才 `launch`）：
   - `ZombieForms.hasLeadership(player)`（尸群领导技能）且 `isUndead(player)`（不死者形态）；
   - `countFollowers(player) > 0`（**64 格**内自有随从，`HordeManager.java:743-754`，
     半径键 `follower_count_radius`）；
   - `findVillageCenter(player) != null`（见 §2）；
   - 当前无进行中尸潮（`!entry.active`）；发动冷却 30 秒（固定钉子，冷却中只提示、不解散）。
   - **任一不满足 → `disband`（`HordeManager.java:323`，含「已在进行中」也解散——类注释明示的合并语义）**。
4. 成功/失败/中止（全部在 `tick` 内判定，`HordeManager.java:458-493`）：
   - 成功：村庄中心 48 格内活村民清零 → `succeed`（`:473-475 → :584-589`，`tidesCleared`+1、说服度 +10）；
   - 中止：玩家远离村庄中心 > `abort_distance`（48）持续 `abort_ticks`（600t=30s）→ `abort`（`:478-488`，不扣说服度）；
   - 失败：玩家死亡 → `onOwnerDeath`（`:592-600`，`LivingDeathEvent` 触发，说服度 −20）；
   - 收尾 `endTide`（`:608-618`）：散开随从 + 清理本场傀儡（`clearTideGolems`，`:564-580`）。
5. 演示/调试入口：`onAirUse`（`HordeManager.java:352-392`，`demo-zombie-tide.sh` 用；已在尸潮中时提示
   `launch.fail.active` 并保留会话）、`/ulpack tide start|stop` → `forceStart/forceStop`（`:623-670`，
   调试版只要 ≥1 村民）。

## 2. 「村民 ≥4」的实现与口径

`HordeManager.findVillageCenter`（`HordeManager.java:796-816`），数值键 `zombie_tide.horde.*`：

- 半径：**以玩家为中心 32 格**（`village_radius=32`，`:797`；AABB grow，`village_min_villagers=4`，`:798`，
  默认值见 `src/main/resources/assets/aaaa0ggmc_ul_modpack/ul_strength/zombie_tide.json:36-37`）。
- 计数口径：`EntityVillager` **已加载实体**（`getEntitiesWithinAABB`），跳过 `isDead`（`:803-806`）；
  僵尸村民不计（类型不匹配）；幼年村民计。**不含未加载区块的村民**。
- **不要求原版村庄结构/门**，不要求村民分属同一村庄，不要求玩家站在村庄里——「村民聚在一起即村庄」
  （`:792-794` 注释明示的设计修订，质心即村庄中心，`:815`）。
- 僵尸村民、已死村民、32 格外/未加载的村民都不算；不足 4 → 返回 null → 发动失败 → 解散队伍。

成功判定用另一把尺子：`countVillagers`（`:818-822`）按**村庄中心 48 格**（`villager_count_radius=48`）
统计（不滤 isDead，仅影响一拍延迟）；中止判定用第三把（玩家↔中心 48 格）。

## 3. 铁傀儡召唤逻辑与原版对照

### 3.1 本项目：尸潮自研「守村傀儡」（时间驱动，无村民触发器）

- 调用点：`HordeManager.tick`（`HordeManager.java:469`，每 `tick_interval=20t` 一拍）→
  `maybeSpawnGolem`（`:532-556`）。**触发条件 = 尸潮 active + 距上次尝试 ≥ `golem_interval_ticks`(100t=5s) +
  存活数 < `golem_max_alive`(默认 9) + 找到合法落点**。与村民数量、恐慌、受伤、天空可见均无关。
- 落点：`findGolemSpawn`（`:831-846`）——村庄中心 `golem_spawn_radius`(16) 内随机 `golem_spawn_tries`(12) 次，
  取 `world.getHeight`（高度图「最上层不透明方块之上第一格空气」，`Chunk.generateHeightMap`
  `reference/.../chunk/Chunk.java:158-178` 存 y+1；`World.getHeight` `World.java:710-718`）且上方两格空气。
- 生成：`new EntityIronGolem` + `setPlayerCreated(false)`，挂 `ulTideGolem` NBT（主人 UUID，收尾清理用，
  `:553`）+ `TimedSummons.mark` TTL `golem_lifespan_ticks=1200`（60 秒，`morph-extension/.../TimedSummons.java:49-58`，
  逐实体 tick 到期清理 `:75-92`）。
- 定位：守村方（「铁傀儡会还击随从（原版守村 AI）」`HordeManager.java:500`），随从被其击杀掉热忱的腐肉
  （`onFollowerDrops`，`:686-708`，`LivingDropsEvent`）。
- 上限口径注意：`countGolems`（`:824-828`）按「中心 16 格**三维**盒」统计全部 `EntityIronGolem`
  （含野生），不是只数 `ulTideGolem`。

### 3.2 原版 Minecraft 1.12.2（reference 实证）

`reference/minecraft-forge-1.12.2-2847-stable39/net/minecraft/village/Village.java:60-82`
（`Village.tick` → 自然刷傀儡）：

```java
int i = this.numVillagers / 10;
if (this.numIronGolems < i && this.villageDoorInfoList.size() > 20 && this.world.rand.nextInt(7000) == 0) {
    // findRandomSpawnPos(center, 2, 4, 2) → spawnEntity(new EntityIronGolem)
}
```

即原版要求：**≥10 村民/只 + ≥21 扇有效门 + 每 tick 1/7000 概率**；村民数按村庄中心
（y ±4）统计（`Village.java:134-148`）；落点在**村庄中心 y±3** 找 2×4×2 净空（`findRandomSpawnPos`，
`Village.java:85-95`）。**4 个村民在原版永远刷不出铁傀儡**（10 门槛 + 21 门 + 随机）。
1.12.2 也没有「村民恐慌/受伤召唤傀儡」机制（那是 1.14+ 村庄改版语义）。

结论：本项目是**自研、独立于原版**的计时刷怪，与原版条件不矛盾（互不复用、互不干扰）；
「村民数量阈值」只存在于原版侧，本项目尸潮傀儡无此门槛。

## 4. 为什么「4 村民不召唤铁傀儡」——测试现场实证

测试现场还原（`~/Games/Minecraft/shaping_the_world_cr/logs/2026-11-05-2.log.gz`，世界 "Test"）：

- 17:05:39-41 `/summon` ×4（村民，`[raleeuuen: Object successfully summoned]` ×4）；
- 17:05:43 `[horde] action: ul.horde.launch` —— **尸潮发动成功**（onAirUse/launch 路径）；
- 17:05:58-17:06:05 多次 `ul.horde.launch.fail.active` —— 会话一直 active；
- 此后至 17:07:13 退出无任何 horde 动作；会话期间日志无事件异常（17:07:14 的
  ForgeChunkManager NPE 是关服竞态，与本题无关）。

存档实证（`saves/Test/data/ul_horde.dat`，mtime 17:06 = 「Saving and pausing」落盘）：

```
Active=1, StartTick=128100, LastGolem=0, AbortSince=0, Village=(-137, 64, -74)
```

**`LastGolem=0` 是决定性证据**：`maybeSpawnGolem` 第 535-538 行只要被进入一次就必然写入非零时间戳
（先写 `lastGolemSpawnTick=now` 再做上限/落点判断）。active ≥18 秒（≈360 tick，够 18 次 tick 拍）
仍为 0 ⇒ **`maybeSpawnGolem` 一次都没被调用** ⇒ 断点在 tick 链路，不在落点/上限/冷却。

断点定位：`ZombieLine.register()`（`ZombieLine.java:65-73`）：

```java
EventBus bus = MinecraftForge.EVENT_BUS;
bus.register(new HordeEvents());      // ← 实例注册
bus.register(new TitanFormHandler()); // ← 同病
bus.register(new TitanSlamMigration());
```

而 `HordeEvents` 六个 handler **全部是 static**（`HordeEvents.java:37/49/60/67/72/79`），
`TitanFormHandler` 三个也全是 static（`TitanFormHandler.java:49/58/67`）。Forge 1.12.2
`EventBus.register(Object)`（`reference/forge-1.12.2-14.23.5.2860/net/minecraftforge/fml/common/eventhandler/EventBus.java:79-86`）：

```java
boolean isStatic = target.getClass() == Class.class;
for (Method method : (...).getMethods()) {
    if (isStatic && !Modifier.isStatic(method.getModifiers())) continue;
    else if (!isStatic && Modifier.isStatic(method.getModifiers())) continue; // ← 实例注册跳过全部 static 方法
```

**注册实例时 static handler 被静默跳过、无任何告警**，9 个 handler 全部未挂上总线。
（`WorldTickEvent` 派发本身正常：`FMLCommonHandler.java:274`。`TitanSlamMigration.onLogin` 是实例方法，
`:29-30`，不受影响——所以「登录迁移」等实例 handler 一直正常，掩盖了问题。）

### 后果清单（同一根因）

| 失效 handler | 后果 |
|---|---|
| `onWorldTick`（`HordeEvents.java:72-76`） | `HordeManager.tick` 从不执行 → **不刷铁傀儡**、随从不接屠村作战指令（`commandFollowers`）、成功/中止永不判定（尸潮挂死） |
| `onLivingDrops`（`:67-69`） | 热忱的腐肉**永不掉落**（任务「随从被铁傀儡打死才会掉」即使有傀儡也做不了） |
| `onLivingDeath`（`:49-53`） | 玩家死亡不再判尸潮失败 |
| `onLivingAttack`（`:79-104`） | 「随从不伤主人」门失效 |
| `onPlayerTick`（`:37-46`） | 队员发光续挂 / 队伍快照节流同步失效（入队即时描边仍在，因 `syncTeam` 直调） |

### 其余候选逐一排除（附证据）

- a) 测试方法：发动确实成功（日志 `ul.horde.launch`）且 4 村民满足下限；「村民不恐慌」不是条件——
  本设计无恐慌触发器（§3.1）。
- b) 阈值/半径单位：`DesignRegistry.integer` = `(int) Math.round(num(...))`（`DesignRegistry.java:204-205`），
  无秒/tick 换算地雷；`zombie_tide.horde.*` 53 键正常加载（11-05 日志 `[uld] Loaded 53 strength values`，
  仅 `recruit_reach` 缺键走默认并告警，与傀儡无关）。
- c) 配置：CR 测试实例 `config/ul_strength_design/overrides.json` 的 `values` 为空（默认 9 上限生效），
  不挡首只；Forge 实例的 `golem_max_alive=4` 是 `demo-zombie-tide.sh:36-39` 的演示调参残留，与本测试无关。
- d) 生成被上限/冷却/TTL 拦：`LastGolem=0` 排除（这些逻辑在 `lastGolemSpawnTick` 写入之后才执行）。
- e) 区块/落点：`findGolemSpawn` 在正常地表可用（高度图语义已核实 §3.1）；且即使落点全失败也必先写
  `LastGolem`。落点存在**视野缺陷**（见 §6 次要问题），但不是本次根因。

## 5. 最小修复方案（本次只给方案，未改代码）

改一处即可（推荐，单文件）：`src/main/java/aaaa0ggmc/ulmodpack/zombie/ZombieLine.java:71-73`——
两个「全静态 handler」类改为**类注册**（与 `@Mod.EventBusSubscriber` 同机制，静态方法才绑定），
`TitanSlamMigration` 保持实例注册（其 `onLogin` 是实例方法）：

```java
EventBus bus = MinecraftForge.EVENT_BUS;
bus.register(HordeEvents.class);          // 全 static → 类注册
bus.register(TitanFormHandler.class);     // 全 static → 类注册
bus.register(new TitanSlamMigration());   // 实例方法，保持不变
```

等价替代（二选一，别同时做）：把 `HordeEvents`/`TitanFormHandler` 的 9 个 handler 去掉 `static`
（`HordeEvents.java:37/49/60/67/72/79`、`TitanFormHandler.java:49/58/67`），保留实例注册。
参考正确姿势：morphext `FollowerHandler` 用 `@Mod.EventBusSubscriber` + static（`FollowerHandler.java:36-44`），
`TimedSummons` 用实例注册 + 实例方法（`TimedSummons.java:75`），两种都活。

注意：不要给 `TitanSlamMigration` 改类注册（会反向丢掉它的实例 handler）；修复后同一次注册
不要重复挂（`EventBus.register` 对同一 target 幂等）。

## 6. 次要问题（顺手修，非本次根因）

1. **落点视野缺陷**：`findGolemSpawn`（`HordeManager.java:831-846`）用高度图顶取落点——村庄有屋顶/树冠时
   傀儡刷在屋顶/树顶（玩家看不见），且可能落在 `countGolems`（`:824-828`，中心 ±16 三维盒）之外导致
   上限失守。建议对齐原版语义（`Village.findRandomSpawnPos`，`Village.java:85-95`）：优先在
   `center.getY() ± 3~6` 找「2 宽 4 高 2 深净空」的地面落点，高度图仅作兜底。
2. `zombie_tide.horde.golem_multiplier`（`ul_strength/zombie_tide.json:19`）是死键，无代码引用；
   设计上若想要「每 N 村民一只」的语义需另行实现或删键。
   → **2026-11-07 处理：接线为「铁傀儡数量倍率」（不删）**。worldwiki 设计文档证实设计意图存在：
   `tideIronGolemMultiplier = 3.0` 是「铁傀儡刷新倍率」——尸潮对常规（村落线流言/恐吓：15 秒 1 只、
   上限 3 只）正好 3 倍（5 秒 1 只、上限 9 只），见「尸潮.md」铁傀儡刷新一节、「实现契约.md」参数表；
   上面「每 N 村民一只」的猜测**不成立**（与村民数无关）。实现 `HordeManager.golemAliveCap()`：
   存活上限 = clamp(round(常规基线 3 × golem_multiplier), 1, golem_max_alive)，倍率读不到回退 1.0
   （= 常规 1 倍、仅 3 只），配置值 3.0 → 9，与接线前行为一致；`golem_max_alive` 仍是 ≤9 硬上限。
   刷怪间隔仍由 `golem_interval_ticks` 直配（100t = 设计的 3 倍速率），倍率只作用于数量上限。
   `zombie_tide.json` 的 `_doc` 已注明该键单位（「对常规 3 只的倍率」）与回退例外。
   → **2026-11-07（同日稍后）按用户口径改回「N×村民数」并删键**：用户原话「上限就是玩家周围区块最多
   N×村民个数 个铁傀儡，只不过生成速度很快」。`HordeManager.golemAliveCap(World, BlockPos)` 改为
   `clamp(round(村庄中心 villager_count_radius 内活村民数 × golem_villager_multiplier), 1, golem_max_alive)`，
   N 默认 1.0（6 村民 → 上限 6）；`golem_max_alive` 降级为硬上限兜底（normal 9→20、hard 12→30，
   只在村民×N 超过它时生效）；旧键 `golem_multiplier` 与常量 `REGULAR_GOLEM_CAP` 一并删除（避免第二个数量真相），
   `CommandUlPack` 的强度来源探针键同步换成 `golem_villager_multiplier`。`countGolems` 半径由
   `golem_spawn_radius`(16) 改 `villager_count_radius`(48)，与上限的村民侧同一把尺子（傀儡会走远，用小半径会漏计失守）。
   生成速度未动（`golem_interval_ticks` normal 100t / hard 80t，相对原版每 tick 1/7000 已 ~70 倍速）。
3. `countVillagers`（`HordeManager.java:818-822`）不过滤 `isDead`，死村民可能拖一拍再判成功（无害）。
   → **2026-11-07 已修**：改为逐个滤 `isDead` 统计活村民，成功判定「活村民清零」口径与
   `findVillageCenter`（同样滤 isDead）对齐；死村民不再拖一拍。
4. 语义不一致：`launchOrDisband` 在「已在进行中」时**解散**（`HordeManager.java:313-324`），
   而演示入口 `onAirUse` 在同样情况提示 `fail.active` 并保留（`:363-366`）。已按类注释视为设计，
   但建议统一或在任务文案里写明「潜行空放=强制收尾」。
   → **2026-11-07 复核结论：是设计取舍，非 bug，不改行为，已写清**。`launchOrDisband` 是玩家唯一
   入口（`horde_command` 潜行对空），三能力合并后没有独立「停止」能力，「进行中二次潜行对空=
   强制收尾」是 toggle 语义的有意设计（玩家始终有手动收尾手段）；`onAirUse` 已降级为演示脚本专用
   （仅 `demo-zombie-tide.sh:67` 反射调用），「进行中只提示 `fail.active`、保留现场」防脚本重复
   `invoke` 误收尾中断录像。分歧已在双方 javadoc、`HordeAbilities` 分派注释写明，并在
   zh/en 能力文案补「尸潮进行中潜行对空=强制收尾」。
5. Forge 实例 `shaping_the_world/config/ul_strength_design/overrides.json` 里 `golem_max_alive=4`
   为演示脚本残留（`demo-zombie-tide.sh:36-39`），非演示时建议还原，避免上限口径漂移。
   → **2026-11-07 已核实并还原**：该文件确为 demo 脚本 heredoc 整体覆盖写入的残留
   （`_doc` 即「演示调参：铁傀儡上限降低」），已还原为干净模板（`values` 空，与 CR 实例一致）；
   `golem_max_alive=9` 默认恢复生效，配合 §6.2 接线后合成上限 = min(9, round(3×3.0)) = 9。
   注意：再跑一次 `demo-zombie-tide.sh` 会重新写入该覆盖（脚本在 ul-agent-control 仓库、未改），
   演示后需手动还原；后续可给脚本加「收尾时清理 overrides」。

## 7. 复现与验证步骤（游戏内）

复现（修复前，必现）：

1. 进世界（单人即可），任意僵尸形态 + `ulpack skill give <玩家> ul:horde_leadership`；
   招 ≥1 个僵尸族随从（不潜行对僵尸释放 `horde_command`）。
2. 平坦空地放 4 个活村民（`/summon minecraft:villager`，保证在玩家 32 格内），清掉场上其它铁傀儡。
3. 潜行、对准空气释放「尸群指挥」（或 `/ulpack tide start`）→ Actionbar 出现发动提示。
4. 观察 30 秒：**无任何铁傀儡出现**；`/ulpack tide status` 之类无进展（尸潮挂死，杀光村民也不结算）。
5. 退出存档后查 `saves/<世界>/data/ul_horde.dat`：`Active=1` 且 **`LastGolem=0`**（本次判据）。

验证（修复后，逐项过）：

1. 同上发动 → **1 秒内**村庄中心 16 格内刷出第 1 只铁傀儡（`/data getentity` 可见 `ulTideGolem` 标签、
   `ulSummonUntil` TTL）；之后每 5 秒 +1，直至上限（默认 9；`/ulstrength where zombie_tide.horde.golem_max_alive`
   核对实例覆盖值）。
2. 傀儡会还击攻击村民的随从；随从被傀儡击杀掉 1 个「热忱的腐肉」（`LivingDropsEvent` 链路同根因修复）。
3. 杀光中心 48 格内活村民 → 聊天栏 `ul.horde.win`、`tidesCleared` +1、说服度 +10；
   收尾时本场傀儡全部消失（未加载的 60 秒 TTL 兜底）。
4. 负向回归：远离中心 48 格超 30 秒 → `ul.horde.abort`（不扣说服度）；尸潮中玩家死亡 → `ul.horde.fail`
   （说服度 −20）；非潜行对僵尸释放仍是招募、潜行对队员仍是踢出。
5. 泰坦回归：摔落减免/挖掘减速/睡眠检查（`TitanFormHandler` 同批修复）恢复生效。

## 8. 证据索引

| 结论 | 证据 |
|---|---|
| 发动链路与条件 | `HordeAbilities.java:85-150`；`HordeManager.java:307-325/395-404` |
| 「4 村民」口径 | `HordeManager.java:796-816`；`ul_strength/zombie_tide.json:36-38`；`dev/quests/zombie_tide.json:144` |
| 傀儡 = 计时刷怪、无村民触发器 | `HordeManager.java:469/532-556`；`HordeState.java:25/37` |
| 原版 1.12.2 门槛 ≥10 村民+≥21 门+1/7000 | `reference/.../village/Village.java:60-82/134-148/85-95` |
| 测试现场：发动成功、无傀儡 | `shaping_the_world_cr/logs/2026-11-05-2.log.gz`（17:05:43 `ul.horde.launch` 等） |
| tick 链路死 | `saves/Test/data/ul_horde.dat`（`Active=1, LastGolem=0`）+ `HordeManager.java:535-538` |
| 根因：实例注册丢弃 static handler | `ZombieLine.java:71-73`；`HordeEvents.java:37-79`；`EventBus.java:79-86`（Forge 2860） |
| 对照正确姿势 | `FollowerHandler.java:36-44`（EventBusSubscriber+static）；`TimedSummons.java:75`（实例+实例方法） |
| 配置未污染 | `shaping_the_world_cr/config/ul_strength_design/overrides.json`（values 空）；`DesignRegistry.java:204-205` |
