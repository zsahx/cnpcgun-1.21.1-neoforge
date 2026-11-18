# 1.20.1 → 1.21.1 NeoForge 移植记录

按「编译报错驱动」的方式逐条改，最后 `compileJava` 从 102 个错误降到 0。

## 1. 命名

- 反编译产物里方法名是 SRG（`m_9236_`、`f_46443_`），类名已经是 Mojang 名。
- 用 `joined.tsrg`(obf→srg) 与 Mojang `client-1.20.1-mappings.txt`(obf→mojang) 以
  `(obf类, obf成员, 描述符)` 为键连接，得到 SRG→Mojang 字典（63877 条），脚本全量替换。
- 1.21.1 的 NeoForge 运行时**不再用 SRG 名**，所以移植后的代码里直接用 Mojang 名，不需要 refmap。

## 2. 平台层

| 1.20.1 Forge | 1.21.1 NeoForge |
|---|---|
| `net.minecraftforge.*` | `net.neoforged.neoforge.*`（`fml`→`net.neoforged.fml`，`eventbus`→`net.neoforged.bus`） |
| `MinecraftForge.EVENT_BUS` | `NeoForge.EVENT_BUS` |
| `@Mod` 无参构造 | `@Mod` 构造参数 `(IEventBus modBus, ModContainer container)` |
| `Mod.EventBusSubscriber`（已删除） | 改为在构造函数里 `NeoForge.EVENT_BUS.register(XxxEvents.class)` / `modBus.addListener(...)` |
| `LivingHurtEvent` / `LivingAttackEvent` | `LivingIncomingDamageEvent`（统一、可取消，`getSource()`/`getEntity()`/`getAmount()`） |
| `LivingEvent.LivingTickEvent` | `event.tick.EntityTickEvent.Post` |
| `TickEvent.ServerTickEvent` + `Phase` | `event.tick.ServerTickEvent.Pre` / `.Post` |
| `TickEvent.ClientTickEvent` + `Phase` | `client.event.ClientTickEvent.Post` |
| `NetworkRegistry.newSimpleChannel` / `SimpleChannel` | `RegisterPayloadHandlersEvent` + `CustomPacketPayload` + `StreamCodec` |
| `PacketDistributor.PLAYER/TRACKING_ENTITY` | `PacketDistributor.sendToPlayer` / `sendToPlayersTrackingEntity` |
| `ForgeRegistries.ITEMS` | `BuiltInRegistries.ITEM` |
| `new ResourceLocation(a,b)` | `ResourceLocation.fromNamespaceAndPath(a,b)` |
| matter | `ItemStack.save(HolderLookup.Provider, CompoundTag)` / `ItemStack.parseOptional(...)`（新增 `ItemData` 工具类） |
| matter | `ItemStack.hasTag()/getTag()` → `DataComponents.CUSTOM_DATA` |
| `ItemStack.OPTIONAL_STREAM_CODEC` | 需要 `RegistryFriendlyByteBuf`，所以 `Response` payload 的编解码缓冲改成它 |

## 3. 游戏层

- `SavedData#save(CompoundTag)` → `save(CompoundTag, HolderLookup.Provider)`；
  `DimensionDataStorage#computeIfAbsent(SavedData.Factory, name)`（工厂需带 `DataFixTypes.LEVEL`）。
  `MinecraftServer#overworld().getDataStorage()`。
- `ServerLevel#getAllLevels()`（`m_129785_`）、`server.getLevel(key)`（`m_129880_`）。
- `SmallFireball(Level, LivingEntity, Vec3)`（原来 5 参版本已删）。
- `Explosion#getExploder()/getDamageSource()` → `getDirectSourceEntity()/getIndirectSourceEntity()`，
  伤害源用 `level.damageSources().explosion(explosion)`。
- `Holder<MobEffect>#isBeneficial()` → `holder.value().isBeneficial()`。
- GUI：`Screen#setTooltipForNextRenderPass` 变 public；
  `mouseScrolled` 多一个水平滚动参数；`EditBox#tick()` 删除（改由 `renderWidget` 按时间闪烁）；
  `InventoryScreen#renderEntityInInventoryFollowsMouse` 多了绘制框参数；
  `HumanoidModel.ArmPose#applyTransform` 删除（挪到 `HumanoidModel#poseRightArm/poseLeftArm`，
  `GunIdlePoseMixin` 因此改注入点）。
- `AbstractProjectileDispenseBehavior` 删除（1.21 改成 `ProjectileItem` 体系），对应的
  `ProjectileFactoryAccess` invoker mixin 没有任何调用方，直接删掉。

## 4. 反编译器产物修补

- 重复局部变量（`GunController.noAmmo`、`Parameter.validate`）、变量作用域错位
  （`CombatService` 的 `gunx` lambda）、lambda 捕获非 final 循环变量（`NodeDialog`、`GuiWeapons`）、
  原始类型 `ArrayList` → 泛型，都手工修好。
- 反编译会把 mixin 里的 `(Entity)this` 生成成非法强转，改为 `(Entity)(Object)this`。

## 5. 依赖裁剪

`bincnpc/gun/compat/{superb,cgm,artifice,lr,cnpcplus,pathfinding,slashblade}` 与对应 mixin 移除；
`GunPersistence` 改为直接用 NPC 自身 NBT；`ThrowAdapters` 只保留原版投掷物适配；
`PathfindingBridge` 变空实现；`AdapterRegistry` 的 `adapters.json` 仍按 ModList 检测，缺模组时自动跳过。
