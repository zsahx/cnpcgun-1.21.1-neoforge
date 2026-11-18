package bincnpc.gun.slashblade.strategy;

import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.config.NpcConfig;
import bincnpc.gun.common.network.EditorWeapons;
import bincnpc.gun.common.persistence.PresetLibrary;
import bincnpc.gun.slashblade.action.ActionRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public final class Presets {
   public static final String[] BUILTINS = new String[]{"BEGINNER", "NORMAL", "CUSTOM", "MAID"};

   private Presets() {
   }

   public static JsonObject template(String id) {
      if (!Arrays.asList(BUILTINS).contains(id)) {
         return null;
      } else {
         NpcConfig config = new NpcConfig();
         config.set("slashblade.preset", new JsonPrimitive(id));
         config.set("slashblade.attackInterval", new JsonPrimitive(id.equals("MAID") ? 4 : (id.equals("NORMAL") ? 8 : 12)));

         for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) {
            config.putAction(def.info.id, defaultAction(id, def));
         }

         if (!id.equals("CUSTOM")) {
            List<ActionRegistry.Definition> sequence = Arrays.stream(ActionRegistry.INSTANCE.all())
               .filter(def -> def.info.type.equals("NORMAL_ATTACK") && !def.tags.contains("air"))
               .sorted(Comparator.comparing(def -> def.info.id))
               .limit(id.equals("BEGINNER") ? 3L : 6L)
               .collect(Collectors.toCollection(ArrayList::new));
            if (!id.equals("BEGINNER")) {
               Arrays.stream(ActionRegistry.INSTANCE.all()).filter(def -> def.info.type.equals("SPECIAL_ATTACK")).findFirst().ifPresent(sequence::add);
            }

            if (id.equals("MAID")) {
               Arrays.stream(ActionRegistry.INSTANCE.all())
                  .filter(def -> def.info.type.equals("PHANTOM_SWORD") && def.tags.contains("basic"))
                  .findFirst()
                  .ifPresent(sequence::add);
            }

            if (!sequence.isEmpty()) {
               JsonObject graph = new JsonObject();
               String graphId = "preset_" + id.toLowerCase(Locale.ROOT);
               graph.addProperty("id", graphId);
               graph.addProperty("entryNode", "n1");
               JsonArray nodes = new JsonArray();

               for (int i = 0; i < sequence.size(); i++) {
                  JsonObject node = new JsonObject();
                  node.addProperty("id", "n" + (i + 1));
                  node.addProperty("action", sequence.get(i).info.id);
                  node.add("parameters", new JsonObject());
                  node.add("conditions", new JsonArray());
                  node.add("interrupts", new JsonArray());
                  node.addProperty("timeout", 200);
                  node.addProperty("onSuccess", i + 1 < sequence.size() ? "n" + (i + 2) : "end");
                  node.addProperty("onFailure", "end");
                  node.addProperty("onTimeout", "end");
                  nodes.add(node);
               }

               graph.add("nodes", nodes);
               config.putGraph(graphId, graph);
            }
         }

         JsonObject root = config.snapshot();
         root.addProperty("presetVersion", 3);
         root.addProperty("templateId", id);
         return root;
      }
   }

   public static JsonObject defaultAction(String preset, ActionRegistry.Definition def) {
      JsonObject out = new JsonObject();
      boolean special = def.info.type.equals("SPECIAL_ATTACK");
      boolean phantom = def.info.type.equals("PHANTOM_SWORD");
      boolean beginner = preset.equals("BEGINNER");
      boolean maid = preset.equals("MAID");
      boolean basic = def.tags.contains("basic");
      String firstSpecial = Arrays.stream(ActionRegistry.INSTANCE.all())
         .filter(a -> a.info.type.equals("SPECIAL_ATTACK"))
         .map(a -> a.info.id)
         .findFirst()
         .orElse("");
      out.addProperty(
         "enabled",
         !preset.equals("CUSTOM")
            && (
               def.info.type.equals("NORMAL_ATTACK")
                  || def.info.type.equals("COMBO_ATTACK")
                  || special && (!beginner || def.info.id.equals(firstSpecial))
                  || phantom && (!beginner || basic)
                  || maid && Set.of("MOVEMENT", "DEFENSE", "COUNTER", "LOCK_ON").contains(def.info.type)
            )
            && (!def.tags.contains("super") || maid)
      );
      out.addProperty("priority", special ? 180 : (basic ? 100 : (def.tags.contains("storm") ? 140 : 120)));
      double cooldown = beginner ? 4.0 : (maid ? 0.1 : 2.0);
      if (maid && phantom) {
         cooldown = basic
            ? 0.5
            : (
               def.tags.contains("rain")
                  ? 2.5
                  : (def.tags.contains("blistering") ? 1.675 : (!def.tags.contains("spiral") && !def.tags.contains("storm") ? 1.0 : 0.85))
            );
      }

      if (maid && def.backend.equals("guard")) {
         cooldown = 1.0;
      }

      if (maid && def.backend.equals("teleport")) {
         cooldown = 1.5;
      }

      if (maid && def.tags.contains("super")) {
         cooldown = 1.0;
      }

      out.addProperty("cooldownMultiplier", cooldown);
      out.addProperty("interruptNormal", maid);
      out.addProperty("consecutive", maid);
      out.addProperty("linkPhantom", false);
      if (maid) {
         JsonObject params = new JsonObject();
         if (special) {
            params.addProperty("artsType", def.tags.contains("super") ? "Super" : "Jackpot");
         }

         if (phantom && !def.backend.equals("phantom:spiral_fire")) {
            params.addProperty("count", basic ? 1 : (def.tags.contains("rain") ? 18 : 8));
         }

         if (phantom && Arrays.stream(def.info.parameters).anyMatch(p -> p.id.equals("targetCount"))) {
            params.addProperty("targetCount", 4);
         }

         if (def.backend.equals("guard")) {
            params.addProperty("duration", 50);
         }

         out.add("parameters", params);
      }

      JsonArray conditions = new JsonArray();
      conditions.add("target_valid");
      if (beginner && (special || phantom)) {
         JsonObject elapsed = new JsonObject();
         elapsed.addProperty("id", "combat_time");
         elapsed.addProperty("min", 80);
         conditions.add(elapsed);
      }

      if (!maid && phantom && !basic) {
         conditions.add("multiple_targets");
      }

      out.add("conditions", conditions);
      return out;
   }

   public static JsonObject action(CombatContext ctx, ActionRegistry.Definition def) {
      JsonObject stored = ctx.config.action(def.info.id);
      return stored.size() == 0 ? defaultAction(ctx.config.text("slashblade.preset"), def) : stored;
   }

   public static Result apply(CombatContext ctx, String id) {
      if (id == null || !id.matches("[A-Za-z0-9_.:-]{1,64}")) {
         return Result.fail("INVALID_ARGUMENT");
      } else if (!EditorWeapons.has(ctx, "slashblade.attack")) {
         return Result.fail("UNSUPPORTED");
      } else if (!ctx.runtime.scheduler.busy(ctx.now) && !ctx.data.throwing.blocksAttack(ctx.now)) {
         JsonObject preset = ctx.config.getCopy(id);
         if (preset == null) {
            preset = id.startsWith("user:") ? PresetLibrary.get(ctx).find(id) : template(id);
         }

         if (preset == null) {
            return Result.fail("NOT_FOUND");
         } else {
            JsonObject previous = ctx.config.snapshot();
            JsonObject copies = previous.getAsJsonObject("presetCopies").deepCopy();
            if (id.startsWith("user:")) {
               preset.addProperty("templateId", id);
               preset.getAsJsonObject("values").addProperty("slashblade.preset", "CUSTOM");
            }

            JsonObject next = previous.deepCopy();
            JsonObject values = next.getAsJsonObject("values");

            for (Entry<String, JsonElement> e : preset.getAsJsonObject("values").entrySet()) {
               if (e.getKey().startsWith("slashblade.")) {
                  values.add(e.getKey(), e.getValue().deepCopy());
               }
            }

            next.add("graphs", preset.getAsJsonObject("graphs").deepCopy());
            next.add("actions", preset.getAsJsonObject("actions").deepCopy());
            next.add("presetCopies", copies);
            next.addProperty("presetVersion", 3);
            next.addProperty("templateId", id);
            next.addProperty("selectedGraph", preset.has("selectedGraph") ? preset.get("selectedGraph").getAsString() : "");
            ctx.config.replace(next);
            return Result.success(id);
         }
      } else {
         return Result.fail("BUSY");
      }
   }

   public static Result remove(CombatContext ctx, String id) {
      if (Arrays.asList(BUILTINS).contains(id)) {
         return Result.fail("PERMISSION_DENIED");
      } else {
         return id.startsWith("user:") ? PresetLibrary.get(ctx).remove(id) : ctx.config.removeCopy(id);
      }
   }

   public static Result copy(CombatContext ctx, String source, String destination) {
      if (source != null && source.matches("[A-Za-z0-9_.:-]{1,64}") && destination != null && destination.matches("[A-Za-z0-9_.:-]{1,64}")) {
         JsonObject data = ctx.config.getCopy(source);
         if (data == null) {
            data = source.startsWith("user:") ? PresetLibrary.get(ctx).find(source) : template(source);
         }

         if (data == null) {
            return Result.fail("NOT_FOUND");
         } else if (ctx.config.getCopy(destination) == null && ctx.config.snapshot().getAsJsonObject("presetCopies").size() < 32) {
            data.remove("presetCopies");
            data.getAsJsonObject("values").addProperty("slashblade.preset", "CUSTOM");
            ctx.config.saveCopy(destination, data);
            return Result.success();
         } else {
            return Result.fail("CONFLICT");
         }
      } else {
         return Result.fail("INVALID_ARGUMENT");
      }
   }
}
