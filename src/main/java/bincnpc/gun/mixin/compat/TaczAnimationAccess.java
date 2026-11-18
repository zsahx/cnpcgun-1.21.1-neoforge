package bincnpc.gun.mixin.compat;

import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(
   targets = {"com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager"},
   remap = false
)
public interface TaczAnimationAccess {
   @Invoker("getAnimations")
   Optional<KeyframeAnimation> cnpcgun$animation(ResourceLocation var1, String var2);
}
