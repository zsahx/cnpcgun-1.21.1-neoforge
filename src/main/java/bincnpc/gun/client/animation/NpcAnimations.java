package bincnpc.gun.client.animation;

import bincnpc.gun.common.network.GunNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.core.util.Vec3f;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import noppes.npcs.entity.EntityNPCInterface;

public final class NpcAnimations {
   private static final Map<EntityNPCInterface, NpcAnimations.WeaponAnimation> ANIMATIONS = new WeakHashMap<>();
   private static final Map<HumanoidModel<?>, List<NpcAnimations.PartPose>> SAVED_POSES = new WeakHashMap<>();

   private NpcAnimations() {
   }

   public static void preview(EntityNPCInterface npc, String domain, String pose, String actionId, int duration, boolean aiming) {
      NpcAnimations.WeaponAnimation animation = ANIMATIONS.computeIfAbsent(npc, NpcAnimations.WeaponAnimation::new);
      animation.start(domain, pose, actionId, duration, aiming);
   }

   public static void clearPreview(EntityNPCInterface npc) {
      ANIMATIONS.remove(npc);
   }

   public static void receive(GunNetwork.Animation packet) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null && level.getEntity(packet.entityId()) instanceof EntityNPCInterface npc && npc.getUUID().equals(packet.npcId())) {
         NpcAnimations.WeaponAnimation animation = ANIMATIONS.computeIfAbsent(npc, NpcAnimations.WeaponAnimation::new);
         if (!packet.pose().equals("STATE") && !packet.pose().equals("STATE_IDLE")) {
            animation.start(packet.domain(), packet.pose(), packet.actionId(), packet.duration(), packet.aiming());
            if (packet.domain().equals("SLASHBLADE") && packet.actionId().startsWith("combo:") && ModList.get().isLoaded("slashblade")) {
               try {
                  BladeNativeAnimations.synchronizeBlade(npc, packet.actionId());
               } catch (LinkageError | RuntimeException var5) {
               }
            }
         } else {
            animation.state(packet.domain(), packet.aiming(), packet.pose().equals("STATE_IDLE"));
         }
      }
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post event) {
      {
         if (Minecraft.getInstance().level == null) {
            ANIMATIONS.clear();
            SAVED_POSES.values().forEach(poses -> poses.forEach(NpcAnimations.PartPose::restore));
            SAVED_POSES.clear();
         } else {
            Iterator<Entry<EntityNPCInterface, NpcAnimations.WeaponAnimation>> iterator = ANIMATIONS.entrySet().iterator();

            while (iterator.hasNext()) {
               Entry<EntityNPCInterface, NpcAnimations.WeaponAnimation> item = iterator.next();
               if (!item.getKey().isRemoved() && item.getKey().isAlive() && item.getKey().level() == Minecraft.getInstance().level) {
                  item.getValue().processor.tick();
               } else {
                  iterator.remove();
               }
            }
         }
      }
   }

   public static void apply(EntityNPCInterface npc, HumanoidModel<?> model, float partialTick) {
      NpcAnimations.WeaponAnimation animation = ANIMATIONS.get(npc);
      if (animation != null && animation.processor.isActive()) {
         restore(model);
         List<NpcAnimations.PartPose> saved = new ArrayList<>();

         for (ModelPart part : List.of(model.head, model.hat, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg)) {
            saved.add(new NpcAnimations.PartPose(part));
         }

         if (model instanceof PlayerModel<?> player) {
            for (ModelPart part : List.of(player.rightSleeve, player.leftSleeve, player.jacket, player.rightPants, player.leftPants)) {
               saved.add(new NpcAnimations.PartPose(part));
            }
         }

         SAVED_POSES.put(model, saved);
         animation.processor.setTickDelta(partialTick);
         transform(animation.processor, "rightArm", model.rightArm);
         transform(animation.processor, "leftArm", model.leftArm);
         transform(animation.processor, "rightLeg", model.rightLeg);
         transform(animation.processor, "leftLeg", model.leftLeg);
         transform(animation.processor, "head", model.head);
         if (!animation.nativeActive()) {
            transform(animation.processor, "body", model.body);
         }

         model.hat.copyFrom(model.head);
         if (model instanceof PlayerModel<?> player) {
            player.rightSleeve.copyFrom(player.rightArm);
            player.leftSleeve.copyFrom(player.leftArm);
            player.jacket.copyFrom(player.body);
            player.rightPants.copyFrom(player.rightLeg);
            player.leftPants.copyFrom(player.leftLeg);
         }
      }
   }

   public static void restore(EntityModel<?> model) {
      if (model instanceof HumanoidModel<?> humanoid) {
         List<NpcAnimations.PartPose> saved = SAVED_POSES.remove(humanoid);
         if (saved != null) {
            saved.forEach(NpcAnimations.PartPose::restore);
         }
      }
   }

   public static boolean nativeRoot(EntityNPCInterface npc) {
      NpcAnimations.WeaponAnimation animation = ANIMATIONS.get(npc);
      return animation != null && animation.nativeActive();
   }

   public static boolean nativeGun(EntityNPCInterface npc) {
      NpcAnimations.WeaponAnimation animation = ANIMATIONS.get(npc);
      return animation != null && animation.gunAnimation() != null;
   }

   public static boolean idleGun(EntityNPCInterface npc) {
      NpcAnimations.WeaponAnimation animation = ANIMATIONS.get(npc);
      return animation != null && animation.summaryAge < 45 && animation.idleGun();
   }

   public static void root(EntityNPCInterface npc, PoseStack pose, float partialTick) {
      NpcAnimations.WeaponAnimation animation = ANIMATIONS.get(npc);
      if (animation != null && animation.nativeActive()) {
         animation.processor.setTickDelta(partialTick);
         Vec3f position = animation.processor.get3DTransform("body", TransformType.POSITION, Vec3f.ZERO);
         Vec3f rotation = animation.processor.get3DTransform("body", TransformType.ROTATION, Vec3f.ZERO);
         pose.translate(-(Float)position.getX(), ((Float)position.getY()).floatValue() + 0.7, -(Float)position.getZ());
         pose.mulPose(Axis.ZP.rotation((Float)rotation.getZ()));
         pose.mulPose(Axis.YP.rotation((Float)rotation.getY()));
         pose.mulPose(Axis.XP.rotation((Float)rotation.getX()));
         pose.translate(0.0, -0.7, 0.0);
      }
   }

   private static void transform(AnimationProcessor processor, String name, ModelPart part) {
      Vec3f value = processor.get3DTransform(name, TransformType.ROTATION, new Vec3f(part.xRot, part.yRot, part.zRot));
      part.xRot = (Float)value.getX();
      part.yRot = (Float)value.getY();
      part.zRot = (Float)value.getZ();
      Vec3f position = processor.get3DTransform(name, TransformType.POSITION, new Vec3f(part.x, part.y, part.z));
      part.x = (Float)position.getX();
      part.y = (Float)position.getY();
      part.z = (Float)position.getZ();
   }

   private record PartPose(ModelPart part, float x, float y, float z, float rx, float ry, float rz) {
      PartPose(ModelPart part) {
         this(part, part.x, part.y, part.z, part.xRot, part.yRot, part.zRot);
      }

      void restore() {
         this.part.x = this.x;
         this.part.y = this.y;
         this.part.z = this.z;
         this.part.xRot = this.rx;
         this.part.yRot = this.ry;
         this.part.zRot = this.rz;
      }
   }

   private static final class WeaponAnimation implements IAnimation {
      final WeakReference<EntityNPCInterface> npc;
      final AnimationProcessor processor = new AnimationProcessor(this);
      String domain = "UNKNOWN";
      String pose = "";
      IAnimation nativeBlade;
      IAnimation nativeGunAction;
      IAnimation nativeGunLoop;
      String gunLoopKey = "";
      boolean aiming;
      boolean idle;
      int age = 1000;
      int duration = 1;
      int summaryAge = 1000;
      int direction = 1;
      int phantomAge = 1000;
      int phantomDuration = 1;
      int guardAge = 1000;
      int guardDuration = 1;
      int throwAge = 1000;
      int throwDuration = 1;
      boolean throwRelease;
      boolean throwHolding;
      float delta;

      WeaponAnimation(EntityNPCInterface npc) {
         this.npc = new WeakReference<>(npc);
      }

      void state(String domain, boolean aiming, boolean idle) {
         if (!this.domain.equals(domain)) {
            this.nativeBlade = null;
            this.nativeGunAction = this.nativeGunLoop = null;
            this.gunLoopKey = "";
            this.age = this.phantomAge = this.guardAge = 1000;
            this.pose = "";
         }

         this.domain = domain;
         this.aiming = aiming;
         this.idle = idle;
         this.summaryAge = 0;
         if (idle) {
            this.nativeGunLoop = null;
            this.gunLoopKey = "";
         }
      }

      void start(String domain, String pose, String id, int duration, boolean aiming) {
         if (!this.domain.equals(domain)) {
            this.nativeBlade = null;
            this.nativeGunAction = this.nativeGunLoop = null;
            this.gunLoopKey = "";
            this.phantomAge = this.guardAge = 1000;
         }

         this.domain = domain;
         this.aiming = aiming;
         this.idle = false;
         this.summaryAge = 0;
         if (pose.equals("THROW_END")) {
            this.throwAge = 1000;
            this.throwHolding = false;
         } else if (pose.equals("THROW") || pose.equals("THROW_HOLD") || pose.equals("THROW_RELEASE")) {
            this.throwAge = 0;
            this.throwDuration = Mth.clamp(duration, 1, 220);
            this.throwRelease = pose.equals("THROW_RELEASE");
            this.throwHolding = pose.equals("THROW_HOLD");
         } else if (domain.equals("SLASHBLADE") && pose.equals("PHANTOM_SWORD")) {
            this.phantomAge = 0;
            this.phantomDuration = Mth.clamp(duration, 4, 100);
         } else if (domain.equals("SLASHBLADE") && pose.equals("DEFENSE") && !id.startsWith("combo:")) {
            this.guardAge = 0;
            this.guardDuration = Mth.clamp(duration, 4, 100);
         } else {
            this.pose = pose;
            this.age = 0;
            this.duration = Mth.clamp(duration, 1, 2400);
            this.direction = (id.hashCode() & 1) == 0 ? 1 : -1;
            this.nativeBlade = null;
            this.nativeGunAction = null;
            if (domain.equals("GUN") && ModList.get().isLoaded("tacz") && this.npc.get() != null) {
               try {
                  TaczNativeAnimations.Clip clip = TaczNativeAnimations.action(this.npc.get(), pose, aiming);
                  if (clip != null) {
                     this.nativeGunAction = (IAnimation)(pose.equals("RELOAD") && id.equals("reload_override")
                        ? new ModifierLayer(clip.animation(), new AbstractModifier[]{new SpeedModifier((float)clip.duration() / this.duration)})
                        : clip.animation());
                     if (!id.equals("reload_override")) {
                        this.duration = Math.max(this.duration, clip.duration());
                     }
                  }
               } catch (LinkageError | RuntimeException var8) {
               }
            }

            if (domain.equals("SLASHBLADE") && ModList.get().isLoaded("slashblade")) {
               try {
                  this.nativeBlade = BladeNativeAnimations.create(id);
               } catch (LinkageError | RuntimeException var7) {
               }
            }
         }
      }

      boolean nativeActive() {
         return this.throwAge >= this.throwDuration + 4
            && this.domain.equals("SLASHBLADE")
            && this.nativeBlade != null
            && this.nativeBlade.isActive()
            && this.age < this.duration + 4;
      }

      boolean idleGun() {
         boolean action = this.pose.equals("FIRE") || this.pose.equals("RELOAD") || this.pose.equals("MELEE");
         return this.domain.equals("GUN") && this.idle && (!action || this.age >= this.duration + 4);
      }

      IAnimation gunAnimation() {
         if (this.domain.equals("GUN") && this.summaryAge < 45 && !this.idleGun()) {
            return this.nativeGunAction != null && this.nativeGunAction.isActive()
               ? this.nativeGunAction
               : (this.nativeGunLoop != null && this.nativeGunLoop.isActive() ? this.nativeGunLoop : null);
         } else {
            return null;
         }
      }

      public void tick() {
         this.age++;
         this.summaryAge++;
         this.phantomAge++;
         this.guardAge++;
         this.throwAge++;
         if (this.nativeBlade != null) {
            this.nativeBlade.tick();
         }

         if (this.idleGun()) {
            this.nativeGunAction = null;
         }

         if (this.nativeGunAction != null) {
            this.nativeGunAction.tick();
         }

         if (this.domain.equals("GUN") && !this.idle && this.summaryAge < 45 && ModList.get().isLoaded("tacz") && this.npc.get() != null) {
            try {
               String key = TaczNativeAnimations.loopKey(this.npc.get(), this.aiming);
               if (!key.equals(this.gunLoopKey) || this.nativeGunLoop == null || !this.nativeGunLoop.isActive()) {
                  this.gunLoopKey = key;
                  this.nativeGunLoop = TaczNativeAnimations.loop(this.npc.get(), this.aiming);
               }
            } catch (LinkageError | RuntimeException var2) {
               this.nativeGunLoop = null;
            }
         }

         if (this.nativeGunLoop != null) {
            this.nativeGunLoop.tick();
         }
      }

      public boolean isActive() {
         return this.age < this.duration + 4 && !this.idleGun()
            || this.domain.equals("GUN") && !this.idleGun() && this.summaryAge < 45
            || this.throwAge < this.throwDuration + 4
            || this.domain.equals("SLASHBLADE") && (this.phantomAge < this.phantomDuration + 4 || this.guardAge < this.guardDuration + 4);
      }

      public void setupAnim(float partialTick) {
         this.delta = partialTick;
      }

      public Vec3f get3DTransform(String part, TransformType type, float partialTick, Vec3f original) {
         if (type != TransformType.ROTATION || !(this.throwAge + partialTick < this.throwDuration + 4) || !part.equals("leftArm") && !part.equals("body")) {
            IAnimation gun = this.gunAnimation();
            if (gun == null || !part.equals("rightArm") && !part.equals("leftArm") && !part.equals("body")) {
               float elapsed = this.age + this.delta;
               float phase = Mth.clamp(elapsed / Math.max(1, this.duration), 0.0F, 1.0F);
               float fade = Mth.clamp((this.duration + 4 - elapsed) / 4.0F, 0.0F, 1.0F);
               float pulse = (float)Math.sin(phase * Math.PI);
               float pitch = this.npc.get() == null ? 0.0F : this.npc.get().getXRot() * (float) (Math.PI / 180.0);
               Vec3f target = original;
               float weight = fade;
               if (this.nativeActive()) {
                  Vec3f value = this.nativeBlade.get3DTransform(part, type, partialTick, original);
                  if (Float.isFinite((Float)value.getX()) && Float.isFinite((Float)value.getY()) && Float.isFinite((Float)value.getZ())) {
                     target = value;
                  }

                  if (type == TransformType.POSITION) {
                     target = new Vec3f(
                        Mth.clamp((Float)target.getX(), (Float)original.getX() - 32.0F, (Float)original.getX() + 32.0F),
                        Mth.clamp((Float)target.getY(), (Float)original.getY() - 32.0F, (Float)original.getY() + 32.0F),
                        Mth.clamp((Float)target.getZ(), (Float)original.getZ() - 32.0F, (Float)original.getZ() + 32.0F)
                     );
                  }

                  target = new Vec3f(
                     Mth.lerp(fade, (Float)original.getX(), (Float)target.getX()),
                     Mth.lerp(fade, (Float)original.getY(), (Float)target.getY()),
                     Mth.lerp(fade, (Float)original.getZ(), (Float)target.getZ())
                  );
                  if (type != TransformType.ROTATION) {
                     return target;
                  }

                  original = target;
                  weight = 0.0F;
               } else if (type != TransformType.ROTATION) {
                  return original;
               }

               if (this.domain.equals("GUN") && !this.idleGun()) {
                  weight = this.summaryAge < 35 ? 1.0F : Mth.clamp((45 - this.summaryAge) / 10.0F, 0.0F, 1.0F);
                  if (part.equals("rightArm")) {
                     target = new Vec3f((this.aiming ? -1.55F : -0.85F) + pitch - (this.pose.equals("FIRE") ? pulse * fade * 0.15F : 0.0F), -0.12F, 0.0F);
                  }

                  if (part.equals("leftArm")) {
                     target = new Vec3f((this.aiming ? -1.45F : -0.55F) + pitch, 0.55F, -0.12F);
                  }

                  if (this.pose.equals("RELOAD") && elapsed < this.duration + 4) {
                     if (part.equals("rightArm")) {
                        target = new Vec3f(-0.7F - 0.25F * pulse, -0.3F, 0.2F);
                     }

                     if (part.equals("leftArm")) {
                        target = new Vec3f(-0.8F - 0.3F * pulse, 0.6F, -0.25F);
                     }
                  }
               } else if (this.domain.equals("SLASHBLADE") && elapsed < this.duration + 4 && !this.nativeActive()) {
                  if (part.equals("rightArm")) {
                     target = new Vec3f(-1.5F + phase * 2.5F, this.direction * (0.4F - pulse), this.direction * (0.2F + pulse * 0.5F));
                  }

                  if (part.equals("body")) {
                     target = new Vec3f(0.08F * pulse, this.direction * 0.3F * pulse, 0.0F);
                  }

                  if (this.pose.equals("PHANTOM_SWORD") && part.equals("leftArm")) {
                     target = new Vec3f(-1.2F, 0.3F, -0.5F * pulse);
                  }

                  if (this.pose.equals("DEFENSE") && part.equals("rightArm")) {
                     target = new Vec3f(-1.2F, -0.6F, 0.6F);
                  }
               }

               Vec3f result = new Vec3f(
                  Mth.lerp(weight, (Float)original.getX(), (Float)target.getX()),
                  Mth.lerp(weight, (Float)original.getY(), (Float)target.getY()),
                  Mth.lerp(weight, (Float)original.getZ(), (Float)target.getZ())
               );
               if (this.domain.equals("SLASHBLADE") && part.equals("leftArm") && this.phantomAge < this.phantomDuration + 4) {
                  float overlay = Mth.clamp((this.phantomDuration + 4 - this.phantomAge - this.delta) / 4.0F, 0.0F, 1.0F);
                  result = new Vec3f(
                     Mth.lerp(overlay, (Float)result.getX(), -1.15F),
                     Mth.lerp(overlay, (Float)result.getY(), 0.3F),
                     Mth.lerp(overlay, (Float)result.getZ(), -0.3F)
                  );
               }

               if (this.domain.equals("SLASHBLADE") && part.equals("rightArm") && this.guardAge < this.guardDuration + 4) {
                  float overlay = Mth.clamp((this.guardDuration + 4 - this.guardAge - this.delta) / 4.0F, 0.0F, 1.0F);
                  result = new Vec3f(
                     Mth.lerp(overlay, (Float)result.getX(), -1.2F),
                     Mth.lerp(overlay, (Float)result.getY(), -0.6F),
                     Mth.lerp(overlay, (Float)result.getZ(), 0.6F)
                  );
               }

               return result;
            } else {
               gun.setupAnim(partialTick);
               Vec3f valuex = gun.get3DTransform(part, type, partialTick, original);
               return Float.isFinite((Float)valuex.getX()) && Float.isFinite((Float)valuex.getY()) && Float.isFinite((Float)valuex.getZ()) ? valuex : original;
            }
         } else {
            float time = this.throwHolding ? 1.0F : Mth.clamp((this.throwAge + partialTick) / Math.max(1, this.throwDuration), 0.0F, 1.0F);
            float blend = Mth.clamp((this.throwDuration + 4 - this.throwAge - partialTick) / 4.0F, 0.0F, 1.0F);
            Vec3f targetx = part.equals("leftArm")
               ? new Vec3f(this.throwRelease ? -1.65F + time * 1.25F : -1.25F - time * 1.15F, 0.25F, -0.18F)
               : new Vec3f(this.throwRelease ? -0.06F * (1.0F - time) : 0.06F * time, -0.08F * (1.0F - time), 0.0F);
            return new Vec3f(
               Mth.lerp(blend, (Float)original.getX(), (Float)targetx.getX()),
               Mth.lerp(blend, (Float)original.getY(), (Float)targetx.getY()),
               Mth.lerp(blend, (Float)original.getZ(), (Float)targetx.getZ())
            );
         }
      }
   }
}

