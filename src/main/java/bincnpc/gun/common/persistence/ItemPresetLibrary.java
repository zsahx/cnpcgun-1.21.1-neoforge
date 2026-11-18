package bincnpc.gun.common.persistence;


import bincnpc.gun.common.api.ItemData;
import bincnpc.gun.common.api.Result;
import bincnpc.gun.throwing.ThrowAdapters;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import noppes.npcs.CustomItems;

public final class ItemPresetLibrary extends SavedData {
   private final Map<String, ItemPresetLibrary.Entry> entries = new LinkedHashMap<>();

   public static ItemPresetLibrary get(ServerPlayer player) {
      if (player != null && player.getServer() != null) {
         return player.getServer().overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(ItemPresetLibrary::new, ItemPresetLibrary::load, DataFixTypes.LEVEL), "cnpcgun_item_presets"
         );
      } else {
         throw new IllegalArgumentException("item presets require a server player");
      }
   }

   public static ItemPresetLibrary load(CompoundTag tag, HolderLookup.Provider registries) {
      ItemPresetLibrary library = new ItemPresetLibrary();
      if (tag == null) {
         return library;
      } else {
         for (Tag raw : tag.getList("Items", 10)) {
            if (library.entries.size() >= 256) {
               break;
            }

            CompoundTag item = (CompoundTag)raw;
            if (item.hasUUID("Owner") && item.getString("Id").length() <= 64 && item.getString("Title").length() <= 48) {
               ItemStack stack = ItemStack.parseOptional(registries, item.getCompound("Item"));
               if (!stack.isEmpty() && ItemData.save(stack).toString().length() <= 32768) {
                  stack.setCount(1);
                  library.entries.put(item.getString("Id"), new ItemPresetLibrary.Entry(item.getUUID("Owner"), item.getString("Title"), stack));
               }
            }
         }

         return library;
      }
   }

   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      ListTag list = new ListTag();
      this.entries.forEach((id, entry) -> {
         CompoundTag item = new CompoundTag();
         item.putString("Id", id);
         item.putUUID("Owner", entry.owner);
         item.putString("Title", entry.title);
         item.put("Item", ItemData.save(entry.item));
         list.add(item);
      });
      tag.put("Items", list);
      return tag;
   }

   public JsonArray snapshot(UUID owner) {
      JsonArray list = new JsonArray();
      this.entries.forEach((id, entry) -> {
         if (entry.owner.equals(owner)) {
            JsonObject item = new JsonObject();
            item.addProperty("id", id);
            item.addProperty("title", entry.title);
            item.addProperty("itemName", entry.item.getHoverName().getString());
            item.addProperty("throwable", ThrowAdapters.find(entry.item) != null);
            item.addProperty("stackLimit", Math.max(1, Math.min(64, entry.item.getMaxStackSize())));
            list.add(item);
         }
      });
      return list;
   }

   public Result create(ServerPlayer player, String title, ItemStack stack) {
      if (player != null && title != null && stack != null) {
         title = title.trim();
         if (!player.isCreative()) {
            return Result.fail("PERMISSION_DENIED");
         } else if (!title.isEmpty()
            && title.length() <= 48
            && !title.chars().anyMatch(Character::isISOControl)
            && !stack.isEmpty()
            && !stack.is(CustomItems.wand)
            && ItemData.save(stack).toString().length() <= 32768) {
            long owned = this.entries.values().stream().filter(entry -> entry.owner.equals(player.getUUID())).count();
            if (owned < 64L && this.entries.size() < 256) {
               String id = UUID.randomUUID().toString();
               ItemStack copy = stack.copy();
               copy.setCount(1);
               this.entries.put(id, new ItemPresetLibrary.Entry(player.getUUID(), title, copy));
               this.setDirty();
               return Result.success(id);
            } else {
               return Result.fail("CONFLICT");
            }
         } else {
            return Result.fail("INVALID_ARGUMENT");
         }
      } else {
         return Result.fail("INVALID_ARGUMENT");
      }
   }

   public ItemStack find(UUID owner, String id) {
      ItemPresetLibrary.Entry entry = this.entries.get(id);
      return entry != null && entry.owner.equals(owner) ? entry.item.copy() : ItemStack.EMPTY;
   }

   public Result remove(UUID owner, String id) {
      ItemPresetLibrary.Entry entry = this.entries.get(id);
      if (entry != null && entry.owner.equals(owner)) {
         this.entries.remove(id);
         this.setDirty();
         return Result.success();
      } else {
         return Result.fail("NOT_FOUND");
      }
   }

   private record Entry(UUID owner, String title, ItemStack item) {
   }
}
