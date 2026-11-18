package bincnpc.gun.throwing;

import bincnpc.gun.common.combat.CombatService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import noppes.npcs.entity.EntityNPCInterface;

public final class CollisionSnapshot {
   public static final int MAX_CELLS = 4096;
   public static final int PER_TICK = 192;
   public static final int SERVER_BUDGET = 2048;
   private static int remaining = 2048;
   private final ServerLevel level;
   private final EntityNPCInterface owner;
   private final Ballistics.Request request;
   private final List<Ballistics.Candidate> candidates;
   private final List<Ballistics.Cell> cells;
   private final boolean safeDistance;
   private final List<Ballistics.Bounds> walls = new ArrayList<>();
   private int cursor;
   private boolean failed;

   public CollisionSnapshot(
      ServerLevel level, EntityNPCInterface owner, Ballistics.Request request, List<Ballistics.Candidate> candidates, boolean safeDistance
   ) {
      this.level = level;
      this.owner = owner;
      this.request = request;
      this.candidates = List.copyOf(candidates);
      this.safeDistance = safeDistance;
      LinkedHashSet<Ballistics.Cell> cells = new LinkedHashSet<>();

      for (Ballistics.Candidate candidate : candidates) {
         for (int i = 1; i < candidate.points().size(); i++) {
            for (Ballistics.Cell cell : Ballistics.cells(candidate.points().get(i - 1), candidate.points().get(i), request.physics().radius(), 4096)) {
               cells.add(cell);
               cells.add(new Ballistics.Cell(cell.x(), cell.y() - 1, cell.z()));
               if (cells.size() > 4096) {
                  throw new IllegalArgumentException("terrain budget");
               }
            }
         }
      }

      this.cells = List.copyOf(cells);
   }

   public static void beginTick() {
      remaining = 2048;
   }

   private static boolean budget() {
      if (remaining <= 0) {
         return false;
      } else {
         remaining--;
         return true;
      }
   }

   public boolean capture() {
      requireThread(this.level);

      for (int count = 0; !this.failed && this.cursor < this.cells.size() && count < 192 && budget(); this.cursor++) {
         Ballistics.Cell cell = this.cells.get(this.cursor);
         BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
         if (!loadedShape(this.level, pos)) {
            this.walls
               .add(new Ballistics.Bounds(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1));
         } else {
            BlockState state = this.level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
               this.walls
                  .add(new Ballistics.Bounds(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1));
            }

            for (AABB box : state.getCollisionShape(this.level, pos, CollisionContext.of(this.owner)).toAabbs()) {
               this.walls.add(bounds(box.move(pos)));
               if (this.walls.size() > 4096) {
                  this.failed = true;
                  break;
               }
            }
         }

         if (this.walls.size() > 4096) {
            this.failed = true;
         }

         count++;
      }

      return this.failed || this.cursor >= this.cells.size();
   }

   public Ballistics.World result() {
      if (!this.failed && this.cursor >= this.cells.size()) {
         List<Ballistics.Bounds> danger = this.safeDistance ? danger(this.level, this.owner, this.request.target(), this.request.physics().blast()) : List.of();
         return danger == null ? null : new Ballistics.World(this.request, this.candidates, this.walls, danger);
      } else {
         return null;
      }
   }

   public static Ballistics.Vec vector(Vec3 v) {
      return new Ballistics.Vec(v.x, v.y, v.z);
   }

   public static Vec3 vector(Ballistics.Vec v) {
      return new Vec3(v.x(), v.y(), v.z());
   }

   public static Ballistics.Bounds bounds(AABB b) {
      return new Ballistics.Bounds(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
   }

   public static boolean loaded(ServerLevel level, BlockPos pos) {
      return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos);
   }

   public static boolean loadedShape(ServerLevel level, BlockPos pos) {
      if (!loaded(level, pos)) {
         return false;
      } else {
         for (int x : new int[]{-1, 1}) {
            for (int z : new int[]{-1, 1}) {
               if (!level.hasChunkAt(pos.offset(x, 0, z))) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   public static boolean volumeLoaded(ServerLevel level, AABB box) {
      if (!(box.minY < level.getMinBuildHeight()) && !(box.maxY > level.getMaxBuildHeight())) {
         int x0 = (int)Math.floor((box.minX - 1.0) / 16.0);
         int x1 = (int)Math.floor((box.maxX + 1.0) / 16.0);
         int z0 = (int)Math.floor((box.minZ - 1.0) / 16.0);
         int z1 = (int)Math.floor((box.maxZ + 1.0) / 16.0);
         if ((long)(x1 - x0 + 1) * (z1 - z0 + 1) > 64L) {
            return false;
         } else {
            for (int x = x0; x <= x1; x++) {
               for (int z = z0; z <= z1; z++) {
                  if (!level.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(box.minY), z * 16))) {
                     return false;
                  }
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private static void requireThread(ServerLevel level) {
      if (!level.getServer().isSameThread()) {
         throw new IllegalStateException("main-thread terrain capture");
      }
   }

   private static List<Ballistics.Bounds> danger(ServerLevel level, EntityNPCInterface owner, Ballistics.Vec goal, double blast) {
      requireThread(level);
      if (blast <= 0.0) {
         return List.of();
      } else {
         List<Ballistics.Bounds> result = new ArrayList<>();
         result.add(bounds(owner.getBoundingBox().inflate(blast + 0.35)));
         AABB area = new AABB(
            goal.x() - blast - 2.0, goal.y() - blast - 2.0, goal.z() - blast - 2.0, goal.x() + blast + 2.0, goal.y() + blast + 2.0, goal.z() + blast + 2.0
         );

         for (LivingEntity nearby : level.getEntitiesOfClass(
            LivingEntity.class, area, entity -> entity != owner && entity.isAlive() && !entity.isSpectator() && !CombatService.hostile(owner, entity)
         )) {
            if (result.size() >= 65) {
               return null;
            }

            result.add(bounds(nearby.getBoundingBox().inflate(blast + 0.35)));
         }

         return List.copyOf(result);
      }
   }

   public static boolean safeArea(ServerLevel level, EntityNPCInterface owner, Ballistics.Vec goal, double blast) {
      List<Ballistics.Bounds> danger = danger(level, owner, goal, blast);
      return danger != null && danger.stream().noneMatch(box -> box.contains(goal));
   }

   public static boolean clear(ServerLevel level, Entity entity, Vec3 start, Vec3 end) {
      requireThread(level);
      if (start.distanceToSqr(end) > 16384.0) {
         return false;
      } else {
         try {
            for (Ballistics.Cell cell : Ballistics.cells(vector(start), vector(end), 0.0, 1024)) {
               if (!loadedShape(level, new BlockPos(cell.x(), cell.y(), cell.z()))) {
                  return false;
               }
            }
         } catch (IllegalArgumentException var6) {
            return false;
         }

         return level.clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, entity)).getType() == Type.MISS;
      }
   }

   public static boolean stepClear(ServerLevel level, Entity projectile, Vec3 from, Vec3 to) {
      if (!clear(level, projectile, from, to)) {
         return false;
      } else {
         double radius = Math.min(2.0, projectile.getBbWidth() * 0.5);

         try {
            for (Ballistics.Cell cell : Ballistics.cells(vector(from), vector(to), radius, 512)) {
               if (!loaded(level, new BlockPos(cell.x(), cell.y(), cell.z()))) {
                  return false;
               }
            }
         } catch (IllegalArgumentException var8) {
            return false;
         }

         AABB body = projectile.getBoundingBox().move(to.subtract(from));
         return volumeLoaded(level, body) && level.noCollision(projectile, body);
      }
   }

   public static final class RouteCapture {
      private final ServerLevel level;
      private final Entity projectile;
      private final Ballistics.Vec min;
      private final Ballistics.Vec start;
      private final Ballistics.Vec target;
      private final boolean[] blocked = new boolean[729];
      private int cursor;

      public RouteCapture(ServerLevel level, Entity projectile, Vec3 target) {
         this.level = level;
         this.projectile = projectile;
         this.start = CollisionSnapshot.vector(projectile.position());
         this.target = CollisionSnapshot.vector(target);
         this.min = new Ballistics.Vec(Math.floor(this.start.x()) - 4.0, Math.floor(this.start.y()) - 4.0, Math.floor(this.start.z()) - 4.0);
      }

      public Ballistics.Vec start() {
         return this.start;
      }

      public Ballistics.Vec target() {
         return this.target;
      }

      public boolean capture() {
         CollisionSnapshot.requireThread(this.level);
         int side = 9;

         for (int count = 0; this.cursor < this.blocked.length && count < 96 && CollisionSnapshot.remaining >= 2; this.cursor++) {
            CollisionSnapshot.remaining -= 2;
            int x = (int)this.min.x() + this.cursor % side;
            int y = (int)this.min.y() + this.cursor / (side * side);
            int z = (int)this.min.z() + this.cursor / side % side;
            BlockPos pos = new BlockPos(x, y, z);
            double r = Math.max(0.08, Math.min(0.49, this.projectile.getBbWidth() * 0.5));
            AABB body = new AABB(x + 0.5 - r, y + 0.5 - r, z + 0.5 - r, x + 0.5 + r, y + 0.5 + r, z + 0.5 + r);
            if (CollisionSnapshot.loadedShape(this.level, pos) && CollisionSnapshot.loadedShape(this.level, pos.below())) {
               for (BlockPos sample : new BlockPos[]{pos, pos.below()}) {
                  BlockState state = this.level.getBlockState(sample);
                  if (!state.getFluidState().isEmpty()) {
                     this.blocked[this.cursor] = true;
                     break;
                  }

                  for (AABB box : state.getCollisionShape(this.level, sample, CollisionContext.of(this.projectile)).toAabbs()) {
                     if (box.move(sample).intersects(body)) {
                        this.blocked[this.cursor] = true;
                        break;
                     }
                  }
               }
            } else {
               this.blocked[this.cursor] = true;
            }

            count++;
         }

         return this.cursor >= this.blocked.length;
      }

      public Ballistics.Grid result() {
         return this.cursor >= this.blocked.length ? new Ballistics.Grid(this.min, this.start, this.target, this.blocked) : null;
      }
   }
}
