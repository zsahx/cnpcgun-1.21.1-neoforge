package bincnpc.gun.slashblade.combo;

import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.combat.CombatContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class Conditions {
   public static final Set<String> IDS = Set.of(
      "always",
      "target_valid",
      "target_close",
      "target_far",
      "multiple_targets",
      "on_ground",
      "in_air",
      "low_health",
      "ammo_available",
      "damaged_recently",
      "target_changed",
      "distance",
      "health",
      "target_count",
      "weapon_type",
      "state",
      "resource",
      "combat_time"
   );

   private Conditions() {
   }

   public static String[] ids() {
      return IDS.stream().sorted().toArray(String[]::new);
   }

   public static void validate(JsonElement condition) {
      String id = condition.isJsonPrimitive() && condition.getAsJsonPrimitive().isString()
         ? condition.getAsString()
         : (condition.isJsonObject() ? SafeJson.string(condition.getAsJsonObject(), "id", "") : "");
      if (!IDS.contains(id)) {
         throw new IllegalArgumentException("unknown condition: " + id);
      } else {
         if (condition.isJsonObject()) {
            JsonObject obj = condition.getAsJsonObject();

            for (String key : obj.keySet()) {
               if (!Set.of("id", "min", "max", "value", "not").contains(key)) {
                  throw new IllegalArgumentException("condition field: " + key);
               }
            }

            for (String keyx : List.of("min", "max")) {
               if (obj.has(keyx)) {
                  JsonElement v = obj.get(keyx);
                  if (!v.isJsonPrimitive()
                     || !v.getAsJsonPrimitive().isNumber()
                     || !Double.isFinite(v.getAsDouble())
                     || v.getAsDouble() < 0.0
                     || v.getAsDouble() > 100000.0) {
                     throw new IllegalArgumentException("condition range");
                  }
               }
            }

            if (obj.has("min") && obj.has("max") && obj.get("min").getAsDouble() > obj.get("max").getAsDouble()) {
               throw new IllegalArgumentException("condition range");
            }

            if (obj.has("not") && (!obj.get("not").isJsonPrimitive() || !obj.get("not").getAsJsonPrimitive().isBoolean())) {
               throw new IllegalArgumentException("condition negation");
            }

            if (Set.of("weapon_type", "state").contains(id)
               && (!obj.has("value") || !obj.get("value").isJsonPrimitive() || !obj.get("value").getAsJsonPrimitive().isString())) {
               throw new IllegalArgumentException("condition value");
            }
         } else if (Set.of("distance", "health", "target_count", "weapon_type", "state", "resource", "combat_time").contains(id)) {
            throw new IllegalArgumentException("condition needs parameters");
         }
      }
   }

   public static boolean all(JsonArray conditions, CombatContext ctx) {
      for (JsonElement condition : conditions) {
         if (!test(condition, ctx)) {
            return false;
         }
      }

      return true;
   }

   public static boolean any(JsonArray conditions, CombatContext ctx) {
      for (JsonElement condition : conditions) {
         if (test(condition, ctx)) {
            return true;
         }
      }

      return false;
   }

   public static boolean test(JsonElement condition, CombatContext ctx) {
      JsonObject params = condition.isJsonObject() ? condition.getAsJsonObject() : new JsonObject();
      String id = condition.isJsonObject() ? SafeJson.string(params, "id", "") : condition.getAsString();
      double health = ctx.npc.getHealth() / Math.max(1.0F, ctx.npc.getMaxHealth());

      boolean result = switch (id) {
         case "always" -> true;
         case "target_valid" -> ctx.validTarget();
         case "target_close" -> ctx.validTarget() && ctx.distance <= 4.0;
         case "target_far" -> ctx.validTarget() && ctx.distance > 8.0;
         case "multiple_targets" -> ctx.targets().size() >= 2;
         case "on_ground" -> ctx.npc.onGround();
         case "in_air" -> !ctx.npc.onGround();
         case "low_health" -> health <= ctx.config.number("weapon.lowHealth");
         case "ammo_available" -> !ctx.runtime.ammoEmpty;
         case "damaged_recently" -> ctx.now - ctx.runtime.lastHurt <= 20L;
         case "target_changed" -> !Objects.equals(ctx.runtime.comboTarget, ctx.runtime.targetId);
         case "distance" -> ctx.validTarget() && range(ctx.distance, params);
         case "health" -> range(health, params);
         case "target_count" -> range(ctx.targets().size(), params);
         case "combat_time" -> ctx.validTarget() && range(ctx.now - ctx.runtime.firstTargetTick, params);
         case "weapon_type" -> ctx.runtime.domain.equals(SafeJson.string(params, "value", ""));
         case "state" -> ctx.runtime.combatState.equals(SafeJson.string(params, "value", ""));
         case "resource" -> {
            JsonObject state = ctx.data.blade.state(ctx);
            yield state.has("resource") && state.get("resource").isJsonPrimitive() && range(state.get("resource").getAsDouble(), params);
         }
         default -> false;
      };
      return params.has("not") && params.get("not").getAsBoolean() ? !result : result;
   }

   private static boolean range(double value, JsonObject params) {
      return value >= (params.has("min") ? params.get("min").getAsDouble() : 0.0) && value <= (params.has("max") ? params.get("max").getAsDouble() : 100000.0);
   }
}
