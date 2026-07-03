package earth.code.universe.asmp.mixin;

import earth.code.universe.asmp.Event;
import net.minecraft.advancements.Advancement;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {

    @Unique
    private static final Logger log = LoggerFactory.getLogger(PlayerAdvancementsMixin.class);

    @Shadow
    private ServerPlayer player;

    @Inject(
            method = "award",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"
            )
    )
    private void onAward(
            Advancement advancement,
            String criterion,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (advancement.getDisplay() == null) {
            return;
        }

        Event.EventHandler.onAdvancement(player, advancement);
    }
}