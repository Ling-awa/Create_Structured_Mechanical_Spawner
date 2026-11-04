package io.github.ling.spawnerstructures;

import com.mojang.logging.LogUtils;
import com.oierbravo.create_mechanical_spawner.foundation.utility.LivingEntityHelper;
import io.github.ling.spawnerstructures.config.CsmConfig;
import io.github.ling.spawnerstructures.requirement.CsmRequirements;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * 本模组是 Create: Mechanical Spawner（动力刷怪笼）的附属模组，
 * 同时依赖 Create（机械动力）提供旋转动力与配方体系。
 * <p>
 * 前置依赖在 {@code META-INF/neoforge.mods.toml} 里声明为 mandatory，
 * 加载器会在缺失时报错并阻止进入游戏。
 */
@Mod(MechanicalSpawnerStructures.MODID)
public class MechanicalSpawnerStructures {

    /** 必须与 gradle.properties 的 mod_id、neoforge.mods.toml 的 modId 保持一致。 */
    public static final String MODID = "create_mechanical_spawner_structures";

    private static final Logger LOGGER = LogUtils.getLogger();

    public MechanicalSpawnerStructures(IEventBus modEventBus, ModContainer modContainer) {
        // mod bus（IEventBus）：模组生命周期事件与 DeferredRegister 的注册挂载点。
        modEventBus.addListener(this::commonSetup);

        // 服务端配置：结构感知随机刷怪的开关、空表策略、调试日志。
        modContainer.registerConfig(ModConfig.Type.SERVER, CsmConfig.SPEC);

        // 配方条件：structure / structure_tag（挂到 Mechanicals 的条件注册表上）。
        CsmRequirements.register(modEventBus);

        // 运行时事件（服务端启动完成后打印生效配置）。
        NeoForge.EVENT_BUS.register(this);

        // 启动期主动触发一次注入目标的类加载 + 自检：让 mixin 配置错误在启动日志里就暴露，
        // 而不是等到玩家第一次站在结构里触发随机刷怪时才炸。
        if (mixinApplied()) {
            LOGGER.info("[{}] structure-aware random mixin applied on {}", MODID, LivingEntityHelper.class.getName());
        } else {
            LOGGER.error("[{}] structure-aware random mixin NOT applied — falling back to vanilla random spawning", MODID);
        }
    }

    /** 目标类上存在 {@code @Unique} 标记字段，即说明 mixin 已织入。 */
    private static boolean mixinApplied() {
        try {
            LivingEntityHelper.class.getDeclaredField("csm$mixinMarker");
            return true;
        } catch (NoSuchFieldException | SecurityException e) {
            return false;
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[{}] common setup | create={} | mechanical_spawner={}",
                MODID,
                CreateIntegration.isCreateLoaded(),
                CreateIntegration.isMechanicalSpawnerLoaded());
    }

    /**
     * 服务端配置在 mod 加载之后才读入，所以开关的实际值要等到服务端启动完成再打印。
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("[{}] structureAwareRandom={} | emptySpawnOverride={}",
                MODID,
                CsmConfig.structureAwareRandom(),
                CsmConfig.emptyOverrideBehavior());
    }
}
