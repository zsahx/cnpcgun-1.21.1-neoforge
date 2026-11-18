package bincnpc.gun.throwing;

import bincnpc.gun.CNPCGun;
import bincnpc.gun.common.combat.CombatContext;
import bincnpc.gun.common.combat.CombatService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

public final class ThrowFlights {
   public static final int MAX_FLIGHTS = 192;
   public static final int FAILURE_TICKS = 40;
   private static final Map<UUID, ThrowFlights.Flight> flights = new LinkedHashMap<>();
   private static final String OWNER_TAG = "CNPCGunThrowOwner";

   private ThrowFlights() {
   }

   public static boolean hasRoom() {
      return flights.size() < 192;
   }

   public static void mark(CombatContext ctx, Projectile projectile) {
      if (CombatService.serverThread(ctx.npc) && projectile.level() == ctx.npc.level()) {
         projectile.getPersistentData().putUUID("CNPCGunThrowOwner", ctx.npc.getUUID());
      }
   }

   public static boolean register(
      CombatContext ctx, Projectile projectile, ThrowAdapter adapter, ThrowAdapter.Profile profile, Ballistics.Candidate candidate, UUID target
   ) {
      if (CombatService.serverThread(ctx.npc) && !projectile.isRemoved() && projectile.level() == ctx.npc.level() && hasRoom()) {
         projectile.getPersistentData().putUUID("CNPCGunThrowOwner", ctx.npc.getUUID());
         flights.put(projectile.getUUID(), new ThrowFlights.Flight(ctx, projectile, adapter, profile, candidate, target));
         return true;
      } else {
         return false;
      }
   }

   public static boolean owned(Entity projectile, Entity owner) {
      return projectile instanceof Projectile
         && owner != null
         && projectile.getPersistentData().hasUUID("CNPCGunThrowOwner")
         && owner.getUUID().equals(projectile.getPersistentData().getUUID("CNPCGunThrowOwner"));
   }

   public static void tick(MinecraftServer server) {
      if (server.isSameThread()) {
         for (ThrowFlights.Flight flight : new ArrayList<>(flights.values())) {
            ServerLevel level = server.getLevel(flight.dimension);
            Entity entity = level == null ? null : level.getEntity(flight.id);
            if (entity instanceof Projectile projectile && !entity.isRemoved()) {
               long now = level.getGameTime();

               try {
                  if (now >= flight.started && now - flight.started <= Math.min(1200, flight.profile.life() + 40)) {
                     if (flight.mode == Ballistics.Trajectory.TRACKING && !flight.nativePhysics) {
                        tracking(level, flight, projectile, now);
                     } else {
                        if (!flight.nativePhysics
                           && flight.mode == Ballistics.Trajectory.STRAIGHT
                           && (flight.profile.blastRadius() > 0.0 || flight.adapter.settleAtLanding(projectile))
                           && (projectile.tickCount >= Math.ceil(flight.travel) || projectile.onGround())) {
                           handover(flight, projectile);
                        }

                        if ((flight.mode == Ballistics.Trajectory.CURVE || flight.nativePhysics) && flight.profile.customGravity()) {
                           projectile.setDeltaMovement(projectile.getDeltaMovement().add(0.0, -flight.profile.gravity(), 0.0));
                        }
                     }

                     if (projectile.isRemoved()) {
                        remove(flight);
                     } else {
                        boolean loaded = true;

                        try {
                           for (Ballistics.Cell cell : Ballistics.cells(
                              CollisionSnapshot.vector(projectile.position()),
                              CollisionSnapshot.vector(projectile.position().add(projectile.getDeltaMovement())),
                              flight.profile.radius(),
                              1024
                           )) {
                              if (!CollisionSnapshot.loadedShape(level, new BlockPos(cell.x(), cell.y(), cell.z()))) {
                                 loaded = false;
                                 break;
                              }
                           }
                        } catch (IllegalArgumentException var11) {
                           loaded = false;
                        }

                        if (!loaded) {
                           projectile.discard();
                           remove(flight);
                        } else if (!projectile.isRemoved() && now >= flight.nextSync) {
                           flight.nextSync = now + 5L;
                           flight.adapter.sync(projectile);
                        }
                     }
                  } else {
                     detonate(flight, projectile);
                  }
               } catch (LinkageError | RuntimeException var12) {
                  CNPCGun.LOGGER.warn("NPC throwable flight ended after adapter failure: {}", var12.toString());
                  projectile.discard();
                  remove(flight);
               }
            } else {
               remove(flight);
            }
         }
      }
   }

   private static void tracking(ServerLevel level, ThrowFlights.Flight f, Projectile projectile, long now) {
      Entity owner = level.getEntity(f.owner);
      Entity target = f.target == null ? null : level.getEntity(f.target);
      if (owner instanceof EntityNPCInterface npc
         && target instanceof LivingEntity living
         && CombatService.validTarget(npc, living)
         && !(projectile.distanceToSqr(living) > 16384.0)) {
         Vec3 goal = living.getBoundingBox().getCenter();
         boolean explosive = f.profile.blastRadius() > 0.0 || f.adapter.settleAtLanding(projectile);
         if (projectile.getBoundingBox().inflate(explosive ? 0.75 : 0.15).intersects(living.getBoundingBox())) {
            handover(f, projectile);
         } else {
            if (now >= f.nextRay) {
               f.nextRay = now + 4L;
               f.direct = CollisionSnapshot.clear(level, projectile, projectile.position(), goal);
               if (f.direct) {
                  f.cancelRoute();
               }
            }

            if (f.direct) {
               Vec3 movement = goal.subtract(projectile.position());
               double speed = Math.min(f.speed, Math.max(0.01, movement.length()));
               Vec3 next = projectile.position().add(movement.normalize().scale(speed));
               if (CollisionSnapshot.stepClear(level, projectile, projectile.position(), next)) {
                  f.failures = 0;
                  f.adapter.guide(projectile, movement.normalize().scale(speed));
                  return;
               }

               f.direct = false;
            }

            if (f.ticket != null && f.ticket.finished()) {
               List<Ballistics.Vec> path = f.ticket.result();
               f.ticket = null;
               if (path != null
                  && now - f.routeStarted <= 28L
                  && f.routeOrigin != null
                  && f.routeTarget != null
                  && f.routeOrigin.distance(CollisionSnapshot.vector(projectile.position())) <= 0.8
                  && f.routeTarget.distance(CollisionSnapshot.vector(goal)) <= 3.0) {
                  f.path = path;
                  f.waypoint = 0;
               } else {
                  f.path = List.of();
               }
            }

            while (f.waypoint < f.path.size() && projectile.position().distanceToSqr(CollisionSnapshot.vector(f.path.get(f.waypoint))) < 0.04000000000000001) {
               f.waypoint++;
            }

            if (f.waypoint < f.path.size()) {
               Vec3 movement = CollisionSnapshot.vector(f.path.get(f.waypoint)).subtract(projectile.position());
               Vec3 step = movement.normalize().scale(Math.min(f.speed, movement.length()));
               if (CollisionSnapshot.stepClear(level, projectile, projectile.position(), projectile.position().add(step))) {
                  f.failures = 0;
                  f.adapter.guide(projectile, step);
                  return;
               }

               f.path = List.of();
               f.waypoint = 0;
            }

            f.adapter.guide(projectile, Vec3.ZERO);
            if (++f.failures >= 40) {
               detonate(f, projectile);
            } else {
               if (f.capture != null && f.capture.capture()) {
                  Ballistics.Grid grid = f.capture.result();
                  boolean fresh = f.capture.start().distance(CollisionSnapshot.vector(projectile.position())) <= 0.8 && now - f.routeStarted <= 24L;
                  f.routeOrigin = f.capture.start();
                  f.routeTarget = f.capture.target();
                  f.capture = null;
                  if (fresh && grid != null) {
                     f.ticket = ThrowPlanner.route(grid);
                  }

                  if (f.ticket == null) {
                     f.nextRoute = now + 4L;
                  }
               }

               if (f.capture == null && f.ticket == null && now >= f.nextRoute) {
                  f.routeStarted = now;
                  f.nextRoute = now + 12L;
                  f.capture = new CollisionSnapshot.RouteCapture(level, projectile, goal);
               }
            }
         }
      } else {
         f.cancelRoute();
         f.adapter.guide(projectile, Vec3.ZERO);
         if (++f.failures >= 40) {
            detonate(f, projectile);
         }
      }
   }

   private static void handover(ThrowFlights.Flight flight, Projectile projectile) {
      flight.cancelRoute();
      flight.adapter.restore(projectile, flight.profile);
      flight.nativePhysics = true;
      if (flight.profile.gravity() > 0.0) {
         Vec3 motion = projectile.getDeltaMovement();
         projectile.setDeltaMovement(0.0, motion.y, 0.0);
      }
   }

   private static void remove(ThrowFlights.Flight flight) {
      flights.remove(flight.id);
      flight.cancelRoute();
   }

   private static void detonate(ThrowFlights.Flight flight, Projectile projectile) {
      remove(flight);
      if (!projectile.isRemoved()) {
         flight.adapter.detonate(projectile);
         if (!projectile.isRemoved()) {
            projectile.discard();
         }
      }
   }

   public static void stop(MinecraftServer server) {
      for (ThrowFlights.Flight flight : new ArrayList<>(flights.values())) {
         flight.cancelRoute();
         ServerLevel level = server.getLevel(flight.dimension);
         Entity entity = level == null ? null : level.getEntity(flight.id);
         if (entity instanceof Projectile projectile && !entity.isRemoved()) {
            try {
               flight.adapter.restore(projectile, flight.profile);
            } catch (LinkageError | RuntimeException var7) {
               projectile.setNoGravity(false);
            }
         }
      }

      flights.clear();
   }

   private static final class Flight {
      final UUID id;
      final UUID owner;
      final UUID target;
      final ResourceKey<Level> dimension;
      final ThrowAdapter adapter;
      final ThrowAdapter.Profile profile;
      final Ballistics.Trajectory mode;
      final double speed;
      final double travel;
      final long started;
      int failures;
      int waypoint;
      long nextRay;
      long nextRoute;
      long nextSync;
      long routeStarted;
      boolean direct;
      boolean nativePhysics;
      CollisionSnapshot.RouteCapture capture;
      ThrowPlanner.Ticket<List<Ballistics.Vec>> ticket;
      Ballistics.Vec routeOrigin;
      Ballistics.Vec routeTarget;
      List<Ballistics.Vec> path = List.of();

      Flight(CombatContext ctx, Projectile projectile, ThrowAdapter adapter, ThrowAdapter.Profile profile, Ballistics.Candidate candidate, UUID target) {
         this.id = projectile.getUUID();
         this.owner = ctx.npc.getUUID();
         this.target = target;
         this.dimension = ctx.npc.level().dimension();
         this.adapter = adapter;
         this.profile = profile;
         this.mode = candidate.mode();
         this.speed = candidate.velocity().length();
         this.travel = candidate.ticks();
         this.started = ctx.now;
      }

      void cancelRoute() {
         if (this.ticket != null) {
            this.ticket.cancel();
         }

         this.ticket = null;
         this.capture = null;
         this.path = List.of();
         this.waypoint = 0;
      }
   }
}
