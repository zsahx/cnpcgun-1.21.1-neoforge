package bincnpc.gun.slashblade.controller;

import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatMovement;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.combat.Scheduler;
import bincnpc.gun.common.network.GunNetwork;
import bincnpc.gun.common.script.EventBridge;
import bincnpc.gun.slashblade.action.ActionConfig;
import bincnpc.gun.slashblade.action.ActionRegistry;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import bincnpc.gun.slashblade.combo.ComboGraph;
import bincnpc.gun.slashblade.combo.Conditions;
import bincnpc.gun.slashblade.combo.GraphValidator;
import bincnpc.gun.slashblade.strategy.BladeStrategy;
import bincnpc.gun.slashblade.strategy.MaidStrategy;
import bincnpc.gun.slashblade.strategy.NormalStrategy;
import bincnpc.gun.slashblade.strategy.Presets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.entity.EntityNPCInterface;

public final class BladeController {
   private final BladeStrategy beginner = new NormalStrategy(true);
   private final BladeStrategy normal = new NormalStrategy(false);
   private final BladeStrategy maid = new MaidStrategy();
   private final Map<String, Long> finishing = new LinkedHashMap<>();
   private ComboGraph graph;
   private ComboGraph.Node node;
   private long graphStart;
   private long nodeStart;
   private long nodeWaitUntil;
   private long nextDecision;
   private boolean nodeExecuted;

   public void reset() {
      this.graph = null;
      this.node = null;
      this.finishing.clear();
      this.nextDecision = 0L;
   }

   public JsonObject state(CombatContext ctx) {
      return ctx.runtime.adapter instanceof BladeAdapter adapter
         ? AdapterRegistry.query(adapter, () -> adapter.state(ctx), new JsonObject())
         : new JsonObject();
   }

   public Result suspendForThrow(CombatContext ctx) {
      if (CombatService.serverThread(ctx.npc) && CombatService.validNpc(ctx.npc)) {
         if (ctx.runtime.adapter instanceof BladeAdapter adapter) {
            Scheduler scheduler = ctx.runtime.scheduler;
            if (scheduler.busy(ctx.now) && !scheduler.channel.equals("BLADE")) {
               return Result.fail("BUSY");
            } else if (scheduler.busy(ctx.now) && !scheduler.interruptible) {
               return Result.fail("BUSY");
            } else {
               ActionRegistry.Definition current = ActionRegistry.INSTANCE.get(scheduler.action);
               if (scheduler.busy(ctx.now) && current != null && current.info.type.equals("SPECIAL_ATTACK")) {
                  return Result.fail("BUSY");
               } else {
                  Result stopped = AdapterRegistry.call(adapter, () -> adapter.interruptForThrow(ctx));
                  if (!stopped.ok) {
                     return stopped;
                  } else {
                     if (this.graph != null) {
                        EventBridge.emit(ctx, "slashblade.combo_interrupted", ctx.runtime.actionId, Result.success("throw"));
                     }

                     this.graph = null;
                     this.node = null;
                     ctx.runtime.comboId = "";
                     ctx.runtime.comboNode = "";
                     ctx.runtime.comboTarget = null;
                     ctx.runtime.interrupted = "throw";
                     if (!ctx.runtime.actionId.isEmpty() && this.finishing.remove(ctx.runtime.actionId) != null) {
                        EventBridge.emit(ctx, "slashblade.action_finished", ctx.runtime.actionId, Result.success());
                     }

                     ctx.runtime.actionId = "";
                     ctx.runtime.actionEnd = ctx.now;
                     this.nextDecision = ctx.now + 1L;
                     if (scheduler.channel.equals("BLADE")) {
                        scheduler.release();
                     }

                     return Result.success();
                  }
               }
            }
         } else {
            return Result.fail("NO_COMPATIBLE_WEAPON");
         }
      } else {
         return Result.fail("INVALID_NPC");
      }
   }

   public boolean ready(CombatContext ctx, String id) {
      ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
      return def != null && def.info.available && ctx.runtime.cooldowns.getOrDefault(id, 0L) <= ctx.now;
   }

   public Result execute(CombatContext ctx, String id, JsonObject input) {
      if (ctx != null && id != null) {
         if (input == null) {
            input = new JsonObject();
         }

         if (!CombatService.serverThread(ctx.npc) || !CombatService.validNpc(ctx.npc) || ctx.runtime.editorPaused) {
            return Result.fail("PERMISSION_DENIED");
         } else if (ctx.data.throwing.blocksAttack(ctx.now)) {
            return Result.fail("BUSY");
         } else if (!(ctx.runtime.adapter instanceof BladeAdapter adapter)) {
            return Result.fail("NO_COMPATIBLE_WEAPON");
         } else if (ctx.config.bool("combat.enabled") && ctx.config.bool("slashblade.enabled")) {
            ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
            if (def == null) {
               return Result.fail("NOT_FOUND");
            } else if (!def.adapterId.equals(adapter.id()) || !def.info.available) {
               return Result.fail("UNSUPPORTED");
            } else if (def.info.targetRequired && !ctx.validTarget()) {
               return Result.fail("INVALID_TARGET");
            } else if (!this.ready(ctx, id)) {
               return Result.fail("COOLDOWN");
            } else {
               JsonObject parameters;
               JsonObject options;
               try {
                  JsonObject action = Presets.action(ctx, def);
                  JsonObject merged = action.has("parameters") ? action.getAsJsonObject("parameters").deepCopy() : new JsonObject();
                  input.entrySet().forEach(entry -> merged.add((String)entry.getKey(), ((JsonElement)entry.getValue()).deepCopy()));
                  parameters = GraphValidator.parameters(merged, def.info.parameters);
                  options = ActionConfig.validate(action, def);
               } catch (IllegalStateException | IllegalArgumentException var17) {
                  return Result.fail("INVALID_ARGUMENT", var17.getMessage());
               }

               if (!ctx.validTarget()
                  || !(ctx.distance < options.get("minDistance").getAsDouble())
                     && !(ctx.distance > Math.min(options.get("maxDistance").getAsDouble(), ctx.config.number("combat.attackRange")))) {
                  if (!Conditions.all(options.getAsJsonArray("conditions"), ctx)) {
                     return Result.fail("CONDITION_NOT_MET");
                  } else {
                     if (ctx.validTarget()) {
                        String targetType = options.get("targetType").getAsString();
                        if (targetType.equals("PLAYER") && !(ctx.target instanceof Player)
                           || targetType.equals("NPC") && !(ctx.target instanceof EntityNPCInterface)
                           || targetType.equals("MOB") && !(ctx.target instanceof Mob)) {
                           return Result.fail("CONDITION_NOT_MET");
                        }

                        if (options.get("targetCount").getAsInt() > 1 && ctx.targets().size() < options.get("targetCount").getAsInt()) {
                           return Result.fail("CONDITION_NOT_MET");
                        }
                     }

                     JsonArray stages = options.getAsJsonArray("stages");
                     if (!stages.isEmpty()) {
                        String phase = SafeJson.string(this.state(ctx), "combo", "");
                        boolean match = false;

                        for (JsonElement stage : stages) {
                           if (stage.getAsString().equals(phase)) {
                              match = true;
                           }
                        }

                        if (!match) {
                           return Result.fail("CONDITION_NOT_MET");
                        }
                     }

                     boolean highest = ctx.config.text("slashblade.preset").equals("MAID");
                     if (ctx.runtime.scheduler.busy(ctx.now)
                        && ctx.runtime.scheduler.channel.equals("BLADE")
                        && highest
                        && !def.info.parallel
                        && options.get("interruptNormal").getAsBoolean()
                        && AdapterRegistry.query(adapter, () -> adapter.canChain(ctx, def, parameters), false)) {
                        ctx.runtime.scheduler.release();
                     } else if (ctx.runtime.scheduler.busy(ctx.now)
                        && ctx.runtime.scheduler.channel.equals("BLADE")
                        && ctx.runtime.scheduler.interruptible
                        && !def.info.parallel
                        && options.get("interruptNormal").getAsBoolean()) {
                        JsonObject phase = this.state(ctx);
                        if (phase.has("canInterrupt")
                           && !phase.get("canInterrupt").getAsBoolean()
                           && (!phase.has("perfectCharge") || !phase.get("perfectCharge").getAsBoolean())) {
                           return Result.fail("BUSY");
                        }

                        ActionRegistry.Definition current = ActionRegistry.INSTANCE.get(ctx.runtime.scheduler.action);
                        if (current != null && current.info.type.equals("SPECIAL_ATTACK")) {
                           return Result.fail("BUSY");
                        }

                        ctx.runtime.scheduler.release();
                     }

                     Result lock = ctx.runtime.scheduler.acquire("BLADE", id, ctx.now, def.info.duration, def.info.interruptible, def.info.parallel);
                     if (!lock.ok) {
                        return lock;
                     } else {
                        Result result = AdapterRegistry.call(adapter, () -> adapter.execute(ctx, def, parameters));
                        ctx.runtime.lastResult = result;
                        if (!result.ok) {
                           if (!def.info.parallel) {
                              ctx.runtime.scheduler.release();
                           } else {
                              ctx.runtime.scheduler.releaseAuxiliary(id);
                           }

                           EventBridge.emit(ctx, "slashblade.action_failed", id, result);
                           return result;
                        } else {
                           if (highest && Set.of("combo:slashblade:combo_a1", "combo:slashblade:combo_b1", "combo:slashblade:combo_c").contains(def.backend)) {
                              ctx.runtime.maid.comboCycle = (ctx.runtime.maid.comboCycle + 1) % 3;
                           }

                           int duration = result.data instanceof BladeAdapter.Execution execution ? execution.duration() : def.info.duration;
                           if (def.backend.equals("guard") && parameters.has("duration")) {
                              duration = parameters.get("duration").getAsInt();
                           }

                           duration = Math.max(1, Math.min(2400, duration));
                           if (!def.info.parallel) {
                              ctx.runtime.scheduler.until = ctx.now + duration;
                           }

                           int delay = Math.max(highest ? 4 : duration, (int)Math.ceil(def.info.cooldown * options.get("cooldownMultiplier").getAsDouble()));
                           ctx.runtime.cooldowns.put(id, ctx.now + delay);
                           if (!def.info.parallel && !ctx.runtime.actionId.isEmpty() && this.finishing.remove(ctx.runtime.actionId) != null) {
                              EventBridge.emit(ctx, "slashblade.action_finished", ctx.runtime.actionId, Result.success());
                           }

                           this.finishing.put(id, ctx.now + duration);
                           if (!def.info.parallel) {
                              ctx.runtime.actionId = id;
                              ctx.runtime.actionEnd = ctx.now + duration;
                              ctx.runtime.lastBladeAction = id;
                              CombatService.setState(ctx, "EXECUTING_ACTION");
                              if (highest && def.info.type.equals("SPECIAL_ATTACK") && !def.tags.contains("super")) {
                                 ctx.runtime.maid.specialAction = id;
                                 ctx.runtime.maid.lastSpecial = ctx.now;
                                 if (++ctx.runtime.maid.specialBurst >= 5) {
                                    ctx.runtime.maid.specialCooldown = ctx.now + 120L;
                                    ctx.runtime.maid.specialCursor++;
                                 }
                              } else if (highest && def.tags.contains("super")) {
                                 ctx.runtime.maid.superCooldown = ctx.now + 400L;
                              } else if (highest && (def.info.type.equals("NORMAL_ATTACK") || def.info.type.equals("MOVEMENT"))) {
                                 ctx.runtime.maid.specialBurst = 0;
                              }
                           }

                           EventBridge.emit(ctx, "slashblade.action_started", id, result);
                           if (def.info.type.equals("SPECIAL_ATTACK")) {
                              EventBridge.emit(ctx, "slashblade.special_attack_used", id, result);
                           }

                           if (def.info.type.equals("PHANTOM_SWORD")) {
                              EventBridge.emit(ctx, "slashblade.phantom_action_used", id, result);
                           }

                           String animation = result.data instanceof BladeAdapter.Execution executionx ? "combo:" + executionx.stage() : def.info.id;
                           GunNetwork.animation(ctx, def.info.type, duration, animation);
                           ctx.npc.updateClient = true;
                           if (def.info.type.equals("SPECIAL_ATTACK") && options.get("linkPhantom").getAsBoolean()) {
                              String phantom = this.selectEnabled(ctx, "PHANTOM_SWORD");
                              if (!phantom.isEmpty()) {
                                 this.execute(ctx, phantom, new JsonObject());
                              }
                           }

                           return result;
                        }
                     }
                  }
               } else {
                  return Result.fail("OUT_OF_RANGE");
               }
            }
         } else {
            return Result.fail("PERMISSION_DENIED");
         }
      } else {
         return Result.fail("INVALID_ARGUMENT");
      }
   }

   public void tick(CombatContext ctx) {
      Iterator<Entry<String, Long>> iterator = this.finishing.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<String, Long> entry = iterator.next();
         if (ctx.now >= entry.getValue()) {
            EventBridge.emit(ctx, "slashblade.action_finished", entry.getKey(), Result.success());
            if (ctx.runtime.actionId.equals(entry.getKey())) {
               ctx.runtime.actionId = "";
            }

            iterator.remove();
         }
      }

      if (ctx.runtime.command.equals("AUTO") && !ctx.data.throwing.blocksAttack(ctx.now)) {
         if (this.graph != null) {
            this.tickGraph(ctx);
         } else if (ctx.runtime.adapter instanceof BladeAdapter
            && ctx.config.bool("slashblade.enabled")
            && ctx.config.bool("slashblade.autoAttack")
            && ctx.validTarget()) {
            if (ctx.now >= this.nextDecision) {
               if (!(ctx.distance > ctx.config.number("combat.attackRange"))) {
                  String preset = ctx.config.text("slashblade.preset");
                  if (preset.equals("CUSTOM")) {
                     String selected = ctx.config.selectedGraph();
                     if (!selected.isEmpty()) {
                        this.startCombo(ctx, selected);
                     }
                  } else {
                     BladeStrategy strategy = switch (preset) {
                        case "NORMAL" -> this.normal;
                        case "MAID" -> this.maid;
                        default -> this.beginner;
                     };
                     if (!ctx.runtime.scheduler.busy(ctx.now) || preset.equals("MAID")) {
                        BladeStrategy.Decision decision = strategy.decide(ctx);
                        this.nextDecision = ctx.now + (preset.equals("MAID") ? 4 : ctx.config.integer("slashblade.attackInterval"));
                        if (decision != null) {
                           Result result = this.execute(ctx, decision.action(), decision.parameters());
                           if (result.ok) {
                              this.nextDecision = ctx.now + decision.delay();
                           }

                           if (ctx.distance > CombatMovement.meleeReach(ctx)) {
                              ctx.npc.getNavigation().moveTo(ctx.target, 1.0);
                           } else {
                              ctx.npc.getNavigation().stop();
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public String selectEnabled(CombatContext ctx, String type) {
      ActionRegistry.Definition best = null;
      int priority = -1;

      for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) {
         if (ctx.runtime.adapter != null
            && def.adapterId.equals(ctx.runtime.adapter.id())
            && this.ready(ctx, def.info.id)
            && (type == null || def.info.type.equals(type))
            && (type != null || Set.of("SPECIAL_ATTACK", "PHANTOM_SWORD").contains(def.info.type))
            && (!def.info.type.equals("SPECIAL_ATTACK") || ctx.config.bool("slashblade.useSpecialAttacks"))
            && (!def.info.type.equals("PHANTOM_SWORD") || ctx.config.bool("slashblade.usePhantoms"))) {
            JsonObject raw = Presets.action(ctx, def);
            if (ActionConfig.value(raw, "enabled").getAsBoolean()
               && ActionConfig.value(raw, "auto").getAsBoolean()
               && (ActionConfig.value(raw, "consecutive").getAsBoolean() || !ctx.runtime.lastBladeAction.equals(def.info.id))) {
               JsonObject options;
               try {
                  options = ActionConfig.validate(raw, def);
               } catch (IllegalStateException | IllegalArgumentException var12) {
                  continue;
               }

               if (!(ctx.distance < options.get("minDistance").getAsDouble())
                  && !(ctx.distance > options.get("maxDistance").getAsDouble())
                  && Conditions.all(options.getAsJsonArray("conditions"), ctx)
                  && this.eligible(ctx, def.info.id)) {
                  int rank = options.get("priority").getAsInt();
                  if (rank > priority) {
                     best = def;
                     priority = rank;
                  }
               }
            }
         }
      }

      return best == null ? "" : best.info.id;
   }

   public boolean autoEnabled(CombatContext ctx, String id) {
      ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
      if (def == null || ctx.runtime.adapter == null || !def.adapterId.equals(ctx.runtime.adapter.id()) || !def.info.available) {
         return false;
      } else if ((!def.info.type.equals("SPECIAL_ATTACK") || ctx.config.bool("slashblade.useSpecialAttacks"))
         && (!def.info.type.equals("PHANTOM_SWORD") || ctx.config.bool("slashblade.usePhantoms"))
         && (!Set.of("COUNTER", "DEFENSE").contains(def.info.type) || ctx.config.bool("slashblade.counter"))) {
         JsonObject options = Presets.action(ctx, def);
         return ActionConfig.value(options, "enabled").getAsBoolean() && ActionConfig.value(options, "auto").getAsBoolean();
      } else {
         return false;
      }
   }

   public boolean eligible(CombatContext ctx, String id) {
      ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(id);
      if (this.autoEnabled(ctx, id) && this.ready(ctx, id) && (!def.info.targetRequired || ctx.validTarget())) {
         JsonObject raw = Presets.action(ctx, def);

         try {
            JsonObject options = ActionConfig.validate(raw, def);
            JsonObject phase = this.state(ctx);
            if ((!phase.has("broken") || !phase.get("broken").getAsBoolean()) && (!phase.has("sealed") || !phase.get("sealed").getAsBoolean())) {
               int cost = (int)Math.ceil((def.info.resourceCost == null ? 0 : def.info.resourceCost) * options.get("resourceMultiplier").getAsDouble());
               int reserve = Math.max(ctx.config.integer("slashblade.resourceReserve"), options.get("resourceReserve").getAsInt());
               if (!ctx.config.bool("slashblade.ignoreRequirements") && phase.has("resource") && phase.get("resource").getAsInt() < cost + reserve) {
                  return false;
               } else if (!options.get("consecutive").getAsBoolean() && !def.info.parallel && ctx.runtime.lastBladeAction.equals(id)) {
                  return false;
               } else {
                  JsonArray stages = options.getAsJsonArray("stages");
                  if (!stages.isEmpty() && !stages.asList().contains(new JsonPrimitive(SafeJson.string(phase, "combo", "")))) {
                     return false;
                  } else {
                     if (ctx.validTarget()) {
                        String kind = options.get("targetType").getAsString();
                        if (kind.equals("PLAYER") && !(ctx.target instanceof Player)
                           || kind.equals("NPC") && !(ctx.target instanceof EntityNPCInterface)
                           || kind.equals("MOB") && !(ctx.target instanceof Mob)) {
                           return false;
                        }

                        if (options.get("targetCount").getAsInt() > 1 && ctx.targets().size() < options.get("targetCount").getAsInt()) {
                           return false;
                        }
                     }

                     return options.get("enabled").getAsBoolean()
                        && options.get("auto").getAsBoolean()
                        && (
                           !ctx.validTarget()
                              || ctx.distance >= options.get("minDistance").getAsDouble()
                                 && ctx.distance <= Math.min(ctx.config.number("combat.attackRange"), options.get("maxDistance").getAsDouble())
                        )
                        && Conditions.all(options.getAsJsonArray("conditions"), ctx);
                  }
               }
            } else {
               return false;
            }
         } catch (IllegalStateException | IllegalArgumentException var11) {
            return false;
         }
      } else {
         return false;
      }
   }

   public Result setCombo(CombatContext ctx, JsonObject input) {
      try {
         ComboGraph checked = GraphValidator.validate(input, ActionRegistry.INSTANCE.snapshot(), false);
         if (ctx.config.graphs().size() >= 32 && ctx.config.graph(checked.id) == null) {
            return Result.fail("CONFLICT");
         } else {
            ctx.config.putGraph(checked.id, checked.original);
            return Result.success();
         }
      } catch (IllegalStateException | IllegalArgumentException var4) {
         return Result.fail("INVALID_ARGUMENT", var4.getMessage());
      }
   }

   public Result startCombo(CombatContext ctx, String id) {
      if (ctx.data.throwing.blocksAttack(ctx.now)) {
         return Result.fail("BUSY");
      } else if (!(ctx.runtime.adapter instanceof BladeAdapter)) {
         return Result.fail("NO_COMPATIBLE_WEAPON");
      } else if (this.graph == null && !ctx.runtime.scheduler.busy(ctx.now)) {
         JsonObject source = ctx.config.graph(id);
         if (source == null) {
            return Result.fail("NOT_FOUND");
         } else {
            try {
               for (JsonElement raw : source.getAsJsonArray("nodes")) {
                  JsonObject candidate = raw.getAsJsonObject();
                  ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(candidate.get("action").getAsString());
                  if (def != null) {
                     String jump = ActionConfig.value(Presets.action(ctx, def), "successNode").getAsString();
                     if (!jump.isEmpty()) {
                        candidate.addProperty("onSuccess", jump);
                     }
                  }
               }

               this.graph = GraphValidator.validate(source, ActionRegistry.INSTANCE.snapshot(), true);
            } catch (IllegalStateException | IllegalArgumentException var9) {
               return Result.fail("INVALID_ARGUMENT", var9.getMessage());
            }

            this.graphStart = ctx.now;
            ctx.runtime.comboId = id;
            ctx.runtime.comboTarget = ctx.runtime.targetId;
            this.enter(ctx, this.graph.entryNode);
            EventBridge.emit(ctx, "slashblade.combo_started", "", Result.success(id));
            return Result.success();
         }
      } else {
         return Result.fail("BUSY");
      }
   }

   private void enter(CombatContext ctx, String id) {
      if (id.equals("end")) {
         EventBridge.emit(ctx, "slashblade.combo_finished", "", Result.success());
         this.graph = null;
         this.node = null;
         ctx.runtime.comboId = "";
         ctx.runtime.comboNode = "";
      } else {
         this.node = this.graph.nodes.get(id);
         this.nodeStart = ctx.now;
         this.nodeExecuted = false;
         ctx.runtime.comboNode = id;
         EventBridge.emit(ctx, "slashblade.combo_node", this.node.action, Result.success(id));
      }
   }

   private void tickGraph(CombatContext ctx) {
      if ((ctx.validTarget() || !ctx.config.bool("combat.stopOnInvalidTarget"))
         && (!ctx.config.text("slashblade.targetChange").equals("INTERRUPT") || Objects.equals(ctx.runtime.comboTarget, ctx.runtime.targetId))
         && !Conditions.any(this.node.interrupts, ctx)
         && ctx.now - this.graphStart <= ctx.config.integer("slashblade.combo.maxTicks")) {
         if (ctx.now - this.nodeStart >= this.node.timeout) {
            this.enter(ctx, this.node.onTimeout);
         } else if (this.nodeExecuted) {
            if (ctx.now >= this.nodeWaitUntil) {
               this.enter(ctx, this.node.onSuccess);
            }
         } else if (!Conditions.all(this.node.conditions, ctx)) {
            this.enter(ctx, this.node.onFailure);
         } else if (ActionRegistry.INSTANCE.get(this.node.action) == null) {
            if (ctx.config.text("slashblade.missingAction").equals("STOP")) {
               this.stopCombo(ctx, "missing_action");
            } else {
               this.enter(ctx, this.node.onFailure);
            }
         } else {
            Result result = this.execute(ctx, this.node.action, this.node.parameters);
            if (result.ok) {
               this.nodeExecuted = true;
               this.nodeWaitUntil = result.data instanceof BladeAdapter.Execution execution
                  ? ctx.now + execution.duration()
                  : ctx.now + ActionRegistry.INSTANCE.get(this.node.action).info.duration;
            } else if (!Set.of("BUSY", "COOLDOWN").contains(result.code)) {
               ActionRegistry.Definition def = ActionRegistry.INSTANCE.get(this.node.action);
               if (def != null && ActionConfig.value(Presets.action(ctx, def), "failure").getAsString().equals("STOP")) {
                  this.stopCombo(ctx, result.code);
               } else {
                  this.enter(ctx, this.node.onFailure);
               }
            }
         }
      } else {
         this.stopCombo(ctx, "condition");
      }
   }

   public Result stopCombo(CombatContext ctx, String reason) {
      if (ctx.runtime.scheduler.busy(ctx.now) && !ctx.runtime.scheduler.interruptible) {
         return Result.fail("BUSY");
      } else {
         if (this.graph != null) {
            EventBridge.emit(ctx, "slashblade.combo_interrupted", ctx.runtime.actionId, Result.success(reason));
         }

         this.graph = null;
         this.node = null;
         ctx.runtime.comboId = "";
         ctx.runtime.comboNode = "";
         ctx.runtime.interrupted = reason;
         if (ctx.runtime.scheduler.channel.equals("BLADE")) {
            if (ctx.runtime.adapter != null) {
               AdapterRegistry.run(ctx.runtime.adapter, () -> ctx.runtime.adapter.cancel(ctx));
            }

            ctx.runtime.scheduler.clear();
            this.finishing.clear();
            ctx.runtime.actionId = "";
         }

         return Result.success();
      }
   }

   public JsonObject comboState(CombatContext ctx) {
      JsonObject out = new JsonObject();
      out.addProperty("comboId", ctx.runtime.comboId);
      out.addProperty("nodeId", ctx.runtime.comboNode);
      out.addProperty("actionId", ctx.runtime.actionId);
      out.addProperty("running", this.graph != null);
      out.addProperty("elapsed", this.graph == null ? 0L : ctx.now - this.graphStart);
      out.addProperty("interruptReason", ctx.runtime.interrupted);
      return out;
   }
}
