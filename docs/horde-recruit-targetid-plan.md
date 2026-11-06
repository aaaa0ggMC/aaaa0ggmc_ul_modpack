# horde_recruit 客户端射线 targetId 通道——实施方案（待确认后执行）

> 状态：**已实施**（A/B 段 2026-11-06 落地，见 `HordeManager.findLooked*` 与核心
> targetId 通道）。2026-11-07 后续变更：`horde_recruit / horde_launch / horde_release`
> 三能力合并为 `horde_command`（服务端按「目标 + 潜行」分派），`clientPickTarget`
> 放宽为任意活体（`findLookedLivingId`），无目标不再批量兜底、直接失败提示；
> 下文保留原方案记录。原文：按任务约束「若必须改 morph-extension 才能加 targetId，
> 先停下出方案等确认」——调查确认 targetId 通道必须动核心（AbilityNetwork 在
> morph-extension），故本轮只出方案。确认后建议由 morph-extension 线程做 A 段
> （或授权本线程代改），本线程做 B 段并构建 sync。

## 0. 调查结论（为什么必须动核心）

| 环节 | 现状 | targetId 可行性 |
| --- | --- | --- |
| `AbilityNetwork.Request` | 序列化仅 `id`（`readId(buf)` / `writeUTF8String`）| 无扩展字段，需加 |
| `AbilityNetwork.RequestHandler` | `ServerAbilityService.activate(player, message.id)` 无参 | 无目标传递通道 |
| `MorphContext` | 字段=player/morph/morphName/dummy（dummy=变身替身，非目标槽） | 无 target 槽 |
| `AbilityParams` | 配置参数（形态条目 params），**不下发** | 不是请求参数通道 |
| `IActiveAbility.activate(player, context)` | 固定签名 | context 可携带（加槽不破坏签名） |
| ul-modpack `ul:ability` 通道 | S2C 队伍快照（C2S 手势包已删） | 不新建独立包（任务约束） |

结论：零核心改动无干净路径（id 后缀/前导包等均为 hack，违背「保持简单」的分层）。

## A. morph-extension 侧（3 处小改，全部向后兼容）

### A1. `AbilityNetwork.Request` 加可选 targetId

```java
public static class Request implements IMessage {
    public String id;
    public int targetId;                       // 0 = 无目标（可选字段）
    @Override public void fromBytes(ByteBuf buf) {
        id = readId(buf);
        targetId = buf.isReadable() ? buf.readInt() : 0;   // 旧客户端不写 → 0
    }
    @Override public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, id);
        buf.writeInt(targetId);                            // 新客户端写
    }
}
```

**向后兼容**（FML SimpleNetworkWrapper 按 discriminant 收包，payload 尾部多余字节
静默丢弃、缺字节按可读性判断）：
- 旧客户端 → 新服务端：读完 id 无可读字节 → `targetId=0` → 无目标逻辑 ✓
- 新客户端 → 旧服务端：多写的 int 被旧端忽略（旧 Request.fromBytes 只读 id）✓
- Reply 不动（S2C 回执不带目标，任务要求）✓；旧存档无关（无持久化）✓

### A2. 目标槽位：`MorphContext` 加可选 target

```java
@Nullable private final EntityLivingBase target;          // 新字段
public MorphContext(EntityPlayer player, @Nullable AbstractMorph morph) { ... target = null; }
// 保留既有构造器全部签名；新增：
public MorphContext(EntityPlayer player, @Nullable AbstractMorph morph,
                    @Nullable EntityLivingBase target) { ... }
@Nullable public EntityLivingBase getTarget() { return target; }
```

`ServerAbilityService.activate(player, id)` 保持不变（target=null）；新增重载
`activate(player, id, @Nullable EntityLivingBase target)`（旧行为完全不变）。
`RequestHandler` 解析：`world.getEntityByID(message.targetId)`，`instanceof EntityLivingBase`
才传入，否则 null（防脏 id/跨维/已消失——宽松校验，详细规则在 ul-modpack 侧）。

### A3. 客户端射线 SPI（分层：射线规则在 ul-modpack，通道在核心）

`IActiveAbility` 加默认方法（能力实现零破坏）：

```java
default int clientPickTarget(EntityPlayer player) { return 0; }
```

`ClientAbilityExecutor` 统一释放链路在调用 `sendRequest` 前取
`ability.clientPickTarget(player)` 并传入新重载 `AbilityNetwork.sendRequest(id, targetId)`。
（若 morph-ext 线程倾向别的挂点，可换；原则=射线规则归 ul-modpack、传输归核心。）

## B. ul-modpack 侧（核心 A 落地后本线程实施）

### B1. 客户端射线（`RecruitAbility.clientPickTarget` 覆写）

- 沿用 `HordeManager.findLookedZombie` 的「射线 + 最近实体」语义：从玩家眼睛沿视线
  在 `recruitReach()`（`zombie_tide.horde.recruit_reach`，默认 4.5–6 格）内找
  `HordeManager.isZombieFamily` 实体；命中返回 `entity.getEntityId()`，否则 0。
- 纯目标选择：**不改客户端权威执行**（执行仍在服务端统一链路）。

### B2. 服务端（`RecruitAbility.activate` 改造）

- `context.getTarget() != null` → 宽松校验后 `HordeManager.tryRecruitAt(player, target)`：
  - 实体存在且 `isZombieFamily`；
  - 与玩家距离 ≤ max(recruitReach, 8.0)（宽松上限，防空指针/跨维异常即可，不查视线）；
  - 失败 → `ActivationResult.unavailable()`（或 FAILED，按现有 Status 语义选）。
- `target == null` → 现有 `HordeManager.tryRecruit(player)`（服务端 findLookedZombie/批量
  兜底）不变——**旧客户端行为完全保留**。

### B3. 其余能力不受影响

`horde_launch` / `horde_release` / `titan_slam`：`clientPickTarget` 保持默认 0，无目标。
（2026-11-07 更新：`horde_launch / horde_release` 已并入 `horde_command`，其
`clientPickTarget` 同样放宽为任意活体；`titan_slam` 仍无目标。）

## C. 兼容性汇总

| 组合 | 行为 |
| --- | --- |
| 新客户端 + 新核心 | 射线目标招募（无目标→兜底批量） |
| 旧客户端 + 新核心 | targetId=0 → 原有无目标逻辑 |
| 新客户端 + 旧核心 | 多余字节被忽略 → 原有无目标逻辑 |
| 旧存档 | 无关（协议层无持久化） |

## D. 待确认（已解决：#1 并行做核心 A 段；B 段已实施）

1. A 段（morph-extension 3 处）由谁执行：morph-extension 线程做，还是授权本线程代改？
2. A3 的 SPI 挂点（`IActiveAbility.clientPickTarget`）是否为 morph-ext 线程接受的形态？
