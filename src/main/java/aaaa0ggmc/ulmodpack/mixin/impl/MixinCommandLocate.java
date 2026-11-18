package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.locate.LocateHelper;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandLocate;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 改进原版 {@code /locate}：区分沙漠神殿 / 丛林神庙 / 女巫小屋 / 雪屋，
 * 使 {@code /locate Temple} 不再误匹配雪屋或女巫小屋，同时补充细分 Tab 补全与点击传送。
 */
@Mixin(value = CommandLocate.class, remap = false)
public abstract class MixinCommandLocate {

    @Inject(
            method = "func_184881_a(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ulmodpack$handleScatteredLocate(MinecraftServer server, ICommandSender sender, String[] args,
                                                 CallbackInfo ci) throws CommandException {
        if (args.length == 1 && LocateHelper.tryHandleLocate(sender, args[0])) {
            ci.cancel();
        }
    }

    @Inject(
            method = "func_184883_a(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/command/ICommandSender;[Ljava/lang/String;Lnet/minecraft/util/math/BlockPos;)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ulmodpack$scatteredTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                                   @Nullable BlockPos targetPos,
                                                   CallbackInfoReturnable<List<String>> cir) {
        if (args.length == 1) {
            cir.setReturnValue(LocateHelper.getTabCompletions(args));
        }
    }
}
