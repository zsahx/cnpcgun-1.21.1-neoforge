package bincnpc.gun.client;

import bincnpc.gun.client.animation.NpcAnimations;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only wiring. Loaded exclusively through the {@code FMLClientSetupEvent} listener that
 * {@code CNPCGun} registers when the current distribution is the client, so dedicated servers never
 * touch client classes.
 */
public final class ClientSetup {
   private ClientSetup() {
   }

   public static void onClientSetup(FMLClientSetupEvent event) {
      NeoForge.EVENT_BUS.register(NpcAnimations.class);
   }
}
