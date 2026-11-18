package bincnpc.gun.mixin;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

public final class CompatMixinPlugin implements IMixinConfigPlugin {
   public void onLoad(String mixinPackage) {
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String target, String mixin) {
      if (mixin.endsWith("ExternalStoreInvoker") || mixin.endsWith("ExternalPortableMixin")) {
         try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(target, true);
            return mixin.endsWith("ExternalPortableMixin")
               ? node.methods.stream().anyMatch(method -> method.name.equals("makeAllPortable"))
               : node.methods
                  .stream()
                  .anyMatch(method -> method.name.equals("<init>") && method.desc.contains("Ljava/util/function/Predicate;Ljava/util/function/Predicate;"));
         } catch (IOException | ClassNotFoundException var5) {
            return false;
         }
      } else if (!mixin.contains(".compat.")) {
         return true;
      } else {
         try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(target, true);
            return mixin.endsWith("TaczBurstMixin")
               ? node.methods
                  .stream()
                  .anyMatch(
                     method -> method.name.equals("modifyProperty")
                        && method.desc.equals("(Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;")
                  )
               : true;
         } catch (IOException | ClassNotFoundException var4) {
            return false;
         }
      }
   }

   public void acceptTargets(Set<String> mine, Set<String> others) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
   }

   public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
   }
}
