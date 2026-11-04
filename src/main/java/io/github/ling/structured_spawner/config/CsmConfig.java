package io.github.ling.structured_spawner.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 服务端配置。
 * <p>
 * 只有 {@link #STRUCTURE_AWARE_RANDOM} 关闭时，本模组对动力刷怪笼的唯一侵入点
 * （{@code LivingEntityHelper} 的两处注入）会直接放行，行为与未安装本模组时一致。
 */
public final class CsmConfig {

    /** 结构声明了 monster 刷怪表、但列表为空时的处理方式。 */
    public enum EmptyOverrideBehavior {
        /** 结构内不刷怪（原版语义：远古城市、试炼密室就是这样禁掉自然刷怪的）。 */
        SUPPRESS,
        /** 忽略该空表，回退到群系刷怪池。 */
        FALLBACK
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue STRUCTURE_AWARE_RANDOM = BUILDER
            .comment("结构感知随机刷怪总开关。",
                    "开启后，随机刷怪的抽取来源会优先跟随刷新点所在结构的 monster 刷怪表。",
                    "关闭后完全恢复动力刷怪笼的原版随机逻辑（按生物群系抽取）。")
            .define("structureAwareRandom", true);

    public static final ModConfigSpec.EnumValue<EmptyOverrideBehavior> EMPTY_OVERRIDE = BUILDER
            .comment("结构声明了 monster 刷怪表但列表为空时（如远古城市、试炼密室）的行为：",
                    "SUPPRESS = 该结构内不刷怪；FALLBACK = 回退群系池。")
            .defineEnum("emptySpawnOverride", EmptyOverrideBehavior.SUPPRESS);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_BLACKLIST = BUILDER
            .comment("结构黑名单：列入的结构不再使用原版刷怪表（spawn_overrides），直接回退群系池。",
                    "写法：结构 id（如 minecraft:fortress）或结构标签（如 #minecraft:village）。",
                    "若同时用数据包 structure_spawn_pool 给该结构定义了自定义池，自定义池仍然生效——",
                    "这样就等于「先禁掉默认行为，再自己定义」。")
            .defineListAllowEmpty("structureBlacklist", List.of(),
                    () -> "minecraft:fortress",
                    CsmConfig::isValidBlacklistEntry);

    public static final ModConfigSpec.BooleanValue WEIGHT_BY_SPAWN_COUNT = BUILDER
            .comment("按「权重 × 平均刷新数量」计算抽取概率。",
                    "原版按 weight 抽的是一次刷新【事件】，事件里再刷 minCount~maxCount 只；",
                    "而刷怪笼一个周期只产 1 只。开启后按个体数量还原占比：",
                    "   有效权重 = weight × (minCount + maxCount)",
                    "关闭则直接使用原始 weight（按事件占比抽取）。")
            .define("weightBySpawnCount", true);

    public static final ModConfigSpec.BooleanValue LOG_RESOLUTION = BUILDER
            .comment("在日志中打印结构池解析结果，用于排查判定问题。")
            .define("logStructureResolution", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CsmConfig() {
    }

    /** 配置未加载（例如专用服务器早期阶段）时一律视为关闭，保证不会误伤原版行为。 */
    public static boolean structureAwareRandom() {
        return SPEC.isLoaded() && STRUCTURE_AWARE_RANDOM.get();
    }

    public static EmptyOverrideBehavior emptyOverrideBehavior() {
        return SPEC.isLoaded() ? EMPTY_OVERRIDE.get() : EmptyOverrideBehavior.SUPPRESS;
    }

    public static boolean logResolution() {
        return SPEC.isLoaded() && LOG_RESOLUTION.get();
    }

    public static boolean weightBySpawnCount() {
        return SPEC.isLoaded() && WEIGHT_BY_SPAWN_COUNT.get();
    }

    public static List<String> structureBlacklist() {
        return SPEC.isLoaded() ? List.copyOf(STRUCTURE_BLACKLIST.get()) : List.of();
    }

    private static boolean isValidBlacklistEntry(Object value) {
        if (!(value instanceof String entry) || entry.isBlank()) {
            return false;
        }
        String raw = entry.startsWith("#") ? entry.substring(1) : entry;
        return net.minecraft.resources.ResourceLocation.tryParse(raw) != null;
    }
}
