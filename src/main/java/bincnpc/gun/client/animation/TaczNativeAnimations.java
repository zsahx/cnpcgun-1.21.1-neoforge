package bincnpc.gun.client.animation;

import bincnpc.gun.mixin.compat.TaczAnimationAccess;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import noppes.npcs.entity.EntityNPCInterface;

final class TaczNativeAnimations {
   private TaczNativeAnimations() {
   }

   private static TaczNativeAnimations.Clip clip(EntityNPCInterface npc, String name) {
      GunDisplayInstance display = (GunDisplayInstance)TimelessAPI.getGunDisplay(npc.getMainHandItem()).orElse(null);
      if (display != null && display.getPlayerAnimator3rd() != null) {
         KeyframeAnimation data = ((TaczAnimationAccess)PlayerAnimatorAssetManager.get()).cnpcgun$animation(display.getPlayerAnimator3rd(), name).orElse(null);
         return data == null ? null : new TaczNativeAnimations.Clip(new KeyframeAnimationPlayer(data), Math.max(1, Math.min(2400, data.endTick)));
      } else {
         return null;
      }
   }

   static TaczNativeAnimations.Clip action(EntityNPCInterface npc, String pose, boolean aiming) {
      return switch (pose) {
         case "RELOAD" -> clip(npc, "reload_upper");
         case "FIRE" -> clip(npc, aiming ? "aim_fire_upper" : "normal_fire_upper");
         case "MELEE" -> clip(npc, "melee_upper");
         default -> null;
      };
   }

   static String loopKey(EntityNPCInterface npc, boolean aiming) {
      GunDisplayInstance display = (GunDisplayInstance)TimelessAPI.getGunDisplay(npc.getMainHandItem()).orElse(null);
      return display == null ? "" : display.getPlayerAnimator3rd() + ":" + loopName(npc, aiming);
   }

   private static String loopName(EntityNPCInterface npc, boolean aiming) {
      return aiming ? "aim_upper" : (npc.getDeltaMovement().horizontalDistanceSqr() > 0.0025 ? "walk_upper" : "hold_upper");
   }

   static IAnimation loop(EntityNPCInterface npc, boolean aiming) {
      TaczNativeAnimations.Clip clip = clip(npc, loopName(npc, aiming));
      return clip == null ? null : clip.animation();
   }

   record Clip(IAnimation animation, int duration) {
   }
}
