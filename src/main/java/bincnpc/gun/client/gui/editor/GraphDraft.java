package bincnpc.gun.client.gui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GraphDraft {
   private JsonObject original;
   public final JsonObject graph;
   public final Map<String, GraphDraft.Point> positions = new HashMap<>();

   public GraphDraft(JsonObject source, String initialId) {
      this.graph = source == null ? new JsonObject() : source.deepCopy();
      if (!this.graph.has("id")) {
         this.graph.addProperty("id", initialId == null ? "" : initialId);
      }

      if (!this.graph.has("entryNode")) {
         this.graph.addProperty("entryNode", "");
      }

      if (!this.graph.has("nodes")) {
         this.graph.add("nodes", new JsonArray());
      }

      text(this.graph, "id");
      text(this.graph, "entryNode");
      if (this.nodes().size() > 128) {
         throw new IllegalArgumentException("node limit");
      } else {
         for (JsonElement raw : this.nodes()) {
            if (!raw.isJsonObject()) {
               throw new IllegalArgumentException("node must be an object");
            }

            normalizeNode(raw.getAsJsonObject());
         }

         this.original = this.graph.deepCopy();
      }
   }

   private static String text(JsonObject object, String key) {
      JsonElement value = object == null ? null : object.get(key);
      if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
         return value.getAsString();
      } else {
         throw new IllegalArgumentException("graph field: " + key);
      }
   }

   private static boolean absent(JsonObject object, String key) {
      return !object.has(key) || object.get(key).isJsonNull();
   }

   private static void normalizeNode(JsonObject node) {
      if (!text(node, "id").isEmpty() && !text(node, "action").isEmpty()) {
         if (absent(node, "onSuccess")) {
            node.addProperty("onSuccess", "end");
         }

         if (absent(node, "onFailure")) {
            node.addProperty("onFailure", "end");
         }

         if (absent(node, "onTimeout")) {
            node.addProperty("onTimeout", text(node, "onFailure"));
         }

         for (String edge : List.of("onSuccess", "onFailure", "onTimeout")) {
            text(node, edge);
         }

         if (absent(node, "timeout")) {
            node.addProperty("timeout", 200);
         }

         JsonElement timeout = node.get("timeout");
         if (timeout.isJsonPrimitive() && timeout.getAsJsonPrimitive().isNumber()) {
            double ticks = timeout.getAsDouble();
            if (Double.isFinite(ticks) && ticks == Math.rint(ticks) && !(ticks < 1.0) && !(ticks > 2400.0)) {
               for (String key : List.of("conditions", "interrupts")) {
                  if (absent(node, key)) {
                     node.add(key, new JsonArray());
                  } else if (!node.get(key).isJsonArray()) {
                     throw new IllegalArgumentException("node array: " + key);
                  }
               }

               if (absent(node, "parameters")) {
                  node.add("parameters", new JsonObject());
               } else if (!node.get("parameters").isJsonObject()) {
                  throw new IllegalArgumentException("node parameters");
               }
            } else {
               throw new IllegalArgumentException("node timeout");
            }
         } else {
            throw new IllegalArgumentException("node timeout");
         }
      } else {
         throw new IllegalArgumentException("node identity");
      }
   }

   public JsonArray nodes() {
      JsonElement value = this.graph.get("nodes");
      if (value != null && value.isJsonArray()) {
         return value.getAsJsonArray();
      } else {
         throw new IllegalArgumentException("graph nodes must be an array");
      }
   }

   public List<String> ids() {
      return this.nodes().asList().stream().map(raw -> text(raw.getAsJsonObject(), "id")).toList();
   }

   public JsonObject node(String id) {
      for (JsonElement raw : this.nodes()) {
         if (text(raw.getAsJsonObject(), "id").equals(id)) {
            return raw.getAsJsonObject();
         }
      }

      return null;
   }

   public boolean dirty() {
      return !this.graph.equals(this.original);
   }

   public JsonObject original() {
      return this.original.deepCopy();
   }

   public void acknowledge(JsonObject submitted) {
      if (submitted == null) {
         throw new IllegalArgumentException("submitted graph");
      } else {
         this.original = submitted.deepCopy();
      }
   }

   public String unique(String prefix) {
      int n = 1;

      while (this.ids().contains(prefix + n)) {
         n++;
      }

      return prefix + n;
   }

   public String add(String action) {
      if (action != null && !action.isEmpty()) {
         if (this.nodes().size() >= 128) {
            throw new IllegalArgumentException("node limit");
         } else {
            String id = this.unique("n");
            JsonObject node = new JsonObject();
            node.addProperty("id", id);
            node.addProperty("action", action);
            node.add("parameters", new JsonObject());
            node.add("conditions", new JsonArray());
            node.add("interrupts", new JsonArray());
            node.addProperty("timeout", 200);

            for (String edge : List.of("onSuccess", "onFailure", "onTimeout")) {
               node.addProperty(edge, "end");
            }

            if (this.nodes().isEmpty()) {
               this.graph.addProperty("entryNode", id);
            } else {
               JsonObject prior = this.nodes().get(this.nodes().size() - 1).getAsJsonObject();
               if (prior.get("onSuccess").getAsString().equals("end")) {
                  prior.addProperty("onSuccess", id);
               }
            }

            this.nodes().add(node);
            return id;
         }
      } else {
         throw new IllegalArgumentException("node action");
      }
   }

   public String copy(String id) {
      if (this.nodes().size() < 128 && this.node(id) != null) {
         JsonObject copy = this.node(id).deepCopy();
         String next = this.unique("copy");
         copy.addProperty("id", next);
         this.nodes().add(copy);
         return next;
      } else {
         throw new IllegalArgumentException("node");
      }
   }

   public void update(String oldId, JsonObject changed) {
      if (changed == null) {
         throw new IllegalArgumentException("node");
      } else {
         JsonObject replacement = changed.deepCopy();
         normalizeNode(replacement);
         String next = text(replacement, "id");
         if (next.matches("[A-Za-z0-9_.:-]{1,64}") && !next.equals("end") && (next.equals(oldId) || !this.ids().contains(next))) {
            int index = this.ids().indexOf(oldId);
            if (index < 0) {
               throw new IllegalArgumentException("missing node");
            } else {
               JsonObject before = this.graph.deepCopy();
               this.nodes().set(index, replacement);
               if (!next.equals(oldId)) {
                  if (this.graph.get("entryNode").getAsString().equals(oldId)) {
                     this.graph.addProperty("entryNode", next);
                  }

                  for (JsonElement raw : this.nodes()) {
                     for (String edge : List.of("onSuccess", "onFailure", "onTimeout")) {
                        if (raw.getAsJsonObject().get(edge).getAsString().equals(oldId)) {
                           raw.getAsJsonObject().addProperty(edge, next);
                        }
                     }
                  }
               }

               try {
                  this.validateEdges();
               } catch (IllegalArgumentException var11) {
                  this.graph.asMap().clear();
                  before.entrySet().forEach(entry -> this.graph.add((String)entry.getKey(), (JsonElement)entry.getValue()));
                  throw var11;
               }

               if (this.positions.containsKey(oldId)) {
                  GraphDraft.Point position = this.positions.remove(oldId);
                  this.positions.put(next, position);
               }
            }
         } else {
            throw new IllegalArgumentException("node ID");
         }
      }
   }

   public void remove(String id) {
      int index = this.ids().indexOf(id);
      if (index >= 0) {
         this.nodes().remove(index);
         this.positions.remove(id);

         for (JsonElement raw : this.nodes()) {
            for (String edge : List.of("onSuccess", "onFailure", "onTimeout")) {
               if (raw.getAsJsonObject().get(edge).getAsString().equals(id)) {
                  raw.getAsJsonObject().addProperty(edge, "end");
               }
            }
         }

         if (this.graph.get("entryNode").getAsString().equals(id)) {
            this.graph.addProperty("entryNode", this.nodes().isEmpty() ? "" : this.nodes().get(0).getAsJsonObject().get("id").getAsString());
         }
      }
   }

   public void reorder(String id, int offset) {
      int index = this.ids().indexOf(id);
      int next = index + offset;
      if (index >= 0 && next >= 0 && next < this.nodes().size()) {
         JsonElement item = this.nodes().get(index);
         this.nodes().set(index, this.nodes().get(next));
         this.nodes().set(next, item);
      }
   }

   public void validateEdges() {
      Set<String> ids = new HashSet<>(this.ids());
      if (ids.size() != this.nodes().size()) {
         throw new IllegalArgumentException("duplicate node");
      } else if (!ids.contains("end") && (ids.isEmpty() || ids.contains(this.graph.get("entryNode").getAsString()))) {
         Map<String, Integer> colors = new HashMap<>();

         for (String id : ids) {
            this.visit(id, ids, colors);
         }
      } else {
         throw new IllegalArgumentException("entry node");
      }
   }

   private void visit(String id, Set<String> ids, Map<String, Integer> colors) {
      if (!id.equals("end")) {
         if (!ids.contains(id)) {
            throw new IllegalArgumentException("missing jump");
         } else {
            int color = colors.getOrDefault(id, 0);
            if (color == 1) {
               throw new IllegalArgumentException("cycle");
            } else if (color != 2) {
               colors.put(id, 1);
               JsonObject node = this.node(id);

               for (String edge : List.of("onSuccess", "onFailure", "onTimeout")) {
                  this.visit(node.get(edge).getAsString(), ids, colors);
               }

               colors.put(id, 2);
            }
         }
      }
   }

   public record Point(double x, double y) {
   }
}
