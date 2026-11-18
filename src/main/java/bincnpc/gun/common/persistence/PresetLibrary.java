package bincnpc.gun.common.persistence;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.combat.CombatContext;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class PresetLibrary extends SavedData {
   private final Map<String, JsonObject> templates = new LinkedHashMap<>();
   private final Map<String, String> unparsed = new LinkedHashMap<>();

   public static PresetLibrary get(CombatContext ctx) {
      return ctx.npc.getServer().overworld().getDataStorage().computeIfAbsent(
         new SavedData.Factory<>(PresetLibrary::new, PresetLibrary::load, DataFixTypes.LEVEL), "cnpcgun_presets"
      );
   }

   public static PresetLibrary load(CompoundTag tag, HolderLookup.Provider registries) {
      PresetLibrary library = new PresetLibrary();
      CompoundTag entries = tag.getCompound("Templates");

      for (String id : entries.getAllKeys()) {
         if (library.templates.size() < 64) {
            try {
               library.templates.put(id, SafeJson.persistedObject(entries.getString(id)));
            } catch (IllegalStateException | IllegalArgumentException var6) {
               library.unparsed.put(id, entries.getString(id));
               CNPCGun.LOGGER.warn("Preset {} retained as unavailable: {}", id, var6.getMessage());
            }
         }
      }

      return library;
   }

   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      CompoundTag entries = new CompoundTag();
      this.templates.forEach((id, config) -> entries.putString(id, SafeJson.GSON.toJson(config)));
      this.unparsed.forEach(entries::putString);
      tag.put("Templates", entries);
      return tag;
   }

   public String[] ids() {
      return this.templates.keySet().toArray(String[]::new);
   }

   public JsonObject find(String id) {
      return this.templates.containsKey(id) ? this.templates.get(id).deepCopy() : null;
   }

   public Result remove(String id) {
      if (!this.templates.containsKey(id)) {
         return Result.fail("NOT_FOUND");
      } else {
         this.templates.remove(id);
         this.setDirty();
         return Result.success();
      }
   }

   public Result saveAs(String id, JsonObject config) {
      if (!id.matches("user:[a-z0-9_.-]{1,48}")) {
         return Result.fail("INVALID_ARGUMENT");
      } else if (!this.templates.containsKey(id) && !this.unparsed.containsKey(id) && this.templates.size() + this.unparsed.size() < 64) {
         JsonObject saved = config.deepCopy();
         saved.remove("presetCopies");
         this.templates.put(id, SafeJson.persistedObject(SafeJson.GSON.toJson(saved)));
         this.setDirty();
         return Result.success(id);
      } else {
         return Result.fail("CONFLICT");
      }
   }
}
