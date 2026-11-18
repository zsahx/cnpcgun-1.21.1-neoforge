package bincnpc.gun.common.network;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.client.ClientPackets;
import bincnpc.gun.client.animation.NpcAnimations;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.config.ConfigSchema;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.common.persistence.ItemPresetLibrary;
import bincnpc.gun.common.persistence.PresetLibrary;
import bincnpc.gun.gun.adapter.GunAdapter;
import bincnpc.gun.slashblade.action.ActionConfig;
import bincnpc.gun.slashblade.action.ActionRegistry;
import bincnpc.gun.slashblade.combo.GraphValidator;
import bincnpc.gun.slashblade.strategy.Presets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import noppes.npcs.CustomItems;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityNPCInterface;

public final class GunNetwork {
   public static final UUID NO_TOKEN = new UUID(0L, 0L);
   public static final int MAX_SNAPSHOT = 524288;
   public static final String PROTOCOL = "4";
   private static final Map<UUID, GunNetwork.Session> SESSIONS = new HashMap<>();
   private static final Map<UUID, GunNetwork.Rate> RATES = new HashMap<>();
   private static ThreadPoolExecutor worker;

   private GunNetwork() {
   }

   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar registrar = event.registrar(PROTOCOL);
      registrar.playToServer(Request.TYPE, Request.STREAM_CODEC, GunNetwork::handleRequest);
      registrar.playToClient(Response.TYPE, Response.STREAM_CODEC, GunNetwork::handleResponse);
      registrar.playToClient(Animation.TYPE, Animation.STREAM_CODEC, GunNetwork::handleAnimation);
   }

   private static void handleRequest(Request payload, IPayloadContext context) {
      if (context.player() instanceof ServerPlayer player) {
         context.enqueueWork(() -> accept(player, payload));
      }
   }

   private static void handleResponse(Response payload, IPayloadContext context) {
      if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
         context.enqueueWork(() -> ClientPackets.receive(payload));
      }
   }

   private static void handleAnimation(Animation payload, IPayloadContext context) {
      if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
         context.enqueueWork(() -> NpcAnimations.receive(payload));
      }
   }

   public static void startWorker() {
      stopWorker();
      worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32), task -> {
         Thread thread = new Thread(task, "CNPCGun-configuration");
         thread.setDaemon(true);
         return thread;
      }, new AbortPolicy());
   }

   public static void stopWorker() {
      if (worker != null) {
         worker.shutdownNow();
      }

      worker = null;
      SESSIONS.clear();
      RATES.clear();
   }

   public static void forget(ServerPlayer player) {
      GunNetwork.Session session = SESSIONS.remove(player.getUUID());
      RATES.remove(player.getUUID());
      finishEditing(player, session);
   }

   private static void finishEditing(ServerPlayer player, GunNetwork.Session ended) {
      if (ended != null && player.getServer() != null) {
         for (ServerLevel level : player.getServer().getAllLevels()) {
            if (level.getEntity(ended.npcId) instanceof EntityNPCInterface npc && CombatService.validNpc(npc)) {
               long remaining = SESSIONS.values()
                  .stream()
                  .filter(session -> session.npcId.equals(ended.npcId))
                  .mapToLong(session -> session.expires)
                  .max()
                  .orElse(0L);
               if (remaining > level.getGameTime()) {
                  CombatService.data(npc).runtime.editorUntil = remaining;
               } else {
                  CombatService.endEditing(new CombatContext(npc));
               }
               break;
            }
         }
      }
   }

   private static EntityNPCInterface editing(ServerPlayer player, UUID id, GunNetwork.Operation operation) {
      EntityNPCInterface npc = NoppesUtilServer.getEditingNpc(player);
      if (npc == null
         || !id.equals(npc.getUUID())
         || !CombatService.validNpc(npc)
         || !player.isAlive()
         || npc.level() != player.level()
         || player.distanceToSqr(npc) > 256.0
         || !player.getMainHandItem().is(CustomItems.wand) && !player.getOffhandItem().is(CustomItems.wand)
         || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)
         || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_ADVANCED)) {
         return null;
      } else {
         return Set.of(
                     GunNetwork.Operation.EXCHANGE,
                     GunNetwork.Operation.UNEQUIP,
                     GunNetwork.Operation.SUPPLY,
                     GunNetwork.Operation.SWITCH,
                     GunNetwork.Operation.PRIMARY_WEAPON,
                     GunNetwork.Operation.EQUIP_CREATIVE,
                     GunNetwork.Operation.SAVE_ITEM_PRESET,
                     GunNetwork.Operation.EQUIP_ITEM_PRESET,
                     GunNetwork.Operation.DELETE_ITEM_PRESET
                  )
                  .contains(operation)
               && !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY)
            ? null
            : npc;
      }
   }

   private static boolean rate(ServerPlayer player, long now) {
      GunNetwork.Rate rate = RATES.computeIfAbsent(player.getUUID(), key -> new GunNetwork.Rate());
      if (now - rate.window >= 20L || now < rate.window) {
         rate.window = now;
         rate.count = 0;
      }

      return ++rate.count <= 8;
   }

   private static void accept(ServerPlayer player, GunNetwork.Request request) {
      long now = player.level().getGameTime();
      if (request.requestId > 0 && (request.operation == GunNetwork.Operation.CLOSE || rate(player, now))) {
         EntityNPCInterface npc = editing(player, request.npcId, request.operation);
         if (npc == null) {
            denied(player, request, "PERMISSION_DENIED");
         } else {
            GunNetwork.Session session = SESSIONS.get(player.getUUID());
            if (request.operation == GunNetwork.Operation.OPEN) {
               GunNetwork.Session previous = SESSIONS.remove(player.getUUID());
               finishEditing(player, previous);
               session = new GunNetwork.Session(npc.getUUID(), now);
               SESSIONS.put(player.getUUID(), session);
            } else if (session == null || !session.npcId.equals(request.npcId) || !session.token.equals(request.token) || session.expires < now) {
               denied(player, request, "PERMISSION_DENIED");
               return;
            }

            if (request.requestId <= session.lastRequest) {
               denied(player, request, "CONFLICT");
            } else if (request.operation == GunNetwork.Operation.CLOSE) {
               SESSIONS.remove(player.getUUID());
               finishEditing(player, session);
            } else if (session.pending != 0) {
               denied(player, request, "BUSY");
            } else {
               session.lastRequest = request.requestId;
               session.expires = now + 1200L;
               CombatContext ctx = new CombatContext(npc);
               CombatService.refresh(ctx);
               CombatService.beginEditing(ctx, session.expires);
               if (request.operation != GunNetwork.Operation.HEARTBEAT) {
                  if (request.operation != GunNetwork.Operation.OPEN
                     && request.operation != GunNetwork.Operation.REFRESH
                     && request.revision != ctx.config.revision) {
                     respond(player, session, request.requestId, ctx, Result.fail("CONFLICT"));
                  } else {
                     try {
                        JsonObject input = SafeJson.parseObject(request.json);
                        if (request.operation == GunNetwork.Operation.GRAPH || request.operation == GunNetwork.Operation.ACTION_CONFIG) {
                           validateAsync(player.getServer(), player.getUUID(), session, request, input);
                           return;
                        }

                        Result result = apply(player, ctx, request.operation, input);
                        respond(player, session, request.requestId, new CombatContext(npc), result);
                     } catch (CreativeCatalogue.Changed var9) {
                        CNPCGun.LOGGER
                           .debug("CNPC Gun catalogue request {} rejected for NPC {}: {}", new Object[]{request.operation, request.npcId, var9.getMessage()});
                        respond(player, session, request.requestId, ctx, Result.fail("CATALOGUE_CHANGED"));
                     } catch (IllegalStateException | IllegalArgumentException var10) {
                        respond(player, session, request.requestId, ctx, Result.fail("INVALID_ARGUMENT"));
                     } catch (RuntimeException var11) {
                        CNPCGun.LOGGER.warn("CNPC Gun editor request {} failed for NPC {}", new Object[]{request.operation, request.npcId, var11});
                        denied(player, request, "INTERNAL_ERROR");
                     }
                  }
               }
            }
         }
      }
   }

   private static void validateAsync(MinecraftServer server, UUID playerId, GunNetwork.Session session, GunNetwork.Request request, JsonObject input) {
      Map<String, ActionRegistry.Definition> actions = ActionRegistry.INSTANCE.snapshot();
      UUID token = session.token;
      session.pending = request.requestId;
      if (worker == null) {
         session.pending = 0;
         ServerPlayer player = server.getPlayerList().getPlayer(playerId);
         if (player != null) {
            denied(player, request, "BUSY");
         }
      } else {
         try {
            worker.execute(() -> {
               JsonObject checked = null;
               Result failure = null;

               try {
                  if (request.operation == GunNetwork.Operation.GRAPH) {
                     checked = GraphValidator.validate(input, actions, false).original;
                  } else {
                     String id = string(input, "id", "");
                     ActionRegistry.Definition def = actions.get(id);
                     if (def == null) {
                        failure = Result.fail("NOT_FOUND");
                     } else {
                        checked = new JsonObject();
                        checked.addProperty("id", id);
                        checked.add("config", ActionConfig.validate(object(input, "config", true), def));
                     }
                  }
               } catch (RuntimeException var10) {
                  failure = Result.fail("INVALID_ARGUMENT");
               }

               JsonObject validated = checked;
               Result error = failure;
               server.execute(() -> {
                  ServerPlayer current = server.getPlayerList().getPlayer(playerId);
                  GunNetwork.Session active = SESSIONS.get(playerId);
                  if (current != null && active != null && active.token.equals(token) && active.pending == request.requestId) {
                     active.pending = 0;
                     EntityNPCInterface npc = editing(current, request.npcId, request.operation);
                     if (npc != null && active.expires >= current.level().getGameTime()) {
                        CombatContext ctx = new CombatContext(npc);
                        CombatService.refresh(ctx);
                        if (ctx.config.revision != request.revision) {
                           respond(current, active, request.requestId, ctx, Result.fail("CONFLICT"));
                        } else {
                           Result result = error;
                           if (error == null) {
                              if (request.operation == GunNetwork.Operation.GRAPH) {
                                 boolean allowed = EditorWeapons.has(ctx, "slashblade.attack");

                                 for (JsonElement raw : validated.getAsJsonArray("nodes")) {
                                    ActionRegistry.Definition definition = ActionRegistry.INSTANCE.get(raw.getAsJsonObject().get("action").getAsString());
                                    if (definition != null && !EditorWeapons.adapter(ctx, definition.adapterId)) {
                                       allowed = false;
                                    }
                                 }

                                 result = allowed ? ctx.data.blade.setCombo(ctx, validated) : Result.fail("UNSUPPORTED");
                              } else {
                                 String id = validated.get("id").getAsString();
                                 ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
                                 if (def != null && EditorWeapons.adapter(ctx, def.adapterId)) {
                                    ctx.config.putAction(id, validated.getAsJsonObject("config"));
                                    result = Result.success();
                                 } else {
                                    result = Result.fail("UNSUPPORTED");
                                 }
                              }
                           }

                           respond(current, active, request.requestId, ctx, result);
                        }
                     } else {
                        denied(current, request, "PERMISSION_DENIED");
                     }
                  }
               });
            });
         } catch (RejectedExecutionException var9) {
            session.pending = 0;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
               denied(player, request, "BUSY");
            }
         }
      }
   }

   private static Result apply(ServerPlayer player, CombatContext ctx, GunNetwork.Operation operation, JsonObject input) {
      String id = string(input, "id", "");

      return switch (operation) {
         case OPEN, REFRESH -> Result.success();
         case SET -> {
            String key = string(input, "key", "");
            yield inventoryConfig(key) && !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY)
               ? Result.fail("PERMISSION_DENIED")
               : (
                  key.equals("slashblade.preset")
                     ? Presets.apply(ctx, ConfigSchema.get(key).validate(input.get("value")).getAsString())
                     : CombatService.setConfig(ctx, key, input.get("value"))
               );
         }
         case BATCH -> {
            JsonObject values = object(input, "values", true);
            yield values.keySet().stream().anyMatch(GunNetwork::inventoryConfig)
                  && !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY)
               ? Result.fail("PERMISSION_DENIED")
               : CombatService.setConfigs(ctx, values);
         }
         case FETCH_GRAPH, SELECT_GRAPH -> ctx.config.selectGraph(id);
         case DELETE_GRAPH -> ctx.data.blade.comboState(ctx).get("running").getAsBoolean() ? Result.fail("BUSY") : ctx.config.removeGraph(id);
         case SWITCH -> ctx.data.armory.switchTo(ctx, integer(input, "slot", 0, 5));
         case EXCHANGE -> Equipment.exchange(ctx, player, integer(input, "slot", 0, 8), integer(input, "inventorySlot", 0, 40));
         case UNEQUIP -> Equipment.unequip(ctx, player, integer(input, "slot", 0, 8));
         case PRIMARY_WEAPON -> {
            int slot = integer(input, "slot", 0, 5);
            if (ctx.data.armory.get(ctx.npc, slot).isEmpty()) {
               yield Result.fail("NO_WEAPON");
            } else {
               ctx.config.set("weapon.primarySlot", new JsonPrimitive(slot));
               yield Result.success();
            }
         }
         case EQUIP_CREATIVE -> player.isCreative()
            ? Equipment.creative(
               ctx, player, integer(input, "slot", 0, 8), CreativeCatalogue.resolve(player, input), input.has("count") ? integer(input, "count", 1, 64) : 1
            )
            : Result.fail("PERMISSION_DENIED");
         case SAVE_ITEM_PRESET -> {
            if (!player.isCreative()) {
               yield Result.fail("PERMISSION_DENIED");
            } else {
               ItemStack item;
               if (input.has("npcSlot")) {
                  item = Equipment.get(ctx, integer(input, "npcSlot", 0, 8)).copy();
               } else if (input.has("inventorySlot")) {
                  int index = integer(input, "inventorySlot", 0, 40);
                  if (index > 35 && index != 40) {
                     yield Result.fail("INVALID_ARGUMENT");
                  }

                  item = player.getInventory().getItem(index).copy();
               } else {
                  item = CreativeCatalogue.resolve(player, input);
               }

               yield ItemPresetLibrary.get(player).create(player, string(input, "name", ""), item);
            }
         }
         case EQUIP_ITEM_PRESET -> {
            if (!player.isCreative()) {
               yield Result.fail("PERMISSION_DENIED");
            } else {
               ItemStack item = ItemPresetLibrary.get(player).find(player.getUUID(), id);
               yield item.isEmpty()
                  ? Result.fail("NOT_FOUND")
                  : Equipment.creative(ctx, player, integer(input, "slot", 0, 8), item, input.has("count") ? integer(input, "count", 1, 64) : 1);
            }
         }
         case DELETE_ITEM_PRESET -> ItemPresetLibrary.get(player).remove(player.getUUID(), id);
         case SUPPLY -> {
            if (ctx.runtime.adapter instanceof GunAdapter gun && ctx.runtime.gunCaps != null && ctx.runtime.gunCaps.hasReserveAmmo) {
               yield ctx.data
                  .ammo
                  .supply(
                     ctx,
                     gun,
                     integer(input, "count", 0, 100000),
                     input.has("replace") && Parameter.bool("replace", false).validate(input.get("replace")).getAsBoolean(),
                     false
                  );
            }

            yield Result.fail("UNSUPPORTED");
         }
         case RELOAD -> ctx.data.gun.reload(ctx);
         case PRESET -> Presets.apply(ctx, id);
         case COPY -> Presets.copy(ctx, string(input, "source", ""), id);
         case SAVE_PRESET -> PresetLibrary.get(ctx).saveAs(id, ctx.config.snapshot());
         case DELETE_PRESET -> Presets.remove(ctx, id);
         case START -> ctx.data.blade.startCombo(ctx, id);
         case STOP -> ctx.data.blade.stopCombo(ctx, "editor");
         case EXECUTE_ACTION -> ctx.data.blade.execute(ctx, id, object(input, "parameters", false));
         case FIRE -> ctx.data.gun.fire(ctx);
         case COMMAND -> CombatService.command(ctx, id);
         default -> Result.fail("INVALID_ARGUMENT");
      };
   }

   private static boolean inventoryConfig(String key) {
      return key.startsWith("ammo.") || key.startsWith("throw.") || key.equals("weapon.primarySlot");
   }

   private static int integer(JsonObject input, String key, int min, int max) {
      return Parameter.integer(key, min, min, max).validate(input.get(key)).getAsInt();
   }

   private static String string(JsonObject input, String key, String fallback) {
      return !input.has(key) ? fallback : new Parameter(key, "string", fallback, null, null, null, "").validate(input.get(key)).getAsString();
   }

   private static JsonObject object(JsonObject input, String key, boolean required) {
      JsonElement value = input.get(key);
      if (value == null && !required) {
         return new JsonObject();
      } else if (value != null && value.isJsonObject()) {
         return value.getAsJsonObject();
      } else {
         throw new IllegalArgumentException(key);
      }
   }

   private static void respond(ServerPlayer player, GunNetwork.Session session, int request, CombatContext ctx, Result result) {
      CombatService.refresh(ctx);
      JsonObject state = EditorSnapshot.create(ctx);
      state.addProperty("inventoryAllowed", CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY));
      state.addProperty("creativeAllowed", player.isCreative() && CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_INVENTORY));
      state.add("itemPresets", ItemPresetLibrary.get(player).snapshot(player.getUUID()));
      state.add("resultData", SafeJson.GSON.toJsonTree(result.data));
      String json = SafeJson.GSON.toJson(state);
      if (json.length() > 524288) {
         denied(
            player, new GunNetwork.Request(session.npcId, session.token, ctx.config.revision, request, GunNetwork.Operation.REFRESH, "{}"), "INTERNAL_ERROR"
         );
      } else {
         ItemStack[] slots = new ItemStack[9];

         for (int i = 0; i < 9; i++) {
            slots[i] = Equipment.get(ctx, i).copy();
         }

         PacketDistributor.sendToPlayer(
            player,
            new GunNetwork.Response(
               session.npcId,
               session.token,
               ctx.config.revision,
               request,
               result.code,
               result.message.length() > 512 ? result.message.substring(0, 512) : result.message,
               json,
               slots
            )
         );
         ctx.npc.updateClient = true;
      }
   }

   private static void denied(ServerPlayer player, GunNetwork.Request request, String code) {
      ItemStack[] empty = Equipment.empty();
      PacketDistributor.sendToPlayer(
         player,
         new GunNetwork.Response(
            request.npcId, request.token, request.revision, request.requestId, code, "cnpcgun.error." + code.toLowerCase(Locale.ROOT), "{}", empty
         )
      );
   }

   public static void animation(CombatContext ctx, String pose, int duration) {
      animation(ctx, pose, duration, ctx.runtime.actionId);
   }

   public static void animation(CombatContext ctx, String pose, int duration, String actionId) {
      if (CombatService.serverThread(ctx.npc)) {
         int ticks = Math.max(1, Math.min(2400, duration));
         if (ctx.runtime.domain.equals("GUN") && (pose.equals("FIRE") || pose.equals("MELEE"))) {
            ctx.runtime.gunAnimationUntil = Math.max(ctx.runtime.gunAnimationUntil, ctx.now + ticks);
         }

         PacketDistributor.sendToPlayersTrackingEntity(
            ctx.npc,
            new GunNetwork.Animation(ctx.npc.getId(), ctx.npc.getUUID(), ctx.runtime.domain, pose, actionId, ticks, ctx.runtime.aiming)
         );
      }
   }

   public static String statePose(CombatContext ctx) {
      if (ctx.runtime.domain.equals("GUN") && ctx.config.bool("gun.disableIdleAnimation")) {
         boolean action = ctx.runtime.reloading
            || ctx.now < ctx.runtime.gunAnimationUntil
            || ctx.runtime.scheduler.busy(ctx.now) && Set.of("GUN", "RELOAD", "THROW").contains(ctx.runtime.scheduler.channel);
         boolean active = !ctx.runtime.editorPaused && ctx.config.bool("combat.enabled") && ctx.config.bool("gun.enabled") && (ctx.validTarget() || action);
         return active ? "STATE" : "STATE_IDLE";
      } else {
         return "STATE";
      }
   }

   public static void summary(CombatContext ctx) {
      if (CombatService.serverThread(ctx.npc)) {
         String pose = statePose(ctx);
         String key = ctx.runtime.domain + ":" + ctx.runtime.aiming + ":" + ctx.runtime.combatState + ":" + pose;
         if (ctx.now >= ctx.runtime.nextAnimationSummary || !key.equals(ctx.runtime.animationSummary)) {
            ctx.runtime.animationSummary = key;
            ctx.runtime.nextAnimationSummary = ctx.now + 20L;
            animation(ctx, pose, 25);
         }
      }
   }

   public record Animation(int entityId, UUID npcId, String domain, String pose, String actionId, int duration, boolean aiming) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Animation> TYPE = new CustomPacketPayload.Type<>(
         ResourceLocation.fromNamespaceAndPath("cnpcgun", "animation")
      );
      public static final StreamCodec<FriendlyByteBuf, Animation> STREAM_CODEC = StreamCodec.of(
         (buf, value) -> value.encode(buf), Animation::decode
      );

      @Override
      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      public static GunNetwork.Animation decode(FriendlyByteBuf buf) {
         return new GunNetwork.Animation(
            buf.readVarInt(), buf.readUUID(), buf.readUtf(24), buf.readUtf(32), buf.readUtf(192), buf.readVarInt(), buf.readBoolean()
         );
      }

      public void encode(FriendlyByteBuf buf) {
         buf.writeVarInt(this.entityId);
         buf.writeUUID(this.npcId);
         buf.writeUtf(this.domain, 24);
         buf.writeUtf(this.pose, 32);
         buf.writeUtf(this.actionId, 192);
         buf.writeVarInt(this.duration);
         buf.writeBoolean(this.aiming);
      }
   }

   public static enum Operation {
      OPEN,
      CLOSE,
      REFRESH,
      SET,
      BATCH,
      ACTION_CONFIG,
      GRAPH,
      FETCH_GRAPH,
      DELETE_GRAPH,
      SELECT_GRAPH,
      SWITCH,
      EXCHANGE,
      SUPPLY,
      RELOAD,
      PRESET,
      COPY,
      SAVE_PRESET,
      START,
      STOP,
      EXECUTE_ACTION,
      FIRE,
      COMMAND,
      PRIMARY_WEAPON,
      EQUIP_CREATIVE,
      SAVE_ITEM_PRESET,
      EQUIP_ITEM_PRESET,
      DELETE_ITEM_PRESET,
      DELETE_PRESET,
      HEARTBEAT,
      UNEQUIP;
   }

   private static final class Rate {
      long window;
      int count;
   }

   public record Request(UUID npcId, UUID token, long revision, int requestId, GunNetwork.Operation operation, String json) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Request> TYPE = new CustomPacketPayload.Type<>(
         ResourceLocation.fromNamespaceAndPath("cnpcgun", "request")
      );
      public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
         (buf, value) -> value.encode(buf), Request::decode
      );

      @Override
      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      public static GunNetwork.Request decode(FriendlyByteBuf buf) {
         return new GunNetwork.Request(
            buf.readUUID(),
            buf.readUUID(),
            buf.readLong(),
            buf.readVarInt(),
            (GunNetwork.Operation)buf.readEnum(GunNetwork.Operation.class),
            buf.readUtf(65536)
         );
      }

      public void encode(FriendlyByteBuf buf) {
         buf.writeUUID(this.npcId);
         buf.writeUUID(this.token);
         buf.writeLong(this.revision);
         buf.writeVarInt(this.requestId);
         buf.writeEnum(this.operation);
         buf.writeUtf(this.json, 65536);
      }
   }

   public record Response(UUID npcId, UUID token, long revision, int requestId, String code, String message, String json, ItemStack[] slots) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Response> TYPE = new CustomPacketPayload.Type<>(
         ResourceLocation.fromNamespaceAndPath("cnpcgun", "response")
      );
      public static final StreamCodec<RegistryFriendlyByteBuf, Response> STREAM_CODEC = StreamCodec.of(
         (buf, value) -> value.encode(buf), Response::decode
      );

      @Override
      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }

      public static GunNetwork.Response decode(RegistryFriendlyByteBuf buf) {
         UUID npc = buf.readUUID();
         UUID token = buf.readUUID();
         long revision = buf.readLong();
         int request = buf.readVarInt();
         String code = buf.readUtf(64);
         String message = buf.readUtf(512);
         String json = buf.readUtf(524288);
         ItemStack[] slots = new ItemStack[9];

         for (int i = 0; i < 9; i++) {
            slots[i] = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
         }

         return new GunNetwork.Response(npc, token, revision, request, code, message, json, slots);
      }

      public void encode(RegistryFriendlyByteBuf buf) {
         buf.writeUUID(this.npcId);
         buf.writeUUID(this.token);
         buf.writeLong(this.revision);
         buf.writeVarInt(this.requestId);
         buf.writeUtf(this.code, 64);
         buf.writeUtf(this.message, 512);
         buf.writeUtf(this.json, 524288);

         for (ItemStack slot : this.slots) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, slot == null ? ItemStack.EMPTY : slot);
         }
      }
   }

   private static final class Session {
      final UUID npcId;
      final UUID token = UUID.randomUUID();
      int lastRequest;
      int pending;
      long expires;

      Session(UUID npcId, long now) {
         this.npcId = npcId;
         this.expires = now + 1200L;
      }
   }
}
