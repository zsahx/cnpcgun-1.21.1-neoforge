package bincnpc.gun.common.script;

import bincnpc.gun.common.api.Result;
import bincnpc.gun.common.combat.CombatContext;
import noppes.npcs.controllers.ScriptContainer;

public final class EventBridge {
   private EventBridge() {
   }

   public static void emit(CombatContext ctx, String name, String action, Result result) {
      if (ctx.npc.script.isEnabled()) {
         if (ctx.runtime.eventTick != ctx.now) {
            ctx.runtime.eventTick = ctx.now;
            ctx.runtime.eventsThisTick = 0;
         }

         if (ctx.runtime.eventsThisTick++ < 24 && ctx.runtime.events.size() < 32) {
            ctx.runtime
               .events
               .add(new WeaponEvent(ctx.npc, name, action, ctx.runtime.weaponId, ctx.validTarget() ? ctx.target : null, result, ctx.runtime.combatState));
         }
      }
   }

   public static void flush(CombatContext ctx) {
      if (!ctx.runtime.flushingEvents) {
         ctx.runtime.flushingEvents = true;

         try {
            int remaining = 16;

            while (!ctx.runtime.events.isEmpty() && remaining-- > 0) {
               WeaponEvent event = ctx.runtime.events.remove();

               for (ScriptContainer script : ctx.npc.script.getScripts()) {
                  script.run("cnpcgun", event);
                  script.run(callback(event.name), event);
               }
            }
         } finally {
            ctx.runtime.flushingEvents = false;
         }
      }
   }

   public static String callback(String event) {
      if (event.equals("gun.no_ammo")) {
         return "onNoAmmo";
      } else {
         StringBuilder name = new StringBuilder("on");

         for (String word : event.split("[._]")) {
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
         }

         return name.toString();
      }
   }
}
