package bincnpc.gun.common.network;


import bincnpc.gun.common.api.ItemData;
import bincnpc.gun.common.api.SafeJson;
import bincnpc.gun.common.config.Parameter;
import bincnpc.gun.throwing.ThrowAdapters;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab.Type;
import noppes.npcs.CustomItems;

public final class CreativeCatalogue {
   private static final String THROWABLES = "cnpcgun:throwable_registry";

   private CreativeCatalogue() {
   }

   public static synchronized List<CreativeCatalogue.Tab> tabs(FeatureFlagSet features, boolean operator, Provider registries) {
      CreativeModeTabs.tryRebuildTabContents(features, operator, registries);
      List<CreativeCatalogue.Tab> result = new ArrayList<>();

      for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
         if (tab.getType() != Type.INVENTORY && tab.getType() != Type.HOTBAR && !tab.getDisplayItems().isEmpty()) {
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id != null) {
               result.add(
                  new CreativeCatalogue.Tab(id.toString(), tab.getDisplayName().getString(), tab.getDisplayItems().stream().<ItemStack>map(ItemStack::copy).toList())
               );
            }
         }
      }

      return result;
   }

   public static synchronized List<CreativeCatalogue.Tab> throwingTabs(FeatureFlagSet features, boolean operator, Provider registries) {
      List<CreativeCatalogue.Tab> nativeTabs = tabs(features, operator, registries);
      List<CreativeCatalogue.Tab> result = new ArrayList<>();
      Set<CompoundTag> shown = new HashSet<>();

      for (CreativeCatalogue.Tab tab : nativeTabs) {
         boolean compatible = false;

         for (ItemStack item : tab.items()) {
            if (ThrowAdapters.find(item.copy()) != null) {
               compatible = true;
               shown.add(ItemData.save(item.copyWithCount(1)));
            }
         }

         if (compatible) {
            result.add(tab);
         }
      }

      Map<CompoundTag, ItemStack> missing = new LinkedHashMap<>();

      for (ItemStack itemx : ThrowAdapters.catalogue()) {
         if (itemx.isItemEnabled(features)) {
            ItemStack copy = itemx.copyWithCount(1);
            CompoundTag key = ItemData.save(copy);
            if (!shown.contains(key) && key.toString().length() <= 32768) {
               missing.putIfAbsent(key, copy);
            }
         }
      }

      if (!missing.isEmpty()) {
         List<ItemStack> items = new ArrayList<>(missing.values());
         items.sort(Comparator.comparing(itemxx -> ItemData.save(itemxx).toString()));
         result.add(
            new CreativeCatalogue.Tab("cnpcgun:throwable_registry", Component.translatable("cnpcgun.editor.throwRegistry").getString(), List.copyOf(items))
         );
      }

      return List.copyOf(result);
   }

   public static int fingerprint(ItemStack stack) {
      CompoundTag saved = ItemData.save(stack).copy();
      if (superbGun(stack.getItem()) && saved.contains("tag", 10)) {
         CompoundTag tag = saved.getCompound("tag");
         if (tag.contains("GunData", 10)) {
            tag.getCompound("GunData").remove("UUID");
         }

         for (String key : new String[]{"GunData", "Perks", "Attachments"}) {
            if (tag.contains(key, 10) && tag.getCompound(key).isEmpty()) {
               tag.remove(key);
            }
         }

         if (tag.isEmpty()) {
            saved.remove("tag");
         }
      }

      return saved.hashCode();
   }

   private static boolean superbGun(Item item) {
      for (Class<?> type = item.getClass(); type != null; type = type.getSuperclass()) {
         if (type.getName().equals("com.atsuishio.superbwarfare.item.gun.GunItem")) {
            return true;
         }
      }

      return false;
   }

   public static synchronized ItemStack resolve(ServerPlayer player, JsonObject input) {
      if (player != null && input != null && player.isCreative()) {
         String tabId = SafeJson.string(input, "tab", "");
         ResourceLocation id = ResourceLocation.tryParse(tabId);
         if (id == null) {
            throw new IllegalArgumentException("tab");
         } else {
            int index = Parameter.integer("itemIndex", 0, 0, 100000).validate(input.get("itemIndex")).getAsInt();
            int expected = Parameter.integer("fingerprint", 0, Integer.MIN_VALUE, Integer.MAX_VALUE).validate(input.get("fingerprint")).getAsInt();
            if ("cnpcgun:throwable_registry".equals(tabId)) {
               int slot = Parameter.integer("slot", 0, 6, 8).validate(input.get("slot")).getAsInt();
               CreativeCatalogue.Tab tab = throwingTabs(player.level().enabledFeatures(), player.hasPermissions(2), player.level().registryAccess())
                  .stream()
                  .filter(entry -> entry.id().equals("cnpcgun:throwable_registry"))
                  .findFirst()
                  .orElseThrow(() -> new CreativeCatalogue.Changed("throwable catalogue unavailable"));
               if (index >= tab.items().size()) {
                  throw new CreativeCatalogue.Changed("throwable catalogue index changed");
               } else {
                  return checked(player, tab.items().get(index), expected);
               }
            } else {
               CreativeModeTabs.tryRebuildTabContents(player.level().enabledFeatures(), player.hasPermissions(2), player.level().registryAccess());
               CreativeModeTab tab = (CreativeModeTab)BuiltInRegistries.CREATIVE_MODE_TAB.getOptional(id).orElseThrow(() -> new IllegalArgumentException("tab"));
               if (tab.getType() != Type.INVENTORY && tab.getType() != Type.HOTBAR) {
                  int cursor = 0;

                  for (ItemStack item : tab.getDisplayItems()) {
                     if (cursor++ == index) {
                        return checked(player, item, expected);
                     }
                  }

                  throw new CreativeCatalogue.Changed("creative catalogue index changed");
               } else {
                  throw new IllegalArgumentException("tab");
               }
            }
         }
      } else {
         throw new IllegalArgumentException("creative editor required");
      }
   }

   private static ItemStack checked(ServerPlayer player, ItemStack item, int expected) {
      if (item != null
         && !item.isEmpty()
         && !item.is(CustomItems.wand)
         && item.isItemEnabled(player.level().enabledFeatures())
         && ItemData.save(item).toString().length() <= 32768) {
         int actual = fingerprint(item);
         if (actual != expected) {
            throw new CreativeCatalogue.Changed(
               "creative template changed for " + BuiltInRegistries.ITEM.getKey(item.getItem()) + " (expected " + expected + ", actual " + actual + ")"
            );
         } else {
            return item.copyWithCount(1);
         }
      } else {
         throw new IllegalArgumentException("unavailable catalogue item");
      }
   }

   public static final class Changed extends IllegalArgumentException {
      private Changed(String reason) {
         super(reason);
      }
   }

   public record Tab(String id, String title, List<ItemStack> items) {
   }
}
