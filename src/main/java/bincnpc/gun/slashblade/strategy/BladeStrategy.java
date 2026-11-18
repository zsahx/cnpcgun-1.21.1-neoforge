package bincnpc.gun.slashblade.strategy;

import bincnpc.gun.common.combat.CombatContext;
import com.google.gson.JsonObject;

public interface BladeStrategy {
   BladeStrategy.Decision decide(CombatContext var1);

   public record Decision(String action, JsonObject parameters, int delay) {
   }
}
