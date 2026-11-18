package bincnpc.gun.client.gui.editor;

import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.ConfigSchema;
import bincnpc.gun.common.config.Parameter;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public final class EditorDraft {
   private final JsonObject changed = new JsonObject();

   public JsonElement value(JsonObject base, String key) {
      JsonElement value = this.changed.has(key) ? this.changed.get(key) : (base == null ? null : base.get(key));
      if (value != null && !value.isJsonNull()) {
         return value.deepCopy();
      } else {
         Parameter schema = ConfigSchema.get(key);
         return (JsonElement)(schema == null ? JsonNull.INSTANCE : SafeJson.copy(schema.defaultValue));
      }
   }

   public void set(JsonObject base, String key, JsonElement value) {
      if (key != null && value != null && !value.isJsonNull()) {
         if (value.equals(base == null ? null : base.get(key))) {
            this.changed.remove(key);
         } else {
            this.changed.add(key, value.deepCopy());
         }
      } else {
         throw new IllegalArgumentException("draft value");
      }
   }

   public boolean dirty() {
      return this.changed.size() > 0;
   }

   public int size() {
      return this.changed.size();
   }

   public JsonObject snapshot() {
      return this.changed.deepCopy();
   }

   public List<JsonObject> batches(int limit) {
      if (limit < 4) {
         throw new IllegalArgumentException("batch limit");
      } else {
         Map<String, JsonObject> groups = new LinkedHashMap<>();

         for (Entry<String, JsonElement> entry : this.changed.entrySet()) {
            groups.computeIfAbsent(group(entry.getKey()), ignored -> new JsonObject()).add(entry.getKey(), entry.getValue().deepCopy());
         }

         List<JsonObject> batches = new ArrayList<>();
         JsonObject batch = new JsonObject();

         for (JsonObject group : groups.values()) {
            if (group.size() > limit) {
               throw new IllegalArgumentException("dependent fields exceed batch limit");
            }

            if (batch.size() + group.size() > limit) {
               batches.add(batch);
               batch = new JsonObject();
            }

            for (Entry<String, JsonElement> entry : group.entrySet()) {
               batch.add(entry.getKey(), entry.getValue());
            }
         }

         if (batch.size() > 0) {
            batches.add(batch);
         }

         return batches;
      }
   }

   private static String group(String key) {
      if (Set.of("gun.minRange", "gun.range").contains(key)) {
         return "gun.range";
      } else if (Set.of("gun.minInterval", "gun.maxInterval").contains(key)) {
         return "gun.interval";
      } else if (Set.of("gun.reloadMinTicks", "gun.reloadMaxTicks").contains(key)) {
         return "gun.reloadDuration";
      } else if (Set.of("ammo.minimumReserve", "ammo.reserveLimit").contains(key)) {
         return "ammo.reserve";
      } else if (key.startsWith("weapon.slot")) {
         return key.substring(0, key.lastIndexOf(46));
      } else {
         return !key.startsWith("throw.slot") || !key.endsWith(".minRange") && !key.endsWith(".maxRange")
            ? key
            : key.substring(0, key.lastIndexOf(46)) + ".range";
      }
   }

   public void clear() {
      this.changed.asMap().clear();
   }

   public void reconcile(JsonObject current) {
      if (current != null) {
         for (String key : new ArrayList<String>(this.changed.keySet())) {
            if (this.changed.get(key).equals(current.get(key))) {
               this.changed.remove(key);
            }
         }
      }
   }

   public void acknowledge(JsonObject submitted) {
      if (submitted != null) {
         for (String key : submitted.keySet()) {
            if (this.changed.has(key) && this.changed.get(key).equals(submitted.get(key))) {
               this.changed.remove(key);
            }
         }
      }
   }
}
