package bincnpc.gun.common.persistence;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.combat.CombatService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * NPC data persistence.
 *
 * <p>The 1.20.1 build delegated storage of the (potentially large) gun configuration to the
 * CNPC+ external store so that cloned NPCs could share a single copy. CNPC+ has no 1.21.1 build
 * yet, so the data is stored inline in the NPC's own NBT under {@link #KEY} (which the mixins
 * already read and write) and sharing is handled by copying the compound when a clone is made.</p>
 */
public final class GunPersistence {
   public static final String KEY = "CNPCGunData";
   private static final String SOURCE_KEY = "CNPCGunSource";

   private GunPersistence() {
   }

   private static void requireData(EntityNPCInterface npc, CompoundTag tag) {
      if (npc == null || tag == null) {
         throw new IllegalArgumentException("NPC persistence requires an NPC and an NBT compound");
      }
   }

   public static void load(EntityNPCInterface npc, CompoundTag tag) {
      requireData(npc, tag);
      if (!npc.level().isClientSide) {
         if (tag.contains(KEY, 10)) {
            CombatService.data(npc).read(tag.getCompound(KEY));
         }
      }
   }

   public static void save(EntityNPCInterface npc, CompoundTag tag) {
      requireData(npc, tag);
      if (npc.level().isClientSide) {
         tag.putUUID(SOURCE_KEY, npc.getUUID());
      } else {
         tag.put(KEY, CombatService.data(npc).write());
      }
   }

   public static void memoryWrite(EntityNPCInterface npc, CompoundTag tag) {
      if (tag != null) {
         requireData(npc, tag);
         tag.put(KEY, CombatService.data(npc).write());
      }
   }

   public static void memoryRead(EntityNPCInterface npc, CompoundTag tag) {
      if (tag != null) {
         requireData(npc, tag);
         if (tag.contains(KEY, 10)) {
            CombatService.data(npc).read(tag.getCompound(KEY));
         }
      }
   }

   public static boolean portable(CompoundTag tag) {
      if (tag == null) {
         return false;
      } else {
         if (tag.hasUUID(SOURCE_KEY)) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            boolean restored = false;
            if (server != null && server.isSameThread()) {
               for (ServerLevel level : server.getAllLevels()) {
                  if (level.getEntity(tag.getUUID(SOURCE_KEY)) instanceof EntityNPCInterface npc && !npc.isRemoved()) {
                     memoryWrite(npc, tag);
                     restored = true;
                     break;
                  }
               }
            }

            if (!restored) {
               CNPCGun.LOGGER.warn("CNPC Gun clone source {} is unavailable; source reference retained", tag.getUUID(SOURCE_KEY));
               return false;
            }
         }

         tag.remove(SOURCE_KEY);
         return true;
      }
   }
}
