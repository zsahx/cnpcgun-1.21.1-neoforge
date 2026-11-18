package bincnpc.gun.slashblade.combo;

import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.slashblade.action.ActionRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GraphValidator {
   private GraphValidator() {
   }

   public static ComboGraph validate(JsonObject input, Map<String, ActionRegistry.Definition> actions, boolean allowMissing) {
      JsonObject obj = SafeJson.object(input);
      if (obj.has("nodes") && obj.get("nodes").isJsonArray()) {
         if (!obj.getAsJsonArray("nodes").isEmpty() && obj.getAsJsonArray("nodes").size() <= 128) {
            String id = SafeJson.string(obj, "id", "");
            String entry = SafeJson.string(obj, "entryNode", "");
            if (validId(id) && validId(entry)) {
               ComboGraph graph = new ComboGraph(obj);
               if (!graph.nodes.containsKey(entry)) {
                  throw new IllegalArgumentException("entry node missing");
               } else {
                  for (ComboGraph.Node node : graph.nodes.values()) {
                     if (validId(node.id) && !node.id.equals("end")) {
                        if (!node.action.matches("slashblade:[a-z0-9_.-]+/[a-z0-9_./-]{1,160}")) {
                           throw new IllegalArgumentException("action ID");
                        }

                        for (String next : List.of(node.onSuccess, node.onFailure, node.onTimeout)) {
                           if (!next.equals("end") && !graph.nodes.containsKey(next)) {
                              throw new IllegalArgumentException("invalid jump: " + next);
                           }
                        }

                        if (node.timeout >= 1 && node.timeout <= 2400) {
                           ActionRegistry.Definition def = actions.get(node.action);
                           if (def == null) {
                              if (!allowMissing) {
                                 throw new IllegalArgumentException("missing action: " + node.action);
                              }
                           } else {
                              parameters(node.parameters, def.info.parameters);
                           }

                           validateConditions(node.conditions);
                           validateConditions(node.interrupts);
                           continue;
                        }

                        throw new IllegalArgumentException("node timeout");
                     }

                     throw new IllegalArgumentException("node ID");
                  }

                  Map<String, Integer> colors = new HashMap<>();

                  for (String node : graph.nodes.keySet()) {
                     visit(graph, node, colors);
                  }

                  return graph;
               }
            } else {
               throw new IllegalArgumentException("graph ID");
            }
         } else {
            throw new IllegalArgumentException("node count");
         }
      } else {
         throw new IllegalArgumentException("nodes must be an array");
      }
   }

   private static boolean validId(String id) {
      return id.matches("[A-Za-z0-9_.:-]{1,64}");
   }

   private static void visit(ComboGraph graph, String id, Map<String, Integer> colors) {
      if (!id.equals("end")) {
         int color = colors.getOrDefault(id, 0);
         if (color == 1) {
            throw new IllegalArgumentException("cyclic graph");
         } else if (color != 2) {
            colors.put(id, 1);
            ComboGraph.Node node = graph.nodes.get(id);

            for (String next : List.of(node.onSuccess, node.onFailure, node.onTimeout)) {
               visit(graph, next, colors);
            }

            colors.put(id, 2);
         }
      }
   }

   public static void validateConditions(JsonArray conditions) {
      if (conditions.size() > 16) {
         throw new IllegalArgumentException("too many conditions");
      } else {
         for (JsonElement condition : conditions) {
            Conditions.validate(condition);
         }
      }
   }

   public static JsonObject parameters(JsonObject input, Parameter[] schema) {
      JsonObject out = new JsonObject();
      Map<String, Parameter> known = new LinkedHashMap<>();

      for (Parameter p : schema) {
         known.put(p.id, p);
      }

      for (String key : input.keySet()) {
         if (!known.containsKey(key)) {
            throw new IllegalArgumentException("unknown parameter: " + key);
         }
      }

      for (Parameter p : schema) {
         out.add(p.id, p.validate(input.has(p.id) ? input.get(p.id) : SafeJson.copy(p.defaultValue)));
      }

      return out;
   }
}
