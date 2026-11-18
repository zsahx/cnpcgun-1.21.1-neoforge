package bincnpc.gun.common.script;

import bincnpc.gun.common.api.Result;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.entity.EntityNPCInterface;

public final class WeaponEvent extends NpcEvent {
   public final String name;
   public final String actionId;
   public final String weaponId;
   public final String state;
   public final IEntity target;
   public final Result result;

   public WeaponEvent(EntityNPCInterface npc, String name, String action, String weapon, LivingEntity target, Result result, String state) {
      super(npc.wrappedNPC);
      this.name = name;
      this.actionId = action;
      this.weaponId = weapon;
      this.target = target == null ? null : NpcAPI.Instance().getIEntity(target);
      this.result = result;
      this.state = state;
   }
}
