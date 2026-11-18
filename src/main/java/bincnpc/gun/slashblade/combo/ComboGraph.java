package bincnpc.gun.slashblade.combo;

import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.Parameter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComboGraph {
   public final String id;
   public final String entryNode;
   public final Map<String, ComboGraph.Node> nodes;
   public final JsonObject original;

   ComboGraph(JsonObject obj) {
      this.original = obj.deepCopy();
      this.id = obj.get("id").getAsString();
      this.entryNode = obj.get("entryNode").getAsString();
      Map<String, ComboGraph.Node> out = new LinkedHashMap<>();

      for (JsonElement e : obj.getAsJsonArray("nodes")) {
         ComboGraph.Node node = new ComboGraph.Node(e.getAsJsonObject());
         if (out.putIfAbsent(node.id, node) != null) {
            throw new IllegalArgumentException("duplicate node");
         }
      }

      this.nodes = Collections.unmodifiableMap(out);
   }

   public static final class Node {
      public final String id;
      public final String action;
      public final String onSuccess;
      public final String onFailure;
      public final String onTimeout;
      public final int timeout;
      public final JsonObject parameters;
      public final JsonArray conditions;
      public final JsonArray interrupts;

      Node(JsonObject obj) {
         for (String key : List.of("id", "action", "onSuccess", "onFailure", "onTimeout")) {
            if (obj.has(key) && (!obj.get(key).isJsonPrimitive() || !obj.get(key).getAsJsonPrimitive().isString())) {
               throw new IllegalArgumentException("node field: " + key);
            }
         }

         for (String keyx : List.of("conditions", "interrupts")) {
            if (obj.has(keyx) && !obj.get(keyx).isJsonArray()) {
               throw new IllegalArgumentException("node array: " + keyx);
            }
         }

         this.id = SafeJson.string(obj, "id", "");
         this.action = SafeJson.string(obj, "action", "");
         this.onSuccess = SafeJson.string(obj, "onSuccess", "end");
         this.onFailure = SafeJson.string(obj, "onFailure", "end");
         this.onTimeout = SafeJson.string(obj, "onTimeout", this.onFailure);
         this.timeout = obj.has("timeout") ? Parameter.integer("timeout", 200, 1, 2400).validate(obj.get("timeout")).getAsInt() : 200;
         this.parameters = obj.has("parameters") ? obj.getAsJsonObject("parameters").deepCopy() : new JsonObject();
         this.conditions = obj.has("conditions") && obj.get("conditions").isJsonArray() ? obj.getAsJsonArray("conditions").deepCopy() : new JsonArray();
         this.interrupts = obj.has("interrupts") && obj.get("interrupts").isJsonArray() ? obj.getAsJsonArray("interrupts").deepCopy() : new JsonArray();
      }
   }
}
