package bincnpc.gun.throwing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

public final class Ballistics {
   public static final int MAX_TICKS = 600;
   public static final int MAX_CANDIDATES = 4;
   public static final int MAX_BOXES = 4096;
   public static final int GRID_SIDE = 9;
   public static final int MAX_NODES = 512;

   private Ballistics() {
   }

   public static Ballistics.Vec targetMovement(Ballistics.Vec movement, boolean grounded) {
      Ballistics.Vec result = grounded && movement.y() < 0.0 ? new Ballistics.Vec(movement.x(), 0.0, movement.z()) : movement;
      return result.length() > 2.0 ? result.unit().scale(2.0) : result;
   }

   public static List<Ballistics.Candidate> candidates(Ballistics.Request r) {
      if (r.origin.distance(r.target) > r.range || r.origin.distance(r.target) < 0.05) {
         return List.of();
      } else if (r.mode == Ballistics.Trajectory.TRACKING) {
         Ballistics.Vec direction = r.target.subtract(r.origin).unit();
         if (!angle(r, direction)) {
            return List.of();
         } else {
            double time = Math.max(0.05, Math.min((double)Math.min(600, r.physics.life), r.origin.distance(r.target) / r.physics.speed));
            return List.of(
               new Ballistics.Candidate(
                  Ballistics.Trajectory.TRACKING,
                  r.origin,
                  direction.scale(r.physics.speed),
                  r.target,
                  time,
                  List.of(r.origin, r.origin.add(direction.scale(0.2)))
               )
            );
         }
      } else {
         List<Ballistics.Candidate> result = new ArrayList<>(4);
         if (r.mode == Ballistics.Trajectory.STRAIGHT || r.mode == Ballistics.Trajectory.AUTO) {
            solve(r, Ballistics.Trajectory.STRAIGHT, 0.0, result);
         }

         if (r.mode == Ballistics.Trajectory.CURVE || r.mode == Ballistics.Trajectory.AUTO) {
            solve(r, Ballistics.Trajectory.CURVE, r.physics.gravity, result);
         }

         return List.copyOf(result);
      }
   }

   private static void solve(Ballistics.Request r, Ballistics.Trajectory mode, double gravity, List<Ballistics.Candidate> result) {
      if (mode != Ballistics.Trajectory.CURVE || !(gravity < 1.0E-9)) {
         double end = Math.min(600.0, Math.max(0.05, (double)(r.physics.life - 1)));
         double previous = 0.01;
         double prior = required(r, gravity, previous).length() - r.physics.speed;

         for (int i = 1; i <= 256 && result.size() < 4; i++) {
            if (Thread.currentThread().isInterrupted()) {
               return;
            }

            double t = 0.01 * Math.pow(end / 0.01, i / 256.0);
            double error = required(r, gravity, t).length() - r.physics.speed;
            if (prior <= 0.0 && error >= 0.0 || prior >= 0.0 && error <= 0.0) {
               double lo = previous;
               double hi = t;
               double side = prior;

               for (int j = 0; j < 28; j++) {
                  double mid = (lo + hi) * 0.5;
                  double f = required(r, gravity, mid).length() - r.physics.speed;
                  if ((!(side <= 0.0) || !(f <= 0.0)) && (!(side >= 0.0) || !(f >= 0.0))) {
                     hi = mid;
                  } else {
                     lo = mid;
                     side = f;
                  }
               }

               double root = (lo + hi) * 0.5;
               Ballistics.Vec velocity = required(r, gravity, root);
               Ballistics.Vec goal = r.goal(root);
               if (angle(r, velocity)
                  && r.origin.distance(goal) <= r.range
                  && result.stream().noneMatch(c -> c.mode == mode && Math.abs(c.ticks - root) < 0.05)) {
                  List<Ballistics.Vec> path = path(r.origin, velocity, gravity, r.physics.drag, root);
                  double length = 0.0;

                  for (int p = 1; p < path.size(); p++) {
                     length += path.get(p).distance(path.get(p - 1));
                  }

                  if (length <= Math.min(512.0, r.range * 4.0)) {
                     result.add(new Ballistics.Candidate(mode, r.origin, velocity, goal, root, path));
                  }
               }
            }

            previous = t;
            prior = error;
         }
      }
   }

   private static boolean angle(Ballistics.Request r, Ballistics.Vec velocity) {
      double angle = Math.toDegrees(Math.atan2(velocity.y, Math.hypot(velocity.x, velocity.z)));
      return angle <= r.elevation + 1.0E-6 && angle >= -r.depression - 1.0E-6;
   }

   public static Ballistics.Vec position(Ballistics.Vec origin, Ballistics.Vec velocity, double gravity, double drag, double ticks) {
      double[] terms = terms(drag, ticks);
      return origin.add(velocity.scale(terms[0])).add(new Ballistics.Vec(0.0, -gravity * terms[1], 0.0));
   }

   private static double[] terms(double drag, double ticks) {
      int n = (int)Math.floor(ticks);
      double fraction = ticks - n;
      if (Math.abs(1.0 - drag) < 1.0E-9) {
         return new double[]{ticks, n * (n - 1) * 0.5 + fraction * n};
      } else {
         double dn = Math.pow(drag, n);
         double sum = (1.0 - dn) / (1.0 - drag);
         return new double[]{sum + fraction * dn, (n - sum) / (1.0 - drag) + fraction * sum};
      }
   }

   private static Ballistics.Vec required(Ballistics.Request r, double gravity, double t) {
      double[] terms = terms(r.physics.drag, t);
      return r.goal(t).subtract(r.origin).add(new Ballistics.Vec(0.0, gravity * terms[1], 0.0)).scale(1.0 / terms[0]);
   }

   public static List<Ballistics.Vec> path(Ballistics.Vec origin, Ballistics.Vec velocity, double gravity, double drag, double ticks) {
      if (Double.isFinite(ticks) && !(ticks <= 0.0) && !(ticks > 600.0)) {
         List<Ballistics.Vec> result = new ArrayList<>((int)Math.ceil(ticks) + 1);
         result.add(origin);

         for (int i = 1; i < ticks; i++) {
            result.add(position(origin, velocity, gravity, drag, i));
         }

         result.add(position(origin, velocity, gravity, drag, ticks));
         return List.copyOf(result);
      } else {
         throw new IllegalArgumentException("flight time");
      }
   }

   public static Ballistics.Candidate retarget(Ballistics.Request r, Ballistics.Candidate prior) {
      if (r.origin.distance(r.target) > r.range) {
         return null;
      } else if (prior.mode == Ballistics.Trajectory.TRACKING) {
         return candidates(r).stream().findFirst().orElse(null);
      } else {
         double gravity = prior.mode == Ballistics.Trajectory.CURVE ? r.physics.gravity : 0.0;
         Ballistics.Vec velocity = required(r, gravity, prior.ticks).unit().scale(r.physics.speed);
         Ballistics.Vec goal = r.goal(prior.ticks);
         if (angle(r, velocity) && !(r.origin.distance(goal) > r.range)) {
            List<Ballistics.Vec> path = path(r.origin, velocity, gravity, r.physics.drag, prior.ticks);
            return path.get(path.size() - 1).distance(goal) > r.tolerance()
               ? null
               : new Ballistics.Candidate(prior.mode, r.origin, velocity, goal, prior.ticks, path);
         } else {
            return null;
         }
      }
   }

   public static Ballistics.Candidate choose(Ballistics.World world) {
      Ballistics.Spatial walls = new Ballistics.Spatial(world.walls, world.request.physics.radius);
      Ballistics.Candidate best = null;
      double score = Double.POSITIVE_INFINITY;

      for (Ballistics.Candidate candidate : world.candidates) {
         if (Thread.currentThread().isInterrupted()) {
            return null;
         }

         if (world.request.mode != Ballistics.Trajectory.AUTO || candidate.mode != Ballistics.Trajectory.TRACKING) {
            Ballistics.Vec impact = candidate.goal;
            boolean clear = true;

            for (int i = 1; i < candidate.points.size(); i++) {
               Ballistics.Vec a = candidate.points.get(i - 1);
               Ballistics.Vec b = candidate.points.get(i);
               double hit = walls.hit(a, b);
               if (Double.isFinite(hit)) {
                  impact = a.lerp(b, hit);
                  clear = candidate.mode != Ballistics.Trajectory.TRACKING && impact.distance(candidate.goal) <= world.request.tolerance();
                  break;
               }
            }

            Ballistics.Vec landing = impact;
            if (clear && !world.danger.stream().anyMatch(box -> box.contains(candidate.goal)) && !world.danger.stream().anyMatch(box -> box.contains(landing))) {
               double rank = candidate.ticks + (candidate.mode == Ballistics.Trajectory.CURVE ? 0.25 : 0.0);
               if (rank < score) {
                  score = rank;
                  best = candidate;
               }
            }
         }
      }

      return best;
   }

   public static List<Ballistics.Cell> cells(Ballistics.Vec a, Ballistics.Vec b, double radius, int limit) {
      if (!(radius < 0.0) && !(radius > 2.0) && limit >= 1 && limit <= 8192) {
         LinkedHashSet<Ballistics.Cell> result = new LinkedHashSet<>();
         double length = a.distance(b);
         int steps = Math.max(1, (int)Math.ceil(length * 4.0));
         if (steps > 4096) {
            throw new IllegalArgumentException("segment budget");
         } else {
            for (int i = 1; i <= steps; i++) {
               Ballistics.Vec p = a.lerp(b, (double)(i - 1) / steps);
               Ballistics.Vec q = a.lerp(b, (double)i / steps);

               for (int x = (int)Math.floor(Math.min(p.x, q.x) - radius); x <= (int)Math.floor(Math.max(p.x, q.x) + radius); x++) {
                  for (int y = (int)Math.floor(Math.min(p.y, q.y) - radius); y <= (int)Math.floor(Math.max(p.y, q.y) + radius); y++) {
                     for (int z = (int)Math.floor(Math.min(p.z, q.z) - radius); z <= (int)Math.floor(Math.max(p.z, q.z) + radius); z++) {
                        result.add(new Ballistics.Cell(x, y, z));
                        if (result.size() > limit) {
                           throw new IllegalArgumentException("cell budget");
                        }
                     }
                  }
               }
            }

            return List.copyOf(result);
         }
      } else {
         throw new IllegalArgumentException("cell budget");
      }
   }

   public static List<Ballistics.Vec> route(Ballistics.Grid grid) {
      int sx = (int)Math.floor(grid.start.x - grid.min.x);
      int sy = (int)Math.floor(grid.start.y - grid.min.y);
      int sz = (int)Math.floor(grid.start.z - grid.min.z);
      if (!grid.free(sx, sy, sz)) {
         return List.of();
      } else {
         int size = 729;
         int start = grid.index(sx, sy, sz);
         int best = start;
         double[] cost = new double[size];
         Arrays.fill(cost, Double.POSITIVE_INFINITY);
         cost[start] = 0.0;
         int[] parent = new int[size];
         Arrays.fill(parent, -1);
         boolean[] closed = new boolean[size];
         PriorityQueue<Ballistics.Open> open = new PriorityQueue<>(Comparator.comparingDouble(Ballistics.Open::score));
         double initial = grid.center(start).distance(grid.target);
         double closest = initial;
         open.add(new Ballistics.Open(start, initial));
         int expanded = 0;

         while (!open.isEmpty() && expanded < 512) {
            if (Thread.currentThread().isInterrupted()) {
               return List.of();
            }

            int current = open.remove().index;
            if (!closed[current]) {
               closed[current] = true;
               expanded++;
               double distance = grid.center(current).distance(grid.target);
               if (distance < closest) {
                  closest = distance;
                  best = current;
               }

               if (distance < 0.8) {
                  best = current;
                  break;
               }

               int x = current % 9;
               int y = current / 81;
               int z = current / 9 % 9;

               for (int dx = -1; dx <= 1; dx++) {
                  for (int dy = -1; dy <= 1; dy++) {
                     for (int dz = -1; dz <= 1; dz++) {
                        if ((dx != 0 || dy != 0 || dz != 0) && grid.free(x + dx, y + dy, z + dz)) {
                           boolean safe = true;

                           for (int ix = 0; ix <= Math.abs(dx); ix++) {
                              for (int iy = 0; iy <= Math.abs(dy); iy++) {
                                 for (int iz = 0; iz <= Math.abs(dz); iz++) {
                                    if (!grid.free(x + ix * dx, y + iy * dy, z + iz * dz)) {
                                       safe = false;
                                    }
                                 }
                              }
                           }

                           if (safe) {
                              int next = grid.index(x + dx, y + dy, z + dz);
                              if (!closed[next]) {
                                 double g = cost[current] + Math.sqrt(dx * dx + dy * dy + dz * dz);
                                 if (!(g >= cost[next])) {
                                    cost[next] = g;
                                    parent[next] = current;
                                    open.add(new Ballistics.Open(next, g + grid.center(next).distance(grid.target)));
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         if (best != start && !(initial - closest < 0.5)) {
            ArrayList<Ballistics.Vec> path = new ArrayList<>();

            for (int at = best; at != start && at >= 0 && path.size() < size; at = parent[at]) {
               path.add(grid.center(at));
            }

            Collections.reverse(path);
            return List.copyOf(path);
         } else {
            return List.of();
         }
      }
   }

   public record Bounds(double x0, double y0, double z0, double x1, double y1, double z1) {
      public Bounds(double x0, double y0, double z0, double x1, double y1, double z1) {
         if (Double.isFinite(x0)
            && Double.isFinite(y0)
            && Double.isFinite(z0)
            && Double.isFinite(x1)
            && Double.isFinite(y1)
            && Double.isFinite(z1)
            && !(x0 > x1)
            && !(y0 > y1)
            && !(z0 > z1)) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
         } else {
            throw new IllegalArgumentException("bounds");
         }
      }

      public Ballistics.Bounds inflate(double r) {
         return new Ballistics.Bounds(this.x0 - r, this.y0 - r, this.z0 - r, this.x1 + r, this.y1 + r, this.z1 + r);
      }

      public boolean contains(Ballistics.Vec p) {
         return p.x >= this.x0 && p.x <= this.x1 && p.y >= this.y0 && p.y <= this.y1 && p.z >= this.z0 && p.z <= this.z1;
      }

      public double hit(Ballistics.Vec a, Ballistics.Vec b) {
         double low = 0.0;
         double high = 1.0;
         double[] p = new double[]{a.x, a.y, a.z};
         double[] d = new double[]{b.x - a.x, b.y - a.y, b.z - a.z};
         double[] min = new double[]{this.x0, this.y0, this.z0};
         double[] max = new double[]{this.x1, this.y1, this.z1};

         for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-10) {
               if (p[i] < min[i] || p[i] > max[i]) {
                  return Double.POSITIVE_INFINITY;
               }
            } else {
               double l = (min[i] - p[i]) / d[i];
               double h = (max[i] - p[i]) / d[i];
               if (l > h) {
                  double t = l;
                  l = h;
                  h = t;
               }

               low = Math.max(low, l);
               high = Math.min(high, h);
               if (low > high) {
                  return Double.POSITIVE_INFINITY;
               }
            }
         }

         return low;
      }
   }

   public record Candidate(
      Ballistics.Trajectory mode, Ballistics.Vec origin, Ballistics.Vec velocity, Ballistics.Vec goal, double ticks, List<Ballistics.Vec> points
   ) {
      public Candidate(
         Ballistics.Trajectory mode, Ballistics.Vec origin, Ballistics.Vec velocity, Ballistics.Vec goal, double ticks, List<Ballistics.Vec> points
      ) {
         if (mode != null
            && mode != Ballistics.Trajectory.AUTO
            && origin != null
            && velocity != null
            && goal != null
            && Double.isFinite(ticks)
            && !(ticks <= 0.0)
            && !(ticks > 600.0)
            && points != null
            && points.size() >= 2
            && points.size() <= 602) {
            points = List.copyOf(points);
            this.mode = mode;
            this.origin = origin;
            this.velocity = velocity;
            this.goal = goal;
            this.ticks = ticks;
            this.points = points;
         } else {
            throw new IllegalArgumentException("candidate");
         }
      }
   }

   public record Cell(int x, int y, int z) {
   }

   public static final class Grid {
      private final Ballistics.Vec min;
      private final Ballistics.Vec start;
      private final Ballistics.Vec target;
      private final boolean[] blocked;

      public Grid(Ballistics.Vec min, Ballistics.Vec start, Ballistics.Vec target, boolean[] blocked) {
         this.min = Objects.requireNonNull(min);
         this.start = Objects.requireNonNull(start);
         this.target = Objects.requireNonNull(target);
         if (blocked != null && blocked.length == 729) {
            this.blocked = (boolean[])blocked.clone();
         } else {
            throw new IllegalArgumentException("grid");
         }
      }

      public Ballistics.Vec min() {
         return this.min;
      }

      public Ballistics.Vec start() {
         return this.start;
      }

      public Ballistics.Vec target() {
         return this.target;
      }

      private int index(int x, int y, int z) {
         return (y * 9 + z) * 9 + x;
      }

      private boolean free(int x, int y, int z) {
         return x >= 0 && x < 9 && y >= 0 && y < 9 && z >= 0 && z < 9 && !this.blocked[this.index(x, y, z)];
      }

      private Ballistics.Vec center(int index) {
         return this.min.add(new Ballistics.Vec(index % 9 + 0.5, index / 81 + 0.5, index / 9 % 9 + 0.5));
      }
   }

   private record Open(int index, double score) {
   }

   public record Physics(double speed, double gravity, double drag, double radius, double blast, int life) {
      public Physics(double speed, double gravity, double drag, double radius, double blast, int life) {
         if (Double.isFinite(speed)
            && !(speed < 1.0E-4)
            && !(speed > 800.0)
            && Double.isFinite(gravity)
            && !(gravity < 0.0)
            && !(gravity > 2.0)
            && Double.isFinite(drag)
            && !(drag <= 0.0)
            && !(drag > 1.0)
            && Double.isFinite(radius)
            && !(radius < 0.0)
            && !(radius > 2.0)
            && Double.isFinite(blast)
            && !(blast < 0.0)
            && !(blast > 32.0)
            && life >= 1
            && life <= 1200) {
            this.speed = speed;
            this.gravity = gravity;
            this.drag = drag;
            this.radius = radius;
            this.blast = blast;
            this.life = life;
         } else {
            throw new IllegalArgumentException("physics");
         }
      }
   }

   public record Request(
      Ballistics.Vec origin,
      Ballistics.Vec target,
      Ballistics.Vec movement,
      Ballistics.Physics physics,
      Ballistics.Trajectory mode,
      double elevation,
      double depression,
      double range,
      double deviation,
      double targetRadius,
      int lead,
      boolean prediction
   ) {
      public Request(
         Ballistics.Vec origin,
         Ballistics.Vec target,
         Ballistics.Vec movement,
         Ballistics.Physics physics,
         Ballistics.Trajectory mode,
         double elevation,
         double depression,
         double range,
         double deviation,
         double targetRadius,
         int lead,
         boolean prediction
      ) {
         Objects.requireNonNull(origin);
         Objects.requireNonNull(target);
         Objects.requireNonNull(movement);
         Objects.requireNonNull(physics);
         Objects.requireNonNull(mode);
         if (Double.isFinite(elevation)
            && !(elevation < 0.0)
            && !(elevation > 89.0)
            && Double.isFinite(depression)
            && !(depression < 0.0)
            && !(depression > 89.0)
            && Double.isFinite(range)
            && !(range < 1.0)
            && !(range > 128.0)
            && Double.isFinite(deviation)
            && !(deviation < 0.0)
            && !(deviation > 16.0)
            && Double.isFinite(targetRadius)
            && !(targetRadius < 0.0)
            && !(targetRadius > 8.0)
            && lead >= 0
            && lead <= 200
            && !(movement.length() > 4.0)) {
            this.origin = origin;
            this.target = target;
            this.movement = movement;
            this.physics = physics;
            this.mode = mode;
            this.elevation = elevation;
            this.depression = depression;
            this.range = range;
            this.deviation = deviation;
            this.targetRadius = targetRadius;
            this.lead = lead;
            this.prediction = prediction;
         } else {
            throw new IllegalArgumentException("throw request");
         }
      }

      public Ballistics.Vec goal(double ticks) {
         return this.prediction ? this.target.add(this.movement.scale(this.lead + ticks)) : this.target;
      }

      public double tolerance() {
         return Math.max(0.35, this.deviation + this.targetRadius);
      }
   }

   private static final class Spatial {
      private final Map<Ballistics.Cell, List<Ballistics.Bounds>> boxes = new HashMap<>();
      private final List<Ballistics.Bounds> broad = new ArrayList<>();

      Spatial(List<Ballistics.Bounds> walls, double radius) {
         for (Ballistics.Bounds original : walls) {
            Ballistics.Bounds b = original.inflate(radius);
            int x0 = (int)Math.floor(b.x0);
            int y0 = (int)Math.floor(b.y0);
            int z0 = (int)Math.floor(b.z0);
            int x1 = (int)Math.floor(b.x1);
            int y1 = (int)Math.floor(b.y1);
            int z1 = (int)Math.floor(b.z1);
            if ((long)(x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 128L) {
               this.broad.add(b);
            } else {
               for (int x = x0; x <= x1; x++) {
                  for (int y = y0; y <= y1; y++) {
                     for (int z = z0; z <= z1; z++) {
                        this.boxes.computeIfAbsent(new Ballistics.Cell(x, y, z), ignored -> new ArrayList<>()).add(b);
                     }
                  }
               }
            }
         }
      }

      double hit(Ballistics.Vec a, Ballistics.Vec b) {
         double first = Double.POSITIVE_INFINITY;
         Set<Ballistics.Bounds> seen = new HashSet<>(this.broad);

         for (Ballistics.Cell cell : Ballistics.cells(a, b, 0.0, 2048)) {
            List<Ballistics.Bounds> nearby = this.boxes.get(cell);
            if (nearby != null) {
               seen.addAll(nearby);
            }
         }

         for (Ballistics.Bounds box : seen) {
            first = Math.min(first, box.hit(a, b));
         }

         return first;
      }
   }

   public static enum Trajectory {
      STRAIGHT,
      CURVE,
      AUTO,
      TRACKING;
   }

   public record Vec(double x, double y, double z) {
      public Vec(double x, double y, double z) {
         if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
            this.x = x;
            this.y = y;
            this.z = z;
         } else {
            throw new IllegalArgumentException("vector");
         }
      }

      public Ballistics.Vec add(Ballistics.Vec v) {
         return new Ballistics.Vec(this.x + v.x, this.y + v.y, this.z + v.z);
      }

      public Ballistics.Vec subtract(Ballistics.Vec v) {
         return new Ballistics.Vec(this.x - v.x, this.y - v.y, this.z - v.z);
      }

      public Ballistics.Vec scale(double s) {
         return new Ballistics.Vec(this.x * s, this.y * s, this.z * s);
      }

      public double length() {
         return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
      }

      public double distance(Ballistics.Vec v) {
         return this.subtract(v).length();
      }

      public Ballistics.Vec unit() {
         double n = this.length();
         return n < 1.0E-10 ? new Ballistics.Vec(0.0, 0.0, 0.0) : this.scale(1.0 / n);
      }

      public Ballistics.Vec lerp(Ballistics.Vec v, double t) {
         return this.add(v.subtract(this).scale(t));
      }
   }

   public record World(Ballistics.Request request, List<Ballistics.Candidate> candidates, List<Ballistics.Bounds> walls, List<Ballistics.Bounds> danger) {
      public World(Ballistics.Request request, List<Ballistics.Candidate> candidates, List<Ballistics.Bounds> walls, List<Ballistics.Bounds> danger) {
         Objects.requireNonNull(request);
         if (candidates != null && candidates.size() <= 4 && walls != null && walls.size() <= 4096 && danger != null && danger.size() <= 65) {
            candidates = List.copyOf(candidates);
            walls = List.copyOf(walls);
            danger = List.copyOf(danger);
            this.request = request;
            this.candidates = candidates;
            this.walls = walls;
            this.danger = danger;
         } else {
            throw new IllegalArgumentException("collision snapshot");
         }
      }
   }
}
