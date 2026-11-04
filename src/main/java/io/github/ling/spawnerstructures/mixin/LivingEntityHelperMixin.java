package io.github.ling.spawnerstructures.mixin;

import com.oierbravo.create_mechanical_spawner.foundation.utility.LivingEntityHelper;
import io.github.ling.spawnerstructures.structure.StructureSpawnPools;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让动力刷怪笼的"随机刷怪"跟随刷新点所在结构的原版刷怪表。
 * <p>
 * 两处注入都只做一件事：把抽取来源换掉，然后复用原模组自己的生成逻辑，
 * 不复制 {@code +0.51} 偏移、随机朝向、{@code checkSpawnObstruction} 等细节。
 * 判定失败（未开启 / 不在结构内 / 结构没声明表）时直接 return，原方法照常执行。
 * <p>
 * {@code remap = false}：目标是模组自己的类，不参与 Minecraft 混淆映射；
 * NeoForge 1.20.2+ 运行时本就是官方名，无需 refmap。
 */
@Mixin(value = LivingEntityHelper.class, remap = false)
public abstract class LivingEntityHelperMixin {

    /**
     * 织入标记：仅用于运行时自检。
     * <p>
     * {@code @Unique} 保证不会与目标类的成员冲突；只要这个字段存在于目标类上，
     * 就说明 mixin 确实被应用了（启动日志会打印自检结果）。
     */
    @Unique
    private static final String csm$mixinMarker = "structure-aware-random";

    /** 无战利品收集器时：直接生成活体生物。 */
    @Inject(method = "spawnRandomLivingEntity", at = @At("HEAD"), cancellable = true)
    private static void csm$structureAwareSpawn(Level level, BlockPos pos, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        StructureSpawnPools.Pick pick = StructureSpawnPools.pick(serverLevel, pos);
        if (pick == null) {
            return;
        }
        if (pick instanceof StructureSpawnPools.Suppress) {
            ci.cancel();
            return;
        }
        EntityType<?> type = ((StructureSpawnPools.Spawn) pick).type();
        // 复用原方法：非 null 的实体键会走"固定实体"分支，生成流程一模一样。
        LivingEntityHelper.spawnLivingEntity(serverLevel, type.builtInRegistryHolder().key(), pos);
        ci.cancel();
    }

    /**
     * 有战利品收集器时：只创建实体用于结算掉落表。
     * <p>
     * 顺带修掉原实现里 {@code spawn.get()} 未判空的问题——在没有任何 MONSTER
     * 自然生成的群系（如蘑菇岛）原本会抛异常并被吞掉，导致"液体扣了但没产出"。
     */
    @Inject(method = "createRandomEntity", at = @At("HEAD"), cancellable = true)
    private static void csm$structureAwareCreate(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Entity> cir) {
        StructureSpawnPools.Pick pick = StructureSpawnPools.pick(level, pos);
        if (pick == null) {
            return;
        }
        if (pick instanceof StructureSpawnPools.Suppress) {
            cir.setReturnValue(null);
            return;
        }
        EntityType<?> type = ((StructureSpawnPools.Spawn) pick).type();
        cir.setReturnValue(type.create(level));
    }
}
