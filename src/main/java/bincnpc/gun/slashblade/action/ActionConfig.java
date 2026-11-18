package bincnpc.gun.slashblade.action;

import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.slashblade.combo.GraphValidator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public final class ActionConfig {
   public static final Parameter[] SCHEMA = new Parameter[]{
      Parameter.bool("enabled", false),
      Parameter.bool("auto", true),
      Parameter.integer("priority", 100, 0, 1000),
      Parameter.number("minDistance", 0.0, 0.0, 128.0),
      Parameter.number("maxDistance", 32.0, 0.0, 128.0),
      Parameter.integer("targetCount", 1, 1, 16),
      Parameter.choice("targetType", "ANY", "ANY", "PLAYER", "NPC", "MOB"),
      Parameter.number("cooldownMultiplier", 1.0, 0.05, 10.0),
      Parameter.number("resourceMultiplier", 1.0, 0.1, 10.0),
      Parameter.integer("resourceReserve", 0, 0, 100000),
      Parameter.bool("interruptNormal", false),
      Parameter.bool("consecutive", false),
      Parameter.bool("linkPhantom", false),
      Parameter.choice("failure", "SKIP", "SKIP", "STOP"),
      new Parameter("successNode", "string", "", null, null, null, "")
   };

   private ActionConfig() {
   }

   public static JsonObject validate(JsonObject source, ActionRegistry.Definition def) {
      JsonObject input = source.deepCopy();
      JsonArray conditions = input.has("conditions") ? input.getAsJsonArray("conditions") : new JsonArray();
      GraphValidator.validateConditions(conditions);
      JsonArray stages = input.has("stages") ? input.getAsJsonArray("stages") : new JsonArray();
      if (stages.size() > 16) {
         throw new IllegalArgumentException("stages");
      } else {
         for (JsonElement stage : stages) {
            if (!stage.isJsonPrimitive() || !stage.getAsJsonPrimitive().isString() || stage.getAsString().length() > 64) {
               throw new IllegalArgumentException("stage");
            }
         }

         JsonObject params = input.has("parameters") ? input.getAsJsonObject("parameters") : new JsonObject();
         input.remove("conditions");
         input.remove("stages");
         input.remove("parameters");
         JsonObject out = GraphValidator.parameters(input, SCHEMA);
         if (out.get("minDistance").getAsDouble() > out.get("maxDistance").getAsDouble()) {
            throw new IllegalArgumentException("distance range");
         } else {
            out.add("conditions", conditions.deepCopy());
            out.add("stages", stages.deepCopy());
            out.add("parameters", GraphValidator.parameters(params, def.info.parameters));
            return out;
         }
      }
   }

   public static JsonElement value(JsonObject obj, String id) {
      if (obj.has(id)) {
         return obj.get(id);
      } else {
         for (Parameter p : SCHEMA) {
            if (p.id.equals(id)) {
               return SafeJson.copy(p.defaultValue);
            }
         }

         return JsonNull.INSTANCE;
      }
   }
}
