package bincnpc.gun.common.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent.Detonate;

/**
 * Cancels friendly fire between NPCs.
 *
 * <p>Registered from {@code CNPCGun} ({@code NeoForge.EVENT_BUS.register(FriendlyFireEvents.class)}).
 * 1.21.1 NeoForge replaced {@code LivingAttackEvent}/{@code LivingHurtEvent} with a single
 * {@link LivingIncomingDamageEvent}, which fires before damage is applied and is cancellable.</p>
 */
public final class FriendlyFireEvents {
   private FriendlyFireEvents() {
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void attack(LivingIncomingDamageEvent event) {
      if (FriendlyFire.blocksDamage(event.getSource(), event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void explosion(Detonate event) {
      if (!event.getLevel().isClientSide && event.getLevel().getServer() != null && event.getLevel().getServer().isSameThread()) {
         Explosion explosion = event.getExplosion();
         Entity source = explosion.getDirectSourceEntity();
         if (source == null) {
            source = explosion.getIndirectSourceEntity();
         }

         DamageSource damage = event.getLevel().damageSources().explosion(explosion);
         Entity attacker = source;
         event.getAffectedEntities()
            .removeIf(victim -> FriendlyFire.isProtected(attacker, victim) || FriendlyFire.blocksDamage(damage, victim));
      }
   }
}
