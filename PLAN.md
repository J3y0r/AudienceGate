# Minecraft 200人同服定制方案

## 目标容量

- 在线人数：200
- 自由活动人数上限：24
- 静态观众人数：约176
- 目标TPS：20
- 目标MSPT：常态 < 35ms，峰值 < 45ms
- 后端：单个 Minecraft 服务端实例，非分服承载

---

## 服务端技术栈

- 服务端核心：Purpur 1.21.x
- Java：Java 21
- 系统：Ubuntu Server 22.04 / 24.04
- 入口层：Velocity
- 后端内存：20G
- Velocity内存：512M–1G
- 存储：NVMe SSD
- 公网上行：≥300Mbps，推荐1Gbps
- 插件原则：极少插件，只保留权限、监控、定制观众插件

固定不采用：

- Forge / Fabric 模组服
- Folia
- 大型RPG插件包
- 动态地图插件
- 大量NPC、全息字、动态记分板、复杂反作弊

---

## 部署结构

```text
玩家客户端
   ↓
Velocity：限流 / 排队 / 转发 / 防登录洪峰
   ↓
Purpur：单实例承载200人同服
   ↓
AudienceGate：观众休眠与活动名额控制插件
```

Velocity只做入口控制，不拆分服务器。所有进入后端的玩家处于同一个服务端实例内。

---

## 核心定制插件：AudienceGate

### 玩家状态

```text
ACTIVE    = 可移动、可交互、可参与玩法
AUDIENCE  = 静态站位、不可移动、不可交互、低视距、低广播
```

固定参数：

```yaml
active-cap: 24

audience:
  view-distance: 2
  simulation-distance: 2
  allow-chat: false
  allow-command: false
  allow-interact: false
  allow-move: false
  allow-look-packet: false
  invulnerable: true
  collision: false
  pickup-items: false

active:
  view-distance: 6
  simulation-distance: 3
  allow-chat: true
  allow-command: true
  allow-interact: true
  allow-move: true

visibility:
  audience-see-audience: false
  audience-see-active: true
  active-see-audience: true
  active-see-active: true

position:
  p1: x/y/z/yaw/pitch
  p2: x/y/z/yaw/pitch
  ......
```

### 可见性矩阵

| 观看者 | 目标 | 结果 |
|---|---|---|
| 活动玩家 | 活动玩家 | 可见 |
| 活动玩家 | 观众玩家 | 可见 |
| 观众玩家 | 活动玩家 | 可见 |
| 观众玩家 | 观众玩家 | 不可见 |

该矩阵把观众之间的`N²实体广播`砍掉。200人同屏压力的关键瓶颈不是内存，而是玩家实体之间的追踪、移动广播和包处理。

---

## AudienceGate关键行为

### 入服行为

- 玩家进入后端立即进入`AUDIENCE`
- 传送到固定观众座位
- 设置个人视距为2
- 设置个人模拟距离为2
- 禁止碰撞
- 禁止拾取物品
- 禁止伤害
- 禁止聊天
- 禁止交互
- 禁止移动与视角包进入主线程
- 观众之间互相隐藏

### 活动名额

- 同时`ACTIVE`人数固定为24
- 管理员或队列系统授予活动状态
- 活动玩家恢复移动、交互、聊天与正常视距
- 活动玩家可看见全部活动玩家与观众玩家
- 活动玩家离开后释放名额

### 观众冻结

观众不使用旁观者模式，保留真实玩家实体给活动玩家观看。

观众冻结采用三层控制：

```text
事件层：取消移动、交互、破坏、放置、丢弃、拾取、聊天
包层：丢弃观众的移动包、视角包、挥手包、交互包
同步层：周期性把观众校正回座位坐标
```

---

## ProtocolLib包过滤示例

使用ProtocolLib拦截观众包。

核心过滤目标：

```java
PacketType.Play.Client.POSITION
PacketType.Play.Client.POSITION_LOOK
PacketType.Play.Client.LOOK
PacketType.Play.Client.FLYING
PacketType.Play.Client.ARM_ANIMATION
PacketType.Play.Client.USE_ENTITY
PacketType.Play.Client.BLOCK_DIG
PacketType.Play.Client.BLOCK_PLACE
PacketType.Play.Client.USE_ITEM
PacketType.Play.Client.HELD_ITEM_SLOT
```

逻辑：

```java
if (audiencePlayers.contains(player.getUniqueId())) {
    event.setCancelled(true);
}
```

保留以下包：

```text
KEEP_ALIVE
TELEPORT_ACCEPT
CLIENT_SETTINGS
PONG
RESOURCE_PACK_STATUS
```

这样观众客户端保持在线，但移动、转头、挥手、交互不会进入服务端主逻辑。

---

## Bukkit事件拦截

观众状态下取消：

```text
PlayerMoveEvent
PlayerInteractEvent
BlockBreakEvent
BlockPlaceEvent
PlayerDropItemEvent
EntityPickupItemEvent
InventoryClickEvent
InventoryDragEvent
AsyncChatEvent
PlayerCommandPreprocessEvent
EntityDamageEvent
FoodLevelChangeEvent
PlayerSwapHandItemsEvent
PlayerItemHeldEvent
```

观众属性：

```java
player.setGameMode(GameMode.ADVENTURE);
player.setInvulnerable(true);
player.setCollidable(false);
player.setCanPickupItems(false);
player.setWalkSpeed(0.0f);
player.setFlySpeed(0.0f);
player.setViewDistance(2);
player.setSimulationDistance(2);
player.setSleepingIgnored(true);
```

统一无碰撞队伍：

```java
Team team = board.getTeam("nocollision");
team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
team.addEntry(player.getName());
```

---

## 推荐插件清单

保留：

```text
AudienceGate        自研核心插件
LuckPerms           权限
spark               性能监控
Chunky              区块预生成
ProtocolLib         包过滤依赖
```

禁止或避免：

```text
Dynmap / BlueMap
Citizens
TAB动态动画
PlaceholderAPI高频变量刷新
CoreProtect全量记录
ItemsAdder / Oraxen大型资源系统
MythicMobs
ModelEngine
GSit类高频姿态插件
大型反作弊
语音插件
复杂菜单插件
```

---

## 验收标准

服务器达到以下状态即合格：

```text
200人在线
24人ACTIVE持续移动
176人AUDIENCE静态站位
TPS稳定20
MSPT平均 < 30ms
MSPT P95 < 40ms
无新区块生成
无实体堆积
无红石/漏斗热点
无观众移动包进入主线程
```

## 最终容量判断

16核32GB机器承载200人同服可行，但必须把绝大多数玩家改造成“低广播、低视距、不可交互、不可移动”的观众态。

稳定上限建议锁定为：

```text
200在线
24自由活动
176静态观众
```

该结构比直接让200个普通玩家站在同一区域稳定得多，主要收益来自：

```text
观众互相不可见
观众移动/视角包丢弃
观众个人视距降到2
禁止新区块生成
禁止生物与红石负载
活动人数硬上限
Velocity削平登录峰值
```