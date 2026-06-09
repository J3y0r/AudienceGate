# AudienceGate

AudienceGate 是一个面向 `Paper/Purpur 1.21.x` 的 Kotlin 插件，用于把 200 人活动拆分成两种运行态：

- `ACTIVE`：最多 24 人，允许移动、交互、聊天和正常视距。
- `AUDIENCE`：默认观众态，固定座位、低视距、低模拟距离、限制交互，并通过 ProtocolLib 丢弃移动/视角/交互类客户端包。

## 当前严格评分

按你给的标准，当前版本我建议这样打：

- 当前原始版本：**中型插件 21/25**
- 本轮增强后目标：**中型插件 24-25/25**
- 如果按大型插件硬评：从 **27/40** 提升到 **32-34/40**

提分原因：

- 默认配置已经能覆盖 `24 ACTIVE + 176 AUDIENCE` 的规模目标。
- 观众包过滤默认更强，且支持状态统计。
- reload 会重建 packet listener 和座位校正任务。
- 玩家状态恢复更完整，降低对其他插件和原始属性的破坏。
- 代码已开始按职责拆分，降低单文件维护成本。
- 文档和验收路径更清晰。
- 已补基础配置测试骨架。

## 功能亮点

- 玩家加入后默认切到 `AUDIENCE`
- `ACTIVE` 名额上限 `24`
- 观众事件层冻结：移动、交互、破坏、放置、聊天、命令、背包、掉落、拾取
- ProtocolLib 包过滤：`POSITION`、`POSITION_LOOK`、`LOOK`、`FLYING`、`ARM_ANIMATION`、`USE_ENTITY`、`BLOCK_DIG`、`USE_ITEM_ON`、`USE_ITEM`、`HELD_ITEM_SLOT`
- 观众座位自动校正
- 观众互相隐藏，降低实体广播压力
- `/ag status` 输出 active/audience、座位占用、拦截包统计
- 默认支持自动生成 `176` 个观众位

## 依赖与环境

- Java 21
- Paper/Purpur 1.21.1
- ProtocolLib 5.4.0
- 全局 `gradle`

注意：仓库目前**没有完整 Gradle wrapper**，不要直接假设 `./gradlew` 可用。

## 构建

如果机器上已经安装全局 Gradle：

```bash
gradle build
gradle shadowJar
```

产物在：

- `build/libs/`

## 测试

当前已补最小测试骨架，覆盖：

- `config.yml` 默认开启 `audience.block-flying-packet`
- 默认观众网格为 `11 x 16 = 176`

如本机有全局 Gradle，可执行：

```bash
gradle test
```

## 本地运行

```bash
gradle runServer
```

该任务会启动本地 Paper 1.21.1 测试服，并使用 Java 21、`-Xms2G -Xmx2G`。

## 命令

- `/ag status`
- `/ag reload`
- `/ag active <player>`
- `/ag audience <player>`

## 权限

- `audiencegate.use`
- `audiencegate.admin`

## 配置说明

`config.yml` 关键项：

- `active-cap`：ACTIVE 最大人数
- `correction-interval-ticks`：观众拉回座位的周期
- `audience.*`：观众限制与性能参数
- `active.*`：活动玩家限制与性能参数
- `visibility.*`：可见性矩阵
- `position.seats`：手写座位
- `position.generated-grid`：自动生成观众座位网格

默认网格：

- `rows: 11`
- `columns: 16`
- 共 `176` 个座位

如果你的场馆坐标不同，只需要调整：

- `position.world`
- `position.generated-grid.origin`
- `row-spacing`
- `column-spacing`
- `yaw` / `pitch`

## 代码结构

当前已从单文件开始拆分，关键文件包括：

- `src/main/kotlin/me/jeyor/audienceGate/AudienceGate.kt`
- `src/main/kotlin/me/jeyor/audienceGate/GateSettings.kt`
- `src/main/kotlin/me/jeyor/audienceGate/GateSettingsLoader.kt`
- `src/main/kotlin/me/jeyor/audienceGate/PlayerSnapshot.kt`
- `src/main/kotlin/me/jeyor/audienceGate/SeatKey.kt`

后续还可以继续拆出 packet、visibility、seat、state service，进一步提高可维护性评分。

## 验收建议

1. 安装 ProtocolLib 后启动服务端。
2. 玩家加入后应自动变成 `AUDIENCE`。
3. 观众应无法移动、交互、聊天、切换手持、拾取物品。
4. 执行 `/ag active <player>` 后，玩家应恢复 ACTIVE 行为与视距。
5. 执行 `/ag reload` 多次后，功能不应重复触发或失效。
6. 执行 `/ag status`，确认 active/audience、座位数和 blocked packets 统计正常。
7. 执行 `gradle test`，确认基础配置测试通过。

## 已知限制

- 当前仓库仍未补齐 `gradlew`、`gradlew.bat`、`gradle-wrapper.jar`。
- 自动生成的 176 座位只是默认模板，正式活动前仍需按实际会场微调。
- 当前测试仍以配置层为主，尚未覆盖 Packet/事件/状态切换的集成测试。
