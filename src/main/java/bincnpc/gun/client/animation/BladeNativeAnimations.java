package bincnpc.gun.client.animation;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Native SlashBlade (VMD) animation bridge.
 *
 * <p>The 1.20.1 build played SlashBlade's own VMD animations for NPC blade combo actions. The 1.21.1
 * SlashBlade build ("SlashBlade: Resharped") changed this API, so the bridge is disabled in this port:
 * blade combos still work logically, they just do not drive the SlashBlade animation library.</p>
 */
final class BladeNativeAnimations {
   private BladeNativeAnimations() {
   }

   static IAnimation create(String action) {
      return null;
   }

   static void synchronizeBlade(EntityNPCInterface npc, String action) {
   }
}
