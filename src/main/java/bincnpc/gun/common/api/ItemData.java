package bincnpc.gun.common.api;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * ItemStack &lt;-&gt; NBT helpers.
 *
 * <p>1.20.5 changed item serialization to require a {@link HolderLookup.Provider} (data components are
 * registry backed). Every call site in this mod is server side (NPC combat, saved data, the server built
 * creative catalogue), so the running server's registry access is used.</p>
 */
public final class ItemData {
   private ItemData() {
   }

   public static HolderLookup.Provider registries() {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      return server != null ? server.registryAccess() : RegistryAccess.EMPTY;
   }

   public static CompoundTag save(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return new CompoundTag();
      }

      Tag tag = stack.save(registries(), new CompoundTag());
      return tag instanceof CompoundTag compound ? compound : new CompoundTag();
   }

   public static ItemStack parse(CompoundTag tag) {
      return tag == null || tag.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries(), tag);
   }

   public static String signature(ItemStack stack) {
      return save(stack).toString();
   }
}
