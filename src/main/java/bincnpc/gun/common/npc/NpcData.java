package bincnpc.gun.common.npc;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.NpcConfig;
import bincnpc.gun.gun.ammo.AmmoController;
import bincnpc.gun.gun.controller.GunController;
import bincnpc.gun.gun.switching.Armory;
import bincnpc.gun.slashblade.controller.BladeController;
import bincnpc.gun.throwing.ThrowController;
import bincnpc.gun.throwing.ThrowInventory;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;

public final class NpcData {
   public final NpcConfig config = new NpcConfig();
   public final Armory armory = new Armory();
   public final AmmoController ammo = new AmmoController();
   public final GunController gun = new GunController();
   public final BladeController blade = new BladeController();
   public final ThrowInventory throwItems = new ThrowInventory();
   public final ThrowController throwing = new ThrowController();
   public RuntimeState runtime = new RuntimeState();
   private CompoundTag preserved = new CompoundTag();

   public void read(CompoundTag tag) {
      this.preserved = tag.copy();
      this.config.replace(new JsonObject());
      String raw = tag.getString("Config");
      if (!raw.isEmpty()) {
         try {
            this.config.replace(SafeJson.persistedObject(raw));
            int schema = this.config.snapshot().get("schemaVersion").getAsInt();
            if (schema != 1) {
               CNPCGun.LOGGER.warn("CNPC Gun configuration schema {} retained; known fields use validated defaults", schema);
            }
         } catch (IllegalStateException | IllegalArgumentException var4) {
            this.preserved.putString("UnparsedConfig", raw);
            CNPCGun.LOGGER.warn("Invalid CNPC Gun configuration retained for recovery: {}", var4.getMessage());
         }
      }

      this.armory.load(tag.getCompound("Armory"));
      this.ammo.load(tag.getCompound("Reserve"));
      this.throwItems.load(tag.getCompound("Throwables"));
      this.throwing.cancel();
      this.blade.reset();
      this.runtime = new RuntimeState();
      this.runtime.restoreSupplies = !tag.contains("Reserve", 10);
      this.runtime.movement.meleeFallback = tag.getBoolean("MeleeFallback");
      if (tag.contains("NavigationModeBeforeCombat", 3)) {
         this.runtime.movement.priorNavigationMode = Math.max(-1, Math.min(7, tag.getInt("NavigationModeBeforeCombat")));
      }
   }

   public CompoundTag write() {
      CompoundTag tag = this.preserved.copy();
      tag.putString("Config", SafeJson.GSON.toJson(this.config.snapshot()));
      tag.put("Armory", this.armory.save());
      tag.put("Reserve", this.ammo.save());
      tag.put("Throwables", this.throwItems.save());
      tag.putBoolean("MeleeFallback", this.runtime.movement.meleeFallback);
      if (this.runtime.movement.priorNavigationMode >= 0) {
         tag.putInt("NavigationModeBeforeCombat", this.runtime.movement.priorNavigationMode);
      } else {
         tag.remove("NavigationModeBeforeCombat");
      }

      tag.putInt("SchemaVersion", 1);
      return tag;
   }
}
