package bincnpc.gun.slashblade.strategy;

import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.slashblade.action.ActionRegistry;
import bincnpc.gun.slashblade.adapter.BladeAdapter;
import com.google.gson.JsonObject;

public final class NormalStrategy implements BladeStrategy {
   private final boolean beginner;

   public NormalStrategy(boolean beginner) {
      this.beginner = beginner;
   }

   @Override
   public BladeStrategy.Decision decide(CombatContext ctx) {
      if (ctx.runtime.adapter instanceof BladeAdapter blade) {
         String preferred = ctx.data.blade.selectEnabled(ctx, null);
         if (!preferred.isEmpty()) {
            return new BladeStrategy.Decision(preferred, new JsonObject(), ctx.config.integer("slashblade.attackInterval"));
         } else {
            if (!this.beginner) {
               String tag = ctx.now - ctx.runtime.lastHurt < 10L && ctx.config.bool("slashblade.counter") ? "counter" : (ctx.distance > 4.0 ? "dash" : "");
               if (!tag.isEmpty()) {
                  for (ActionRegistry.Definition def : ActionRegistry.INSTANCE.all()) {
                     if (def.adapterId.equals(blade.id()) && def.tags.contains(tag) && ctx.data.blade.ready(ctx, def.info.id)) {
                        return new BladeStrategy.Decision(def.info.id, new JsonObject(), 12);
                     }
                  }
               }
            }

            String normal = blade.nextNormal(ctx);
            return normal.isEmpty()
               ? null
               : new BladeStrategy.Decision(
                  normal,
                  new JsonObject(),
                  this.beginner ? Math.max(12, ctx.config.integer("slashblade.attackInterval")) : ctx.config.integer("slashblade.attackInterval")
               );
         }
      } else {
         return null;
      }
   }
}
