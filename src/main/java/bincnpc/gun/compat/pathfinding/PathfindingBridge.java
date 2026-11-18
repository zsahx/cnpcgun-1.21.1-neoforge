package bincnpc.gun.compat.pathfinding;

import bincnpc.gun.common.combat.CombatContext;

/**
 * Bridge to the "NPC Pathfinding" addon used by the 1.20.1 build to hand path decisions to that mod.
 * No 1.21.1 build of the addon exists, so this bridge is a no-op: the NPC falls back to CustomNPCs'
 * own navigation, which is what the mod already does when the addon is absent.
 */
public final class PathfindingBridge {
   private PathfindingBridge() {
   }

   public static void prepare(CombatContext ctx, boolean enhanced) {
   }
}
