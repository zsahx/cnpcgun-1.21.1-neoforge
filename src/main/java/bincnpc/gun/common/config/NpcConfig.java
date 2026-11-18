package bincnpc.gun.common.config;

import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.api.SafeJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public final class NpcConfig {
   public static final int SCHEMA = 1;
   private JsonObject root;
   public long revision;

   public NpcConfig() {
      this(new JsonObject());
   }

   public NpcConfig(JsonObject source) {
      if (source == null) {
         throw new IllegalArgumentException("configuration is null");
      } else {
         this.root = source.deepCopy();
         int version = this.root.has("schemaVersion")
            ? Parameter.integer("schemaVersion", 1, 0, Integer.MAX_VALUE).validate(this.root.get("schemaVersion")).getAsInt()
            : (source.size() == 0 ? 1 : 0);

         for (String field : new String[]{"values", "graphs", "actions", "presetCopies"}) {
            if (!this.root.has(field)) {
               this.root.add(field, new JsonObject());
            } else if (!this.root.get(field).isJsonObject()) {
               throw new IllegalArgumentException("invalid " + field);
            }
         }

         if (version == 0) {
            JsonObject values = this.root.getAsJsonObject("values");

            for (Parameter p : ConfigSchema.all()) {
               if (this.root.has(p.id) && !values.has(p.id)) {
                  values.add(p.id, this.root.get(p.id).deepCopy());
               }
            }

            this.root.addProperty("migratedFromSchema", 0);
            version = 1;
         }

         this.root.addProperty("schemaVersion", version);
         this.repairRange("gun.minRange", "gun.range");
         this.repairRange("gun.minInterval", "gun.maxInterval");
         this.repairRange("gun.reloadMinTicks", "gun.reloadMaxTicks");
         this.repairRange("ammo.minimumReserve", "ammo.reserveLimit");

         for (int i = 0; i < 6; i++) {
            if (this.bool("weapon.slot" + i + ".minRangeEnabled") && this.bool("weapon.slot" + i + ".maxRangeEnabled")) {
               this.repairRange("weapon.slot" + i + ".minRange", "weapon.slot" + i + ".maxRange");
            }
         }

         for (int ix = 0; ix < 3; ix++) {
            this.repairRange("throw.slot" + ix + ".minRange", "throw.slot" + ix + ".maxRange");
         }
      }
   }

   private void repairRange(String lower, String upper) {
      if (!(this.number(lower) <= this.number(upper))) {
         JsonObject values = this.root.getAsJsonObject("values");
         JsonObject recovery = this.root.has("recoveredRanges") && this.root.get("recoveredRanges").isJsonObject()
            ? this.root.getAsJsonObject("recoveredRanges")
            : new JsonObject();

         for (String key : new String[]{lower, upper}) {
            if (values.has(key)) {
               recovery.add(key, values.get(key).deepCopy());
            }

            values.add(key, SafeJson.copy(ConfigSchema.get(key).defaultValue));
         }

         this.root.add("recoveredRanges", recovery);
      }
   }

   public JsonObject snapshot() {
      return this.root.deepCopy();
   }

   public JsonElement get(String key) {
      Parameter p = ConfigSchema.get(key);
      if (p == null) {
         return JsonNull.INSTANCE;
      } else {
         JsonElement value = this.root.getAsJsonObject("values").get(key);

         try {
            return p.validate(value == null ? SafeJson.copy(p.defaultValue) : value);
         } catch (IllegalStateException | IllegalArgumentException var5) {
            return SafeJson.copy(p.defaultValue);
         }
      }
   }

   public boolean bool(String key) {
      return this.get(key).getAsBoolean();
   }

   public int integer(String key) {
      return this.get(key).getAsInt();
   }

   public double number(String key) {
      return this.get(key).getAsDouble();
   }

   public String text(String key) {
      return this.get(key).getAsString();
   }

   public void set(String key, JsonElement value) {
      Parameter p = ConfigSchema.get(key);
      if (p == null) {
         throw new IllegalArgumentException("unknown configuration key");
      } else {
         this.root.getAsJsonObject("values").add(key, p.validate(value));
         this.revision++;
      }
   }

   public JsonObject graphs() {
      return this.root.getAsJsonObject("graphs").deepCopy();
   }

   public JsonObject graph(String id) {
      if (id == null) {
         return null;
      } else {
         JsonElement graph = this.root.getAsJsonObject("graphs").get(id);
         return graph != null && graph.isJsonObject() ? graph.getAsJsonObject().deepCopy() : null;
      }
   }

   public void putGraph(String id, JsonObject graph) {
      this.root.getAsJsonObject("graphs").add(id, graph.deepCopy());
      this.root.addProperty("selectedGraph", id);
      this.revision++;
   }

   public String selectedGraph() {
      return SafeJson.string(this.root, "selectedGraph", "");
   }

   public Result selectGraph(String id) {
      if (this.graph(id) == null) {
         return Result.fail("NOT_FOUND");
      } else {
         if (!id.equals(this.selectedGraph())) {
            this.root.addProperty("selectedGraph", id);
            this.revision++;
         }

         return Result.success();
      }
   }

   public Result removeGraph(String id) {
      if (this.graph(id) == null) {
         return Result.fail("NOT_FOUND");
      } else {
         this.root.getAsJsonObject("graphs").remove(id);
         if (id.equals(this.selectedGraph())) {
            this.root.addProperty("selectedGraph", "");
         }

         this.revision++;
         return Result.success();
      }
   }

   public JsonObject action(String id) {
      JsonElement action = this.root.getAsJsonObject("actions").get(id);
      return action != null && action.isJsonObject() ? action.getAsJsonObject().deepCopy() : new JsonObject();
   }

   public void putAction(String id, JsonObject value) {
      this.root.getAsJsonObject("actions").add(id, value.deepCopy());
      this.revision++;
   }

   public JsonObject actions() {
      return this.root.getAsJsonObject("actions").deepCopy();
   }

   public void saveCopy(String id, JsonObject value) {
      this.root.getAsJsonObject("presetCopies").add(id, value.deepCopy());
      this.revision++;
   }

   public JsonObject getCopy(String id) {
      if (id == null) {
         return null;
      } else {
         JsonElement v = this.root.getAsJsonObject("presetCopies").get(id);
         return v != null && v.isJsonObject() ? v.getAsJsonObject().deepCopy() : null;
      }
   }

   public Result removeCopy(String id) {
      if (this.getCopy(id) == null) {
         return Result.fail("NOT_FOUND");
      } else {
         this.root.getAsJsonObject("presetCopies").remove(id);
         this.revision++;
         return Result.success();
      }
   }

   public void replace(JsonObject value) {
      this.root = new NpcConfig(value).snapshot();
      this.revision++;
   }
}
