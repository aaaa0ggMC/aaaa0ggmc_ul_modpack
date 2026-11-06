# 原版生物硬编码 EntityPlayer 检测 → morphext 统一仇恨接口（注入清单）

> 适配层归属：**本仓库（aaaa0ggmc_ul_modpack）**。morphext（morph-extension）只提供
> 阵营图与统一仇恨接口；逐生物注入与阵营数据在本仓库。搜证基准：
> `reference/minecraft-forge-1.12.2-2847-stable39/`（MCP stable_39 + Forge 2847 补丁）。

## 1. 统一仇恨接口（桥接层 `aaaa0ggmc.ulmodpack.faction.HateQueries`）

**与 morph-extension 契约对照（收尾核对结果）**：morph-extension faction 包已落地
`IEnmityQuery`（统一契约）+ `FactionEnmity`（门面）+ `FactionEnmityQuery`（实现，
全部走 FactionGraphEngine/FactionManager）。本仓库 `HateQueries` 的三个方法签名与语义
已与 `IEnmityQuery` **逐字对齐**（含 dummy 归一、回退 MODERATE→敌对的语义）：

| 方法 | 签名（= `IEnmityQuery`） | 语义 |
| --- | --- | --- |
| `relation` | `FactionRelation relation(EntityLivingBase self, EntityLivingBase target)` | 阵营图原始关系（含玩家身份压图），无数据回退 MODERATE |
| `isEnemy` | `boolean isEnemy(EntityLivingBase self, EntityLivingBase target)` | ENEMY/MODERATE（含回退）→ 敌对（普通无阵营玩家**保持原版敌对语义**）；MEMBER → 被 target（或其变身 dummy）伤害过才敌对；RELATIVE → 同上且 self 半血以下；LORD → 永不；self==target/null → false |
| `findNearestEnemy` | `EntityLivingBase findNearestEnemy(EntityLivingBase self, double range, Predicate<? super EntityLivingBase> filter)` | range 内最近敌人；扫描盒 `grow(range,4,range)` + 欧氏距离过滤；filter 在敌意判定前应用（如限定 `EntityPlayer`） |

与 `FactionManager.isAttackPermitted`（morphext 生物→玩家方向）语义完全一致。
执行侧：全部查询纯读；AI 判定均在服务端 tick（`!world.isRemote`）。

**实现落点说明（G1–G10 扩展后已切换）**：morph-extension 已完成 §G1–§G10 扩展并
构建新 jar，`HateQueries` 现为**纯委托门面**——全部方法一行委托
`FactionEnmity`（含 `isAlly`/`isTolerantOf`/`provokedBy`/`resolveDamageSource`/
锚点与类型化 `findNearestEnemy`/`AggressionPolicy` 新 API），判定逻辑零复制。
保留本入口只为稳定 25+ 注入点的 import 面。新契约以 morph-extension
`ARCHITECTURE.md`「仇恨统一接口」节为准。

## 2. 苦力怕 BUG 修复说明（第一优先，已注入）

**根因**：`EntityCreeper.initEntityAI()`（SRG `func_184651_r`）硬编码
`new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true)`——只认
`EntityPlayer` 为敌。玩家变形僵尸（zombie 阵营）与 creeper 阵营是 MEMBER 关系
（本仓库 `faction_relations.json` 显式固化；图推导经共同祖先 `monster` 交集同为
MEMBER），但硬编码索敌仍把该玩家选为攻击目标 → `EntityAICreeperSwell` 见目标 3 格内
起手膨胀（shouldExecute）→ fuse 满引爆（onUpdate→explode）。

**修改点与新判定**：

| 注入点 | 原判定 | 新判定 |
| --- | --- | --- |
| `MixinEntityCreeper`：`initEntityAI` 的 `EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true)` 构造（@Redirect NEW） | 最近**玩家**=攻击目标（爆炸目标选择的源头） | 换 `NearestEnemyPlayerTarget`：经 `HateQueries.isEnemy` 选最近**敌人**玩家（跳过非敌人找下一个；普通玩家照常被选） |
| `MixinEntityAICreeperSwell.shouldExecute`（`func_75250_a`） | `creeperState>0 \|\| (attackTarget!=null && dist²<9)` | 同左，但 attackTarget 分支追加 `HateQueries.isEnemy`（膨胀起手守门） |
| `MixinEntityAICreeperSwell.updateTask`（`func_75246_d`） | 缓存目标维持引信 | 缓存目标失去敌资格（变形切阵营）→ 置 null，原版 null 分支退引 `setCreeperState(-1)`（引爆守门；读侧 null-safe 已核） |

**新行为**：变形僵尸（MEMBER）玩家站在苦力怕旁——不索敌、不膨胀、不爆炸；
普通玩家行为不变；MEMBER 玩家攻击苦力怕后（provoked 窗口）才允许反击膨胀；
已起手的引信按 morphext 既定口径走完（波及照常结算，不吞爆炸）。
`EntityAIWatchClosest(EntityPlayer.class)`（张望）与打火石 `processInteract` 为
表现/交互，不属仇恨检测，不改。

## 3. 注入清单（逐生物）

状态含义：**已注入**=Mixin 已写并登记（静态自查过，待进游戏验证）；**不改**=经搜证
不属「对玩家特判的仇恨检测」或保留更符合原版语义（标注原因）；**依赖接口/待设计**=
改动牵涉非仇恨系统（声望/经济/平衡）需另行设计；**不存在**=1.12.2 无此类。

### 3.1 已注入（Mixin：`src/main/java/aaaa0ggmc/ulmodpack/mixin/impl/`）

| 生物 | 类/方法(SRG) | 硬编码形式 | 改为 | 执行侧 | 验证要点 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| 苦力怕 | `EntityCreeper.initEntityAI`(func_184651_r):65 | `EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true)` | `NearestEnemyPlayerTarget`（isEnemy） | 服务端 | 变形僵尸站旁不膨胀；普通玩家照常 | 已注入 |
| 苦力怕 | `EntityAICreeperSwell.shouldExecute`(func_75250_a) / `updateTask`(func_75246_d) | `getAttackTarget()!=null && dist²<9` 起手/维持 | isEnemy 守门 + 变节退引 | 服务端 | 同上；起手后换形态即退引 | 已注入 |
| 僵尸（含尸壳/僵尸村民/巨人继承） | `EntityZombie.applyEntityAI`(func_175456_n):93 | 同上三参构造 | `NearestEnemyPlayerTarget` | 服务端 | 变形同阵营不被追 | 已注入 |
| 僵尸 | `EntityZombie.attackEntityFrom`:249 | `world.isAnyPlayerWithinRangeAt(...,7)` 增援生成点规避 | 不改：生成安全规则非仇恨 | 服务端 | — | 不改 |
| 骷髅族（骷髅/流浪者/凋灵骷髅） | `AbstractSkeleton.initEntityAI`(func_184651_r):80 | 三参构造（头颅降权在共享层） | `NearestEnemyPlayerTarget` | 服务端 | 变形同阵营不被射 | 已注入 |
| 蠹虫/银鱼 | `EntitySilverfish.applyEntityAI`(func_175456_n):51 | 三参构造 | `NearestEnemyPlayerTarget` | 服务端 | 同上 | 已注入 |
| 末影螨 | `EntityEndermite.initEntityAI`(func_184651_r):45 | 三参构造 | `NearestEnemyPlayerTarget` | 服务端 | 同上 | 已注入 |
| 卫道士 | `EntityVindicator.initEntityAI`:60 | 三参构造 | `NearestEnemyPlayerTarget` | 服务端 | 同上；Johnny 打一切不动 | 已注入 |
| 恼鬼 | `EntityVex.initEntityAI`:83 | 三参构造 | `NearestEnemyPlayerTarget` | 服务端 | 同上 | 已注入 |
| 烈焰人 | `EntityBlaze.initEntityAI`:61 | 三参构造 | `NearestEnemyPlayerTarget` | 服务端 | 同上 | 已注入 |
| 史莱姆/岩浆怪 | `EntitySlime.initEntityAI`:56 | `new EntityAIFindEntityNearestPlayer(this)`（整类只扫玩家） | `FindNearestEnemyPlayer`（findNearestEnemy） | 服务端 | 变形同阵营不被撞/追 | 已注入 |
| 恶魂 | `EntityGhast.initEntityAI`:51 | 同上 | `FindNearestEnemyPlayer` | 服务端 | 同上 | 已注入 |
| 僵尸猪人 | `updateAITasks`(func_70619_bc):87 / `readEntityFromNBT`(func_70037_a):130 | `world.getPlayerEntityByUUID` 恢复仇恨只认玩家 | `getEntityByUuid` 泛型解析（@Redirect） | 服务端 | 非玩家攻击者的仇恨记忆可恢复 | 已注入 |
| 僵尸猪人 | `AITargetAggressor.shouldExecute`(func_75250_a):220 | 愤怒后仍只搜 `EntityPlayer` | isEnemy 过滤 + findNearestEnemy 改写（@Inject RETURN） | 服务端 | 愤怒后只追敌人玩家 | 已注入 |
| 末影人 | `EntityEnderman.shouldAttackPlayer`(func_70821_d):178 | 对视判定（南瓜头豁免+视线）即激怒 | 对视判定 RETURN 叠加 isEnemy | 服务端 | 变形同阵营对视不激怒；被 HurtByTarget 复仇不受影响 | 已注入 |
| 蜘蛛（含洞穴蜘蛛） | `EntitySpider.AISpiderTarget`(func_75250_a):220 | 目标类写死 EntityPlayer + 光照闸门 | 光照闸门保留 + isEnemy 过滤 | 服务端 | 亮处仍不攻击；暗处只追敌人玩家 | 已注入 |
| 潜影贝 | `EntityShulker.AIAttackNearest`:614 | 只感知 `EntityPlayer`（方向裁剪保留） | isEnemy 过滤 | 服务端 | 变形同阵营不被子弹锁定 | 已注入 |
| 北极熊 | `EntityPolarBear.AIAttackPlayer`:184 | 护崽攻击「附近玩家」 | isEnemy 过滤（只驱赶敌人玩家） | 服务端 | 变形同阵营站幼崽旁不被攻击 | 已注入 |
| 守卫者/远古守卫者 | `EntityGuardian.GuardianTargetSelector.apply`:500 | `instanceof EntityPlayer \|\| instanceof EntitySquid` | 玩家一路叠加 isEnemy；鱿鱼特例保留 | 服务端 | 变形同阵营不被光束锁定 | 已注入 |
| 狼 | `EntityWolf.shouldAttackEntity`(func_142018_a):477 | 驯服狼帮主人反击的目标过滤（PvP/驯服类豁免） | 原版规则之上叠加：**主人永不敌对**（target==owner/getOwner → 拒绝）+ isEnemy 敌意判定 | 服务端 | 驯服狼永不打主人；同阵营形态不被打；野狼照常 | 已注入 |
| 狼 | `EntityWolf.initEntityAI`:95 `EntityAINearestAttackableTarget<>(AbstractSkeleton.class, false)` | 对骷髅自动敌对 | `NearestEnemyTarget` 泛型替身（isEnemy 谓词；wolf↔skeleton ENEMY 边固化） | 服务端 | 变形骷髅同阵营的狼不追 | 已注入 |
| 铁傀儡 | `EntityAIDefendVillage.shouldExecute`(func_75250_a):24-42 | 村庄仇恨两路（攻击村民者 + `Village.getNearestTargetPlayer` 声望扫描） | 两路目标叠加 isEnemy（FactionPassive 身份压图：变形 golem/villager 族不被守护者攻击） | 服务端 | 变形铁傀儡/村民站村不被攻击；普通玩家照常 | 已注入 |
| 凋灵 | `EntityWither.attackEntityFrom`(func_70097_a):498 | 同属性亡灵免疫但**玩家例外**可伤 | 「玩家例外」→「敌人例外」（!instanceof EntityPlayer → !isEnemy）：同属性非敌人免疫 | 服务端 | 变形亡灵同阵营打凋灵不掉血；普通玩家可伤 | 已注入 |
| 凋灵 | `EntityWither.updateAITasks`(func_70619_bc) 头颅发射 `launchWitherSkullToEntity`(func_82216_a) | 头颅只打 watched target（选目标含 NOT_UNDEAD/创造豁免） | 发射层过滤 isEnemy（选目标/watched 维持原版回退语义） | 服务端 | 同阵营玩家不被头颅打 | 已注入 |
| 末影龙 | `EntityDragon.attackEntityFromPart`(func_70965_a):506 | `getTrueSource() instanceof EntityPlayer \|\| isExplosion()` 才受理伤害 | `isEnemy(dragon, trueSource) \|\| isExplosion()`（敌对生物可伤龙；同阵营玩家不可） | 服务端 | 变形末影人玩家打龙不掉血；普通玩家可伤 | 已注入 |
| 末影龙 | `EntityDragon.onCrystalDestroyed`(func_184672_a):890 | 水晶归因：`instanceof EntityPlayer` 用之，否则 `getNearestAttackablePlayer` 硬找玩家 | 敌人攻击者归因，否则 findNearestEnemy 找最近敌人玩家；无敌人归因 null | 服务端 | 毁水晶仇恨指向敌人 | 已注入 |
| 末影龙 | `PhaseHoldingPattern.findNewTarget`(func_188675_j):58 / `PhaseLandingApproach.findNewTarget`(func_188681_j):49 / `PhaseSittingScanning.doLocalUpdate`(func_188659_c):18,50 | 4 处 `getNearestAttackablePlayer`（飞行/降落/扫描目标） | @Inject 复刻方法体（2026-11-06 补回）：查询行换 `HateQueries.findNearestEnemy`（§G3 锚点/self 中心版），阶段机其余逐行原版 | 服务端 | 变形同阵营玩家不被绕飞/扫描 | 已注入（@Inject 复刻，零 @Redirect） |
| 女巫 | `EntityWitch.initEntityAI`:68 | `EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true)`（投药目标） | TargetSwap 换 NearestEnemyPlayerTarget（isEnemy） | 服务端 | 变形同阵营不被投药 | 已注入 |
| 唤魔者 | `EntityEvoker.initEntityAI`:59（链式 `.setUnseenMemoryTicks(300)`） | 索敌只认玩家（召唤恼鬼/尖牙目标） | TargetSwap 换敌人版（unseenMemoryTicks=300 复制）；村民/铁傀儡目标保留 | 服务端 | 变形同阵营不被攻击 | 已注入 |
| 唤魔者 | `EntityEvoker.initEntityAI`:51 `EntityAIAvoidEntity<>(EntityPlayer.class, 8.0F, 0.6, 1.0)` | 躲避玩家（逃跑对象硬编码玩家类） | §G6 躲避语义：换 isEnemy 谓词版躲避任务（只躲敌人玩家，同阵营变形玩家不吓跑） | 服务端 | 变形同阵营靠近不触发逃跑 | 已注入 |
| 幻术师（`EntityIllusionIllager`） | `EntityIllusionIllager.initEntityAI`:71（链式 `.setUnseenMemoryTicks(300)`） | 索敌只认玩家（弓箭/致盲术目标） | TargetSwap 换敌人版（unseenMemoryTicks 复制）；村民目标保留 | 服务端 | 变形同阵营不被射击 | 已注入 |
| 豹猫 | `EntityOcelot.setupTamedAI`(func_175544_ck):289 | 野生豹猫 `EntityAIAvoidEntity<>(EntityPlayer.class, 16.0F, 0.8, 1.33)` 躲避玩家 | @Inject 复刻（remove/add 配对语义保持同一任务实例）：躲避谓词换 isEnemy（§G6） | 服务端 | 变形同阵营靠近不吓跑豹猫 | 已注入 |
| 蝙蝠 | `EntityBat.updateAITasks`(func_70619_bc):121 | `world.getNearestPlayerNotCreative(this, 4.0) != null` → 惊飞（玩家敏感逃跑） | @Inject HEAD cancel 复刻全段：玩家查询换 `HateQueries.findNearestEnemy(self, 4.0, EntityPlayer, 非创造/旁观)`（§G6 躲避 + §G4 类型化） | 服务端 | 变形动物/蝙蝠形态靠近倒挂蝙蝠不惊飞；普通玩家照常 | 已注入（@Inject 复刻，零 @Redirect） |

### 3.2 内部类/谓词注入（同上，单列便于核对）

| 生物 | 目标内部类 | 模式 | 状态 |
| --- | --- | --- | --- |
| 僵尸猪人 | `EntityPigZombie$AITargetAggressor` | shouldExecute/shouldContinueExecuting RETURN 复核 + targetEntity 改写 | 已注入 |
| 蜘蛛 | `EntitySpider$AISpiderTarget` | 同上 | 已注入 |
| 潜影贝 | `EntityShulker$AIAttackNearest` | 同上 | 已注入 |
| 北极熊 | `EntityPolarBear$AIAttackPlayer` | 同上 | 已注入 |
| 守卫者 | `EntityGuardian$GuardianTargetSelector` | apply RETURN 过滤 | 已注入 |

### 3.3 不改 / 依赖接口 / 不存在（搜证记录，均经 reference 核实）

| 生物 | 类/方法 | 硬编码形式 | 结论与原因 |
| --- | --- | --- | --- |
| 全体（表现层） | 各生物 `EntityAIWatchClosest(this, EntityPlayer.class, …)` | 注视玩家 | 不改：纯表现张望，无仇恨语义 |
| 僵尸猪人 | `attackEntityFrom`:145 `getTrueSource() instanceof EntityPlayer → becomeAngryAt` | 只有玩家打才进愤怒记忆 | 不改：激怒=provoked 窗口与统一接口同向；即时反击已由 HurtByTarget 通用覆盖 |
| 僵尸猪人 | `isPreventingPlayerRest`:202 | 愤怒时阻止玩家睡觉 | 不改：睡眠规则非仇恨（如需兼容伪装另行设计） |
| 狼 | `attackEntityFrom`:295 非玩家/非箭攻击者伤害减半 | 玩家享有全额伤害特权 | 不改：平衡优待非仇恨检测；重定向有 super-redirect 递归/双重减半陷阱（缺口 §G5） |
| 铁傀儡 | `canAttackClass`:150 `isPlayerCreated && EntityPlayer.class.isAssignableFrom` | 玩家建造的不打玩家类 | 不改：保护主人语义（类级 API 无实例敌意信息，缺口 §G9）；实例级敌意见 DefendVillage 注入 |
| 铁傀儡 | `onDeath`:263 声望扣减依赖 `attackingPlayer` | 村庄铁傀儡死亡对「最后打它的玩家」扣声望 | 不改：声望系统非仇恨（归因缺口 §G2） |
| 村民 | `setRevengeTarget`:623 / `onDeath`:642-651 | 被玩家打扣声望+粒子；死亡归因；附近有玩家停繁殖 | 不改：经济/声望系统非仇恨；逃跑目标是僵尸/灾厄类（非玩家） |
| 村民/傀儡类 | 各 `EntityAIWatchClosest2` | 注视玩家 | 不改：表现层 |
| 唤魔者 | `EntityAIAvoidEntity<>(EntityPlayer.class)`:51 + `EntityAINearestAttackableTarget(EntityPlayer.class)`:59 | 躲避/索敌玩家 | 不改（低优先）：目标注册同 A 类模式，可后续套用 `NearestEnemyPlayerTarget` |
| 卫道士 Johnny | `AIJohnnyAttack`(EntityLivingBase + JOHNNY_SELECTOR):170 | 改名后无差别攻击**一切**生物 | 不改：本就无玩家硬编码（1.12.2 无 onLivingAttack 版 Johnny 逻辑） |
| 远古守卫者 | `updateAITasks`:89-103 | MiningFatigue 只给 `EntityPlayerMP`（生存/冒险） | 不改：玩家状态效果投放非仇恨检测（兼容伪装需另行设计） |
| 雪傀儡 | `initEntityAI`:56（目标 `IMob.MOB_SELECTOR`） | 只打敌对生物 | 不改：不认玩家为敌，无硬编码玩家检测（注视除外） |
| 恶魂 | `attackEntityFrom`:79 玩家反弹火球秒杀 | 「谁的火球」硬编码玩家 | 不改：伤害规则特判非仇恨检测 |
| 末影人 | `EntityEndermite.isSpawnedByPlayer` 谓词:86 | 攻击「玩家生成的」末影螨 | 不改：目标是末影螨类，玩家生成溯源非仇恨 |
| 末影龙 | `attackEntityFromPart` 的 `sittingDamageReceived` 记账 | 栖息伤害计数 | 不改：随 attackDragonFrom 门一并短路，无独立玩家特判 |
| 凋灵 | `updateAI` 头颅 watched-target 的 disableDamage 豁免 | 创造玩家不作头颅目标 | 不改：玩家模式豁免（保留），敌人过滤在发射层叠加 |
| 幻翼 | — | — | **不存在**：幻翼 1.13+ 加入，1.12.2 无 `EntityPhantom`（溺尸 `EntityDrowned` 同为 1.13+，一并核实无此类） |
| 羊驼 | `EntityLlama`（spit=385 / `AIDefendTarget`:495 / `AIHurtByTarget`:注册） | 吐口水目标=getAttackTarget（泛型）；AIDefendTarget 目标类=`EntityWolf`（驼队自卫打狼） | 不改：**无玩家硬编码仇恨**——spit 随 attackTarget 泛型走，被玩家打的反击由 HurtByTarget 通用覆盖；`wolf↔llama` 如需敌意走阵营边（未加，狼↔羊驼无原版敌对） |
| 雪傀儡 | `EntitySnowman.initEntityAI`:56 | 目标=`EntityLiving.class`+`IMob.MOB_SELECTOR`（只打敌对生物） | 不改：不认玩家为敌，无玩家硬编码检测（注视除外，上轮已判） |

### 3.4 共享 AI/基类层（morphext 核心/通用层归属，本仓库不注入）

以下「硬编码玩家」位于所有生物共用的 AI/基类，按 morphext 分层属通用机制
（应由 morphext 核心统一处理），本仓库逐生物注入不覆盖：

- `EntityAINearestAttackableTarget.shouldExecute`:68-107 — `targetClass == EntityPlayer`
  走 `getNearestAttackablePlayer` 专属通道 + 头颅伪装降权函数；
- `EntityAIWatchClosest.shouldExecute`:42-51 — `getClosestPlayer`；
- `EntityAIFindEntityNearestPlayer` 整类 — 只扫 `world.playerEntities`；
- `EntityAITarget`/`EntityAIAttackMelee` — 创造/spectator 玩家排除（合理保留）；
- `EntityLivingBase.attackEntityFrom`:809-816 — `attackingPlayer` 玩家记账（经验/掉落框架）。

## 4. 阵营数据（`assets/aaaa0ggmc_ul_modpack/morphext/faction_relations.json`）

morphext 核心内置约 18 阵营已覆盖 zombie/skeleton/creeper/ender/spider/illager/
golem/villager 等映射与关系；本仓库只增补缺失项（loader 全模组扫描合并，映射为并集、
关系边同 key 后载覆盖，不与核心冲突）：

- 实体映射补缺：`slime`、`guardian`/`elder_guardian`（aquatic）、`wolf`、`polar_bear`、
  `ocelot`（核心 `creeper↔ocelot ENEMY` 边引用的 ocelot 阵营此前无映射）；
- 新阵营：`wolf`/`ocelot`（parent=beast）；
- 关系边：`zombie↔creeper MEMBER`（任务书点名，显式固化，与图推导一致）、
  `wolf↔skeleton ENEMY`（原版狼自动攻击骷髅的阵营化固化）。

## 5. 覆盖统计

- 搜证命中（硬编码 EntityPlayer 检测点，逐类核实）：约 65 处（含共享层 6 处、
  注视类表现点 12 处）；
- **已注入生物：26 个类**（苦力怕、僵尸、骷髅基类、蠹虫、末影螨、卫道士、恼鬼、
  烈焰人、史莱姆、恶魂、僵尸猪人、末影人、蜘蛛、潜影贝、北极熊、守卫者、狼、
  铁傀儡（DefendVillage）、凋灵、末影龙（本体+3 阶段）、女巫、唤魔者、幻术师、
  豹猫、蝙蝠 + 骷髅三变体/岩浆怪/洞穴蜘蛛等继承覆盖）→ **30 个 Mixin 类 + 5 个替身/桥接类**；
- 不改（审计判定）：羊驼（无玩家硬编码仇恨）、雪傀儡（无玩家仇恨）、Johnny（无玩家
  硬编码）、注视类/声望类/伤害规则类（§3.3 逐条注明）；
- 待改写：无（蝙蝠惊飞已于 2026-11-06 按 §G6 改写，见 §3.1）；
- 不存在：幻翼、溺尸（1.13+ 生物）；
- **@Redirect / MixinExtras 注解：全仓零使用**（编译产物 javap 终验 0 引用）。

## 6. 静态自查与遗留

- SRG 名逐一对照 `reference/mappings-stable39/methods.csv|fields.csv`：
  func_184651_r=initEntityAI、func_175456_n=applyEntityAI、func_75250_a=shouldExecute、
  func_75253_b=shouldContinueExecuting、func_75246_d=updateTask、func_70619_bc=updateAITasks、
  func_70037_a=readEntityFromNBT、func_70821_d=shouldAttackPlayer、func_70097_a=attackEntityFrom、
  func_152378_a=getPlayerEntityByUUID、field_75269_a=swellingCreeper、field_75268_b=creeperAttackTarget；
- morphext API 签名经 `javap` 对 `libs/morph-extension-1.0.0.jar` 核对：
  `FactionGraphEngine.getInstance()/getRelation(EntityLivingBase,EntityLivingBase)`、
  `MorphAiEngine.isHostDummyEntity(EntityPlayer,Entity)`、`FactionRelation` 五值 ✓；
- Mixin 绑定沿用仓库惯例（morphext `MixinEntityAIHurtByTarget` 先例）：不跑 mixin AP，
  方法/字段绑定以 SRG 名 + `remap=false`（生产运行时生效；dev MCP 环境注入不匹配只告警），
  NEW 重定向描述符两侧一致；
- 字段写入安全性（AGENTS.md 教训）：仅 `EntityAICreeperSwell.creeperAttackTarget`
  置 null（读侧 updateTask 首行 null 复查已核）与 `targetEntity` 改写
  （读侧 startExecuting 仅在 shouldExecute=true 后执行）；
- **未验证**：未进游戏验证（构建验证见 §8）；苦力怕 BUG 修复与各生物行为需进游戏
  验证（变形对应形态后站在生物旁/对视/激怒/打 Boss）；
- **遗留**：① `HateQueries` 待 `libs/morph-extension-1.0.0.jar` 含 `FactionEnmity`
  后切换直接委托（签名已对齐，替换即完成，见 §8 构建记录）；② 唤魔者目标注册可套
  同一模式；③ 共享 AI 层（getNearestAttackablePlayer 专属通道等）待 morphext 核心
  统一处理；④ 缺口清单（§7）交 morphext 线程评估。

## 7. morphext 接口缺口清单（压测发现，交 morphext 线程）

逐生物注入过程中，`IEnmityQuery` / `FactionEnmity` / `FactionManager` 表达不了或
语义别扭的场景（编号 §G1–§G10）：

| # | 场景 | 现有接口为何不够 | 建议扩展方向 |
| --- | --- | --- | --- |
| §G1 | **owner/tamed 关系**（狼对主人永不敌对；驯服类通用） | 阵营图没有「主人/驯服」维度；`isEnemy(wolf, owner)` 对无阵营主人回退 MODERATE=true，主人会被当敌人，只能在调用侧硬编码 `target == getOwner()` | `isEnemy` 内建 owner 判定（`IEntityOwnable.getOwner()==target → false`），或 `FactionEnmity.isAlly(self,target)`（owner + 同队 + 阵营和平合一） |
| §G2 | **伤害/击杀归因**（铁傀儡声望扣减、龙水晶归因、猪人 angerTargetUUID 恢复） | `EntityLiving.attackingPlayer` 只记玩家；`FactionManager.findMorphOwner` 是 private；「trueSource 是 dummy/投射物时归因到宿主」的归一没有公开统一入口 | 公开 `resolveHost` 已有（✓），再加 `resolveDamageSource(DamageSource) → EntityLivingBase` 归因入口（近战/投射物/dummy 三级归一） |
| §G3 | **以任意位置/实体为中心的找敌**（龙水晶归因以 BlockPos 为中心、阶段 AI 以基座为中心） | `findNearestEnemy(self, range, filter)` 只能以 self 为中心；换中心要自己扫 AABB | 加 `findNearestEnemyAt(Vec3d/BlockPos anchor, double range, filter)` 或 `(self, anchor, range, filter)` 重载 |
| §G4 | **findNearestEnemy 无类型参数** | 返回 `EntityLivingBase`，「只找玩家」场景要传 `instanceof` 谓词 + 调用侧 cast，啰嗦 | 加 `<E extends EntityLivingBase> E findNearestEnemy(..., Class<E> type, filter)` 泛型重载（内部并入类型过滤） |
| §G5 | **受击宽容/伤害规则维度**（狼「非玩家非箭半伤」的误伤减让） | `isEnemy` 只表达敌意；「误伤减让」要自己读 relation 写 switch；且 Mixin 层重定向该类分支有 super-redirect 递归/双重减半陷阱 | 加 `isTolerantOf(self, target)`（= !isEnemy 的宽容面，语义化）；或文档明确「受击面按 relation 自行判定」并给标准写法 |
| §G6 | **躲避/逃跑语义**（唤魔者躲玩家、村民躲僵尸） | 契约只有「找敌人」；「找最近的可怕之物来躲」是补集语义，调用侧只能 `!isEnemy` 自拼 | 文档明确「躲避 = isEnemy 取反」标准写法，或加 `findNearestAvoidable` 工具 |
| §G7 | **MODERATE 主动性口径固定**（猪人「中立不主动」vs 怪物「中立=原版敌对玩家」） | `isEnemy` 固定 MODERATE→true（保原版），但「中立互不侵犯」的生物（猪人不主动打人）想要 MODERATE→false，表达不了 | `isEnemy` 加主动性策略参数（如 `AggressionPolicy.AGGRESSIVE/DEFENSIVE`），或按生物注册回退口径 |
| §G8 | **玩家身份压图只覆盖「生物→玩家」方向** | `resolvePlayerRankRelation` 仅 observer 非玩家、target 玩家时生效；变形玩家的主动索敌（玩家→生物）没有 rank 压图，语义不对称 | 补对称语义或文档明确单向设计（变形玩家主动攻击受玩家自身控制，可能故意为之） |
| §G9 | **类级攻击豁免**（玩家造的铁傀儡不打玩家类） | `canAttackClass(Class)` 是类级 API，拿不到实例敌意；isEnemy 是实例级 | 文档标注类级豁免属调用侧规则；或提供 `canAttackClassPolicy(self, Class)` 适配点 |
| §G10 | **持久愤怒记忆**（猪人 angerTargetUUID 400-800 tick） | `isEnemy` 的 provoked 只看 `getRevengeTarget()`（100 tick 衰减）；猪人 anger 记忆是独立机制，仇恨判定与愤怒记忆不同步 | 加 `provokedBy(self, target)` 统一判据（revenge + anger 记忆 + dummy 归属），或文档规定愤怒记忆生物自行同步 |

### 7.1 §G1–G10 落地状态（morphext 新 API 消化情况，2026-11-05 收尾）

morphext 侧结论（`ARCHITECTURE.md`「扩展契约」节）：§G1/G2/G3/G4/G5/G7/G10 **采纳**，
§G6/G8/G9 **文档化即可**。本仓库调用侧消化情况：

| # | morphext 落点 | 本仓库调用侧改动 | 状态 |
| --- | --- | --- | --- |
| §G1 | `isEnemy` 内建主人/同主豁免 + 新增 `isAlly` | **MixinEntityWolf**：删除 `target == owner/getOwner` 显式硬编码，`!isEnemy` 一行涵盖（主人豁免先于阵营图） | ✅ 已消化 |
| §G2 | `resolveDamageSource`（近战→投射物→dummy 三级归因） | **MixinEntityDragon** 伤害门/水晶归因、**MixinEntityWither** 免疫判定：手写 trueSource 提取全部替换为 `resolveDamageSource` | ✅ 已消化 |
| §G3 | `findNearestEnemy(self, anchor, range, …)` 锚点重载 | **MixinEntityDragon** 水晶归因：以水晶位置 `Vec3d(pos)` 为锚点找敌（原为龙中心近似） | ✅ 已消化 |
| §G4 | 类型化 `findNearestEnemy(…, Class<E>, …)` 零强转 | **FindNearestEnemyPlayer** + 4 个内部类 Mixin（猪人/蜘蛛/潜影贝/北极熊）：`instanceof` 谓词 + cast 全部换类型化重载 | ✅ 已消化 |
| §G5 | `isTolerantOf`（RELATIVE/LORD 宽容面） | 狼「非玩家/非箭半伤」重定向**保持现状**：新语义「宽容目标半伤」会把 MODERATE 敌人从半伤改为全额（行为变化），且原版分支重定向有 super-redirect 递归/双重减半陷阱（ARCHITECTURE 标准写法亦警示）——待用户拍板是否语义换轨 | ⏸ 保持现状 |
| §G6 | 文档化（躲避=找敌人） | 无调用侧改动（躲避类点未注入，见清单 §3.3） | 📖 文档化 |
| §G7 | `AggressionPolicy.DEFENSIVE`（MODERATE=仅报复） | **MixinPigZombieAggressorTarget**：`isEnemy` 升级为 DEFENSIVE 口径——猪人是中立生物，愤怒后只追「在案挑衅」目标（原版语义精确保留），不再泛化追任意 MODERATE 玩家 | ✅ 已消化 |
| §G8 | 文档化（身份压图单向是设计） | 无 | 📖 文档化 |
| §G9 | 文档化（类级豁免属调用侧组合） | 铁傀儡 `canAttackClass` 保持调用侧组合（与 `isEnemy` 并存） | 📖 文档化 |
| §G10 | `provokedBy`（revenge + dummy 归属统一判据） | `isEnemy` 内建报复窗口已同源；猪人 anger 记忆经 `updateAITasks` 恢复机制同步 revenge（uuid 恢复注入已做），**等价覆盖 angerMatches**，无需 accessor 桥。残余边缘见下 | ✅ 基本消化（边缘列出） |

§G10 残余边缘：猪人 `angerLevel>0` 但 `revengeTarget` 被第三方伤害覆盖时，
「angerTarget 仍是合法反击对象」的窗口不被 `provokedBy` 表达（需 angerTargetUUID
访问器桥）。发生率低（需连续被两方伤害），保持现状，进游戏验证阶段观察。

## 8. 构建验证与 jar 切换状态（本轮）

- **构建**：`gradle compileJava --offline` **BUILD SUCCESSFUL**（Gradle 4.9 + JDK8）。
  环境适配：`~/.gradle` 只读（DSH 沙箱），将 `caches/minecraft`（141M）与
  `caches/modules-2`（658M）复制到可写 `~/.cache/gradle-home` 后用已解压的
  gradle-4.9 二进制离线编译（零联网）。编译期修正 4 处：`MultiPartEntityPart`
  实际包为 `net.minecraft.entity`（无 `EntityDragonPart` 类，dragonPartHead 即
  MultiPartEntityPart）；`launchWitherSkullToEntity`/`attackDragonFrom` 为
  private/protected → `@Shadow` 声明；1.12.2 无 `World.getEntityByUuid`
  （1.13+ API）→ 猪人 UUID 泛型解析改遍历 `loadedEntityList`。
- **HateQueries 委托切换**：**已完成**（2026-11-05 收尾轮）。morph-extension 新 jar
  （含 `FactionEnmity`/`AggressionPolicy`/`IEnmityQuery` 扩展，经 `unzip -l` 核实）
  已就位，`HateQueries` 切为纯委托门面（删除自实现图判定与 dummy 归一逻辑），
  调用侧按 §7.1 消化新 API（owner 豁免内建、resolveDamageSource 归因、
  锚点/类型化 findNearestEnemy、DEFENSIVE 策略）。切换后
  `gradle compileJava --offline` **BUILD SUCCESSFUL**（12s）。

## 9. Cleanroom 崩溃急修记录（2026-11-06）

**事故**：用户 Cleanroom 实例启动崩溃
（`NoClassDefFoundError: EntitySilverfish` ← `MixinTransformerError` ←
`ClassCastException: ArrayList cannot be cast to AnnotationNode` at
`com.llamalad7.mixinextras.wrapper.factory.FactoryRedirectWrapperMixinTransformer.transform`）。
Forge 实例正常。

**根因**（静态定位，未启动游戏）：MixinBooter 12.2 捆绑旧 MixinExtras（0.2.x-beta，
含 FactoryRedirectWrapper，专门包装 `@Redirect(NEW)`）；该 transformer 对**每个含
`@Redirect` 的 mixin 方法**执行 `Annotations.getValue(redirect,"at",true)` 并把列表
元素强转 `AnnotationNode`（反汇编 83 行 checkcast 确认）。javac 把 `@Redirect.at`
编译为注解数组存储（javap 产物显示 `at=[@At(...)]`），Cleanroom 自带新版 Mixin 的
`Annotations` 对该结构的返回语义与旧 MixinExtras 期望不兼容（元素为 `ArrayList`）
→ 必崩。**Forge 实例用 MixinBooter 自带旧 Annotations（自洽）所以正常；
morph-extension 全库零 `@Redirect` 所以双运行时幸免**——差异点即本仓库 15 处
`@Redirect`（11 处 NEW + 4 处 INVOKE），崩在首个被类加载命中的 EntitySilverfish。

**修复**（全部消除 `@Redirect`，改双运行时兼容注解）：
- 11 处 `@Redirect(NEW)`（苦力怕/僵尸/骷髅/蠹虫/末影螨/卫道士/恼鬼/烈焰人/狼/
  史莱姆/恶魂的目标替换）→ **`@ModifyArg`**（改 `EntityAITasks.addTask` 的 AI 参数，
  index=1）+ 新增 `TargetSwap`（反射读 `targetClass`/`shouldCheckSight` SRG 字段，
  按原实例类型换 `NearestEnemyPlayerTarget`/`NearestEnemyTarget`/`FindNearestEnemyPlayer`，
  保持原 checkSight 语义；村民/铁傀儡等非玩家目标原样保留）；
- 3 处 `@Redirect(INVOKE)`：猪人 UUID 仇恨恢复 → `@Inject HEAD`（补非玩家恢复，
  玩家路径保留原版）；凋灵头颅过滤 → `@Inject launchWitherSkullToEntity HEAD cancel`；
  末影龙伤害门 → `@Inject attackDragonFrom HEAD cancel`；
- 龙阶段 3 Mixin（`@Redirect` 无法低成本等价改写，方法体 40–50 行/个）→
  **摘出 mixin config 降级「待改写」**（源文件保留），保启动优先；
- 静态校验：`javap -v` 逐类确认编译产物零 `injection/Redirect` 注解引用；
  config 内 22 个 mixin 全为 @Inject/@ModifyArg/@Mixin/@Shadow。

**构建与同步**：`gradle build --offline` BUILD SUCCESSFUL（含 reobfJar）；
`scripts/sync-game.sh --no-build` 发布+同步+校验完成：
`aaaa0ggmc_ul_modpack-1.0.0.jar`（159,878 B）
SHA-256 `21f391fd08e44a52cc8bcc347c13da3bbe737e2b00ebe1c976d3a5902a9b3e33`
→ `_shared/mods/common/` + `shaping_the_world/mods/` + `shaping_the_world_cr/mods/`
三处一致（cmp 校验通过，旧 jar 已入 `_backups/mods/`）。未启动游戏。

**遗留风险**：① `@ModifyArg` 亦经 MixinExtras 扩展处理（expressions 包装），本次未
进游戏验证；若 Cleanroom 仍崩（换一个 ClassCastException 点），fallback=删除这批
注入点依赖 morphext 通用漏斗（`MixinEntityAITarget` 候选谓词过滤已等价覆盖
「找最近敌人玩家」）；② 龙阶段 3 点待以 `@Inject` 复刻重做；③ TargetSwap 反射
失败时静默退回原版行为（不崩、不误伤）。

**用户存疑点（本轮未动，记录待查）**：① 重生后 relation query 射线全 miss；
② 生存下 `/morph demorph` 静默失败。两者与本次崩溃无直接关联，列入待查。

## 10. 补全轮（2026-11-06，原版生物遗漏类 + 龙阶段补回）

- **新增注入**：女巫、唤魔者（攻击+躲避双注入）、幻术师（`EntityIllusionIllager`，
  1.12.2 实名）、豹猫（躲避，@Inject 复刻保持 remove/add 配对）；
  TargetSwap 扩展 unseenMemoryTicks 复制（链式注册）与 `EntityAIAvoidEntity`
  敌人谓词替换（§G6 躲避语义，参数反射复制）；
- **龙阶段 3 点补回**：HoldingPattern/LandingApproach/SittingScanning 以
  `@Inject HEAD cancel` + 方法体复刻重做（查询行换 `findNearestEnemy`，其余逐行原版），
  重新入 mixin config；
- **审计判定**：羊驼（spit 目标泛型/AIDefendTarget 打狼——无玩家硬编码仇恨）不改；
  雪傀儡（只打 IMob）不改；Rabbit/Squid 等受击恐慌为 AIPanic 泛型触发不改；
- **硬约束核查**：全仓源码 grep + 编译产物 javap 双查，`@Redirect`/`@WrapOperation`/
  `@ModifyExpressionValue` 等 MixinExtras 注解**零使用**（注释提及除外）。

## 11. 蝙蝠惊飞补回（2026-11-06，收尾遗留）

- **实现**：新增 `MixinEntityBat`（入 `mixins.ulmodpack.json`），对
  `EntityBat.updateAITasks`（`func_70619_bc`）做 `@Inject HEAD cancel` + 全段复刻。
  与原版逐行一致的只有玩家查询这一行：
  - 原：`this.world.getNearestPlayerNotCreative(this, 4.0) != null`；
  - 新：`HateQueries.findNearestEnemy(self, 4.0D, EntityPlayer.class,
    p -> !p.isSpectator() && !p.isCreative())`（§G6 躲避 + §G4 类型化）。
  位置（蝙蝠脚下方块）、范围（4.0 欧氏距离）、倒挂/起飞事件 1025、随机再倒挂
  （`nextInt(100)==0`）与随机转头（`nextInt(200)==0`）全部保持原版。
- **语义**：普通无阵营玩家 → `isEnemy` 回退 MODERATE=敌对 → 照常惊飞（原版行为）；
  同阵营变形玩家（蝙蝠/动物形态，阵营 `animal` 同族 RELATIVE）→ 不惊飞。
  创造/旁观玩家仍按原版排除。判定本身纯读，惊飞状态改写发生在服务端
  `updateAITasks` tick 内。
- **构建与同步**：`./gradlew build --offline` **BUILD SUCCESSFUL**；
  `scripts/sync-game.sh --no-build` 发布 + 同步 + `cmp` 校验全部一致。
  `aaaa0ggmc_ul_modpack-1.0.0.jar`（165,761 B）
  SHA-256 `0c9427254bb34f04fbb131d4163644bc01f59e3aa41f836dd09b015f6b33e46c`
  → `_shared/mods/common/`、`shaping_the_world/mods/`、`shaping_the_world_cr/mods/`
  三处一致（硬链接）。
- **静态核查**：编译产物 `javap -v` 仅见 `@Mixin`/`@Inject`/`@Shadow`，
  无 `@Redirect`/`@WrapOperation`/`@ModifyExpressionValue`；`super.updateAITasks()`
  reobf 后为 `invokespecial EntityAmbientCreature.func_70619_bc`（父链为空实现，
  保留调用以维持链）。
- **未做**：未进游戏验证（任务要求不启动游戏）；需进游戏确认变形动物形态靠近
  倒挂蝙蝠不再惊飞、普通玩家照常惊飞。
