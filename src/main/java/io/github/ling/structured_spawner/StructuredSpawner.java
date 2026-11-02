package io.github.ling.structured_spawner;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * 本模组是 Create: Mechanical Spawner（动力刷怪笼）的附属模组，
 * 同时依赖 Create（机械动力）提供旋转动力与配方体系。
 * <p>
 * 前置依赖在 {@code META-INF/neoforge.mods.toml} 里声明为 mandatory，
 * 加载器会在缺失时报错并阻止进入游戏。
 */
@Mod(StructuredSpawner.MODID)
public class StructuredSpawner {

    /** 必须与 gradle.properties 的 mod_id、neoforge.mods.toml 的 modId 保持一致。 */
    public static final String MODID = "create_structured_mechanical_spawner";

    private static final Logger LOGGER = LogUtils.getLogger();

    public StructuredSpawner(IEventBus modEventBus) {
        // mod bus（IEventBus）：模组生命周期事件与 DeferredRegister 的注册挂载点。
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[{}] common setup | create={} | mechanical_spawner={}",
                MODID,
                CreateIntegration.isCreateLoaded(),
                CreateIntegration.isMechanicalSpawnerLoaded());
    }
}
