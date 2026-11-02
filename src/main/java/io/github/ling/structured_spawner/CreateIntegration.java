package io.github.ling.structured_spawner;

import net.neoforged.fml.ModList;

/**
 * 与两个前置模组的对接点。
 * <p>
 * 现代线（1.21.1 / NeoForge）的软依赖检测统一用 {@code ModList.get().isLoaded(...)}，
 * 不要使用老 Forge 的 {@code @Optional}。
 */
public final class CreateIntegration {

    /** Create（机械动力）的 modId。 */
    public static final String CREATE = "create";
    /** Create: Mechanical Spawner（动力刷怪笼）的 modId。 */
    public static final String MECHANICAL_SPAWNER = "create_mechanical_spawner";

    private CreateIntegration() {
    }

    public static boolean isCreateLoaded() {
        return ModList.get().isLoaded(CREATE);
    }

    public static boolean isMechanicalSpawnerLoaded() {
        return ModList.get().isLoaded(MECHANICAL_SPAWNER);
    }
}
