package bincnpc.gun;

import bincnpc.gun.common.adapter.AdapterRegistry;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatDecisions;
import bincnpc.gun.common.combat.CombatMovement;
import bincnpc.gun.common.combat.CombatService;
import bincnpc.gun.common.network.GunNetwork;
import bincnpc.gun.common.npc.NpcData;
import bincnpc.gun.throwing.CollisionSnapshot;
import bincnpc.gun.throwing.ThrowFlights;
import bincnpc.gun.throwing.ThrowPlanner;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;

@Mod("cnpcgun")
public final class CNPCGun {
   public static final String MOD_ID = "cnpcgun";
   public static final String VERSION = "1.1.0";
   public static final String API_VERSION = "1";
   public static final Logger LOGGER = LogUtils.getLogger();

   public CNPCGun(IEventBus modBus, ModContainer container) {
      modBus.addListener(GunNetwork::register);
      if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
         modBus.addListener(bincnpc.gun.client.ClientSetup::onClientSetup);
      }

      NeoForge.EVENT_BUS.register(bincnpc.gun.common.combat.FriendlyFireEvents.class);
      NeoForge.EVENT_BUS.addListener(this::serverStarted);
      NeoForge.EVENT_BUS.addListener(this::tick);
      NeoForge.EVENT_BUS.addListener(this::hurt);
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::death);
      NeoForge.EVENT_BUS.addListener(this::serverStopping);
      NeoForge.EVENT_BUS.addListener(this::logout);
      NeoForge.EVENT_BUS.addListener(this::tracking);
      NeoForge.EVENT_BUS.addListener(this::serverTick);
      NeoForge.EVENT_BUS.addListener(this::serverTickPost);
   }

   private void logout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         GunNetwork.forget(player);
      }
   }

   private void tracking(StartTracking event) {
      if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof EntityNPCInterface npc && CombatService.validNpc(npc)) {
         CombatContext ctx = new CombatContext(npc);
         PacketDistributor.sendToPlayer(
            player,
            new GunNetwork.Animation(npc.getId(), npc.getUUID(), ctx.runtime.domain, GunNetwork.statePose(ctx), "", 25, ctx.runtime.aiming)
         );
      }
   }

   private void serverStarted(ServerStartedEvent event) {
      AdapterRegistry.initialize();
      GunNetwork.startWorker();
      CombatDecisions.start();
      ThrowPlanner.start();
      LOGGER.info("CNPC Gun {} initialized; adapters: {}", "1.1.0", AdapterRegistry.ids());
   }

   private void serverStopping(ServerStoppingEvent event) {
      for (ServerLevel level : event.getServer().getAllLevels()) {
         for (Entity entity : level.getAllEntities()) {
            if (entity instanceof EntityNPCInterface npc) {
               CombatService.data(npc).throwing.cancel();
               CombatMovement.release(new CombatContext(npc));
            }
         }
      }

      ThrowFlights.stop(event.getServer());
      ThrowPlanner.stop();
      CombatDecisions.stop();
      GunNetwork.stopWorker();
      AdapterRegistry.clear();
   }

   private void tick(EntityTickEvent.Post event) {
      if (event.getEntity() instanceof EntityNPCInterface npc && !npc.level().isClientSide) {
         CombatService.tick(npc);
         if (AdapterRegistry.ready() && CombatService.serverThread(npc) && CombatService.validNpc(npc)) {
            GunNetwork.summary(new CombatContext(npc));
         }
      }
   }

   private void serverTick(ServerTickEvent.Pre event) {
      CollisionSnapshot.beginTick();
   }

   private void serverTickPost(ServerTickEvent.Post event) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null) {
         ThrowFlights.tick(server);
      }
   }

   private void hurt(LivingIncomingDamageEvent event) {
      if (event.getEntity() instanceof EntityNPCInterface npc && !npc.level().isClientSide) {
         CombatService.data(npc).runtime.lastHurt = npc.level().getGameTime();
      }
   }

   private void death(LivingDeathEvent event) {
      if (!event.isCanceled() && !event.getEntity().level().isClientSide) {
         if (event.getEntity() instanceof EntityNPCInterface npc) {
            NpcData data = CombatService.data(npc);
            data.runtime.restoreSupplies = true;
            data.throwing.cancel();
            data.throwItems.died();
         }

         if (event.getSource().getEntity() instanceof EntityNPCInterface owner) {
            CombatService.rewardKill(owner, event.getEntity());
         }
      }
   }
}


