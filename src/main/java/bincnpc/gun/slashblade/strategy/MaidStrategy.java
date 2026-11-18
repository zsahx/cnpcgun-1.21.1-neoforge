package bincnpc.gun.slashblade.strategy;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatDecisions;
import bincnpc.gun.common.combat.CombatMovement;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.slashblade.action.ActionConfig;
import bincnpc.gun.slashblade.action.ActionRegistry;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class MaidStrategy implements BladeStrategy {
   @Override
   public BladeStrategy.Decision decide(CombatContext ctx) {
      if (!(ctx.runtime.adapter instanceof BladeAdapter blade && ctx.validTarget())) {
         return null;
      } else if (ctx.now < ctx.runtime.maid.escapeUntil) {
         if (ctx.npc.getSensing().hasLineOfSight(ctx.target)) {
            this.mirage(ctx);
         }

         double dx = ctx.npc.getX() - ctx.target.getX();
         double dz = ctx.npc.getZ() - ctx.target.getZ();
         double length = Math.sqrt(dx * dx + dz * dz);
         if (length < 0.01) {
            dx = 1.0;
            dz = 0.0;
            length = 1.0;
         }

         ctx.npc.getNavigation().moveTo(ctx.npc.getX() + dx / length * 6.0, ctx.npc.getY(), ctx.npc.getZ() + dz / length * 6.0, 1.3);
         return null;
      } else if (!ctx.npc.getSensing().hasLineOfSight(ctx.target)) {
         ctx.npc.getNavigation().moveTo(ctx.target, 1.15);
         return null;
      } else {
         this.mirage(ctx);
         CombatDecisions.Advice advice = CombatService.advice(ctx);
         double reach = CombatMovement.meleeReach(ctx);
         JsonObject phase = ctx.data.blade.state(ctx);
         boolean interrupt = flag(phase, "canInterrupt");
         boolean perfect = flag(phase, "perfectCharge");
         boolean idle = flag(phase, "idle");
         if (idle && ctx.now >= ctx.runtime.maid.specialCooldown) {
            ctx.runtime.maid.specialBurst = 0;
         }

         if (!ctx.npc.onGround() && ctx.npc.getY() - ctx.target.getY() > 5.0 && interrupt) {
            String fall = this.tagged(ctx, "fall", "MOVEMENT", "COMBO_ATTACK");
            if (!fall.isEmpty()) {
               return decision(fall);
            }
         }

         if (interrupt && ctx.now - ctx.runtime.lastHurt <= 8L && ctx.config.bool("slashblade.counter")) {
            String counter = this.tagged(ctx, "counter", "COUNTER");
            if (!counter.isEmpty()) {
               return decision(counter);
            }
         }

         boolean release = perfect || !idle && interrupt && phase.has("elapsed") && phase.get("elapsed").getAsLong() >= 4L;
         if (release && ctx.config.bool("slashblade.useSpecialAttacks")) {
            boolean pressure = advice != null ? advice.pressure() >= 0.6 : ctx.targets().size() >= 3 || ctx.now - ctx.runtime.lastHurt <= 8L;
            String superArt = ctx.now < ctx.runtime.maid.superCooldown || ctx.targets().size() < 2 && !pressure
               ? ""
               : this.tagged(ctx, "super", "SPECIAL_ATTACK");
            if (!superArt.isEmpty()) {
               return decision(superArt);
            }

            if (ctx.now >= ctx.runtime.maid.specialCooldown) {
               String special = this.special(ctx, phase);
               if (!special.isEmpty()) {
                  return decision(special);
               }
            }
         }

         if (interrupt && ctx.distance > 12.0) {
            String trick = this.tagged(ctx, "teleport", "MOVEMENT");
            if (!trick.isEmpty()) {
               return decision(trick);
            }
         }

         String current = phase.has("combo") ? phase.get("combo").getAsString() : "";
         if (interrupt && current.equals("slashblade:rapid_slash") && ctx.distance <= reach) {
            String rising = this.nativeAction(ctx, "rising_star");
            if (!rising.isEmpty()) {
               return decision(rising);
            }
         }

         if (idle && ctx.distance > reach) {
            String dash = this.nativeAction(ctx, "rapid_slash");
            if (!dash.isEmpty()) {
               return decision(dash);
            }
         }

         if (idle && ctx.distance <= reach) {
            if (!ctx.npc.onGround()) {
               String air = this.nativeAction(ctx, "aerial_rave_a1");
               if (!air.isEmpty()) {
                  return decision(air);
               }
            } else if (!ctx.target.onGround()) {
               String launcher = this.nativeAction(ctx, "upperslash");
               if (!launcher.isEmpty()) {
                  return decision(launcher);
               }
            } else {
               List<String> roots = List.of("combo_a1", "combo_b1", "combo_c");
               int start = ctx.targets().size() >= 3 ? 1 : ctx.runtime.maid.comboCycle;

               for (int offset = 0; offset < roots.size(); offset++) {
                  String root = this.nativeAction(ctx, roots.get((start + offset) % roots.size()));
                  if (!root.isEmpty()) {
                     return decision(root);
                  }
               }
            }
         }

         String action = blade.nextNormal(ctx);
         if (!action.isEmpty() && ctx.data.blade.eligible(ctx, action)) {
            return decision(action);
         } else {
            if (idle) {
               String start = this.tagged(ctx, ctx.npc.onGround() ? "ground" : "air", "NORMAL_ATTACK", "COMBO_ATTACK");
               if (!start.isEmpty()) {
                  return decision(start);
               }
            }

            return null;
         }
      }
   }

   private static BladeStrategy.Decision decision(String id) {
      return new BladeStrategy.Decision(id, new JsonObject(), 4);
   }

   private static boolean flag(JsonObject phase, String key) {
      return phase.has(key) && phase.get(key).getAsBoolean();
   }

   private String nativeAction(CombatContext ctx, String path) {
      for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) {
         if (def.backend.equals("combo:slashblade:" + path) && ctx.data.blade.eligible(ctx, def.info.id)) {
            return def.info.id;
         }
      }

      return "";
   }

   private String tagged(CombatContext ctx, String tag, String... types) {
      for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) {
         if (def.tags.contains(tag) && Arrays.asList(types).contains(def.info.type) && ctx.data.blade.eligible(ctx, def.info.id)) {
            return def.info.id;
         }
      }

      return "";
   }

   private String special(CombatContext ctx, JsonObject phase) {
      String nativeId = phase.has("specialAttack") ? phase.get("specialAttack").getAsString() : "";
      if (ctx.runtime.maid.specialBurst > 0 && ctx.data.blade.eligible(ctx, ctx.runtime.maid.specialAction)) {
         return ctx.runtime.maid.specialAction;
      } else {
         List<ActionRegistry.Definition> choices = new ArrayList<>();

         for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) {
            if (def.info.type.equals("SPECIAL_ATTACK") && !def.tags.contains("super") && ctx.data.blade.autoEnabled(ctx, def.info.id)) {
               choices.add(def);
            }
         }

         choices.sort(
            Comparator.<ActionRegistry.Definition>comparingInt(
                  defx -> defx.info.id.equals(nativeId) ? 10000 : ActionConfig.value(Presets.action(ctx, defx), "priority").getAsInt()
               )
               .reversed()
               .thenComparing(defx -> defx.info.id)
         );

         for (int offset = 0; offset < choices.size(); offset++) {
            ActionRegistry.Definition defx = choices.get(Math.floorMod(ctx.runtime.maid.specialCursor + offset, choices.size()));
            if (ctx.data.blade.eligible(ctx, defx.info.id)) {
               return defx.info.id;
            }
         }

         return "";
      }
   }

   private void mirage(CombatContext ctx) {
      MaidCombatState state = ctx.runtime.maid;
      if (ctx.config.bool("slashblade.usePhantoms") && ctx.now >= state.nextPhantom) {
         state.nextPhantom = ctx.now + 4L;
         if (state.spiralReleaseAt > 0L && ctx.now >= state.spiralReleaseAt) {
            String fire = "slashblade:slashblade/phantom/spiral_fire";
            if (ctx.data.blade.eligible(ctx, fire) && ctx.data.blade.execute(ctx, fire, new JsonObject()).ok) {
               state.spiralReleaseAt = 0L;
            }
         }

         for (String kind : List.of("rain", "blistering", "spiral", "storm", "basic")) {
            if (!kind.equals("basic") || !(Math.abs(ctx.target.getY() - ctx.npc.getY()) >= 7.0)) {
               String action = this.tagged(ctx, kind, "PHANTOM_SWORD");
               if (!action.isEmpty() && ctx.data.blade.execute(ctx, action, new JsonObject()).ok) {
                  if (kind.equals("spiral")) {
                     state.spiralReleaseAt = ctx.now + 8L;
                  }

                  return;
               }
            }
         }
      }
   }
}
