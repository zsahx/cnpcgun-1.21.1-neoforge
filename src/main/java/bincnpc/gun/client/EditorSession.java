package bincnpc.gun.client;

import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.api.Views;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.common.network.Equipment;
import bincnpc.gun.common.network.GunNetwork;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class EditorSession {
   public final UUID npcId;
   public UUID token = GunNetwork.NO_TOKEN;
   public long revision;
   public JsonObject snapshot = new JsonObject();
   public ItemStack[] slots = Equipment.empty();
   public String status = "cnpcgun.gui.loading";
   public boolean pending;
   private int requestId;
   private int waitingTicks;
   private int idleTicks;
   private int heartbeatTicks;
   private Consumer<GunNetwork.Response> callback;
   private GunNetwork.Operation waitingOperation;
   public GunNetwork.Operation completedOperation;

   public EditorSession(UUID npcId) {
      this.npcId = npcId;
   }

   public boolean ready() {
      return this.snapshot != null && this.snapshot.has("values") && this.snapshot.get("values").isJsonObject();
   }

   public boolean send(GunNetwork.Operation operation, JsonObject input) {
      return this.send(operation, input, null);
   }

   public boolean send(GunNetwork.Operation operation, JsonObject input, Consumer<GunNetwork.Response> completed) {
      if (!this.pending && operation != null && Minecraft.getInstance().getConnection() != null) {
         try {
            String json = SafeJson.GSON.toJson(SafeJson.object(input));
            this.pending = true;
            this.waitingTicks = 0;
            this.idleTicks = 0;
            this.callback = completed;
            this.waitingOperation = operation;
            this.status = operation != GunNetwork.Operation.OPEN && operation != GunNetwork.Operation.REFRESH ? "cnpcgun.gui.saving" : "cnpcgun.gui.loading";
            PacketDistributor.sendToServer(new GunNetwork.Request(this.npcId, this.token, this.revision, ++this.requestId, operation, json));
            return true;
         } catch (IllegalStateException | IllegalArgumentException var5) {
            this.pending = false;
            this.callback = null;
            this.status = "cnpcgun.error.invalid_argument";
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean receive(GunNetwork.Response packet) {
      if (packet != null && this.pending && Objects.equals(packet.npcId(), this.npcId) && packet.requestId() == this.requestId) {
         this.pending = false;
         this.idleTicks = 0;
         this.completedOperation = this.waitingOperation;

         JsonObject data;
         try {
            data = SafeJson.persistedObject(packet.json());
         } catch (IllegalArgumentException var4) {
            this.callback = null;
            this.status = "cnpcgun.error.invalid_argument";
            return true;
         }

         if (data.has("values") && data.get("values").isJsonObject()) {
            this.snapshot = data;
            this.token = packet.token() == null ? GunNetwork.NO_TOKEN : packet.token();
            this.revision = packet.revision();
            this.slots = Equipment.empty();
            if (packet.slots() != null) {
               for (int i = 0; i < Math.min(this.slots.length, packet.slots().length); i++) {
                  if (packet.slots()[i] != null) {
                     this.slots[i] = packet.slots()[i].copy();
                  }
               }
            }
         }

         this.status = "SUCCESS".equals(packet.code())
            ? "cnpcgun.gui.saved"
            : "cnpcgun.error." + (packet.code() == null ? "internal_error" : packet.code().toLowerCase(Locale.ROOT));
         Consumer<GunNetwork.Response> completed = this.callback;
         this.callback = null;
         if (completed != null) {
            completed.accept(packet);
         }

         return true;
      } else {
         return false;
      }
   }

   public void tick() {
      this.tick(true);
   }

   public void tick(boolean allowRefresh) {
      if (Minecraft.getInstance().getConnection() == null) {
         this.pending = false;
         this.callback = null;
      } else {
         if (this.pending && ++this.waitingTicks > 200) {
            this.pending = false;
            this.callback = null;
            this.status = "cnpcgun.gui.timeout";
         }

         if (this.ready() && ++this.heartbeatTicks >= 600 && !this.pending && !this.token.equals(GunNetwork.NO_TOKEN)) {
            this.heartbeatTicks = 0;
            PacketDistributor.sendToServer(new GunNetwork.Request(this.npcId, this.token, this.revision, ++this.requestId, GunNetwork.Operation.HEARTBEAT, "{}"));
         }

         if (!allowRefresh) {
            this.idleTicks = 0;
         }

         if (allowRefresh && !this.pending && this.ready() && ++this.idleTicks >= 400) {
            this.send(GunNetwork.Operation.REFRESH, new JsonObject());
         }
      }
   }

   public void close() {
      if (!this.token.equals(GunNetwork.NO_TOKEN) && Minecraft.getInstance().getConnection() != null) {
         PacketDistributor.sendToServer(new GunNetwork.Request(this.npcId, this.token, this.revision, ++this.requestId, GunNetwork.Operation.CLOSE, "{}"));
      }

      this.snapshot = new JsonObject();
      this.slots = Equipment.empty();
      this.callback = null;
      this.pending = false;
      this.token = GunNetwork.NO_TOKEN;
   }

   public JsonObject values() {
      return this.ready() ? this.snapshot.getAsJsonObject("values") : new JsonObject();
   }

   public JsonArray array(String key) {
      return SafeJson.arrayField(this.snapshot, key);
   }

   public JsonArray actions() {
      JsonArray valid = new JsonArray();

      for (JsonElement item : this.array("actions")) {
         if (item.isJsonObject()) {
            JsonObject action = item.getAsJsonObject();
            if (!SafeJson.string(action, "id", "").isEmpty() && !SafeJson.string(action, "type", "").isEmpty()) {
               valid.add(action);
            }
         }
      }

      return valid;
   }

   public JsonObject action(String id) {
      if (id == null) {
         return new JsonObject();
      } else {
         JsonObject actions = SafeJson.objectField(this.snapshot, "actionConfig");
         return actions.has(id) && actions.get(id).isJsonObject() ? actions.getAsJsonObject(id).deepCopy() : new JsonObject();
      }
   }

   public JsonObject actionInfo(String id) {
      for (JsonElement item : this.actions()) {
         if (SafeJson.string(item.getAsJsonObject(), "id", "").equals(id)) {
            return item.getAsJsonObject().deepCopy();
         }
      }

      return null;
   }

   public boolean has(String capability) {
      if (this.ready() && capability != null) {
         for (JsonElement item : this.array("editableWeapons")) {
            if (item.isJsonObject()) {
               JsonObject weapon = item.getAsJsonObject();
               if (capability(weapon, capability)) {
                  return true;
               }
            }
         }

         return capability(SafeJson.objectField(this.snapshot, "capabilities"), capability);
      } else {
         return false;
      }
   }

   private static boolean capability(JsonObject weapon, String key) {
      try {
         Views.GunCaps gun = weapon.has("gun") && weapon.get("gun").isJsonObject()
            ? (Views.GunCaps)SafeJson.GSON.fromJson(weapon.get("gun"), Views.GunCaps.class)
            : null;
         Views.BladeCaps blade = weapon.has("slashBlade") && weapon.get("slashBlade").isJsonObject()
            ? (Views.BladeCaps)SafeJson.GSON.fromJson(weapon.get("slashBlade"), Views.BladeCaps.class)
            : null;
         return gun != null && gun.has(key) || blade != null && blade.has(key);
      } catch (IllegalStateException | JsonParseException var4) {
         return false;
      }
   }

   public static Parameter[] parameters(JsonObject info) {
      List<Parameter> valid = new ArrayList<>();

      for (JsonElement value : SafeJson.arrayField(info, "parameters")) {
         if (value.isJsonObject()) {
            try {
               Parameter raw = (Parameter)SafeJson.GSON.fromJson(value, Parameter.class);
               if (raw != null && raw.id != null && raw.type != null) {
                  Parameter parameter = new Parameter(raw.id, raw.type, raw.defaultValue, raw.min, raw.max, raw.values, raw.capability);
                  parameter.validate(SafeJson.copy(parameter.defaultValue));
                  valid.add(parameter);
               }
            } catch (IllegalArgumentException | IllegalStateException | JsonParseException var6) {
            }
         }
      }

      return valid.toArray(Parameter[]::new);
   }

   public boolean inventoryAllowed() {
      return this.ready() && SafeJson.bool(this.snapshot, "inventoryAllowed", false);
   }

   public boolean creativeAllowed() {
      return this.ready() && SafeJson.bool(this.snapshot, "creativeAllowed", false);
   }

   public ItemStack slot(int slot) {
      return this.slots != null && slot >= 0 && slot < this.slots.length && this.slots[slot] != null ? this.slots[slot] : ItemStack.EMPTY;
   }

   public ItemStack previewWeapon(String domain) {
      for (JsonElement item : this.array("editableWeapons")) {
         if (item.isJsonObject()) {
            JsonObject weapon = item.getAsJsonObject();
            String key = "GUN".equals(domain) ? "gun" : ("SLASHBLADE".equals(domain) ? "slashBlade" : "");
            if (!key.isEmpty() && weapon.has(key) && weapon.get(key).isJsonObject()) {
               return this.slot(SafeJson.integer(weapon, "slot", -1)).copy();
            }
         }
      }

      return ItemStack.EMPTY;
   }
}

