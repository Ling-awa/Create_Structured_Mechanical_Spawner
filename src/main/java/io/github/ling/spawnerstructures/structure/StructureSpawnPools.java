package io.github.ling.spawnerstructures.structure;

import io.github.ling.spawnerstructures.config.CsmConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 把「刷新点所在结构的刷怪表」翻译成一次随机抽取。
 * <p>
 * 数据来源的优先级（同一位置命中多个结构时，自定义池优先于原版表）：
 * <ol>
 *   <li>数据包自定义池 {@code structure_spawn_pool}（玩家可覆盖）</li>
 *   <li>原版 {@code spawn_overrides}（结构自己声明的表）</li>
 * </ol>
 * 被 {@code structureBlacklist} 列入的结构不会使用原版表，直接回退群系池；
 * 但它的自定义池仍然生效——即「先禁掉默认，再自己定义」。
 */
public final class StructureSpawnPools {

    /** 本次判定所用池子的来源。 */
    public enum Source {
        /** 数据包 {@code structure_spawn_pool} 提供的自定义池。 */
        CUSTOM,
        /** 结构自带的原版 {@code spawn_overrides}。 */
        VANILLA
    }

    /** 一次解析的结论；{@code null} 表示"不接管"，调用方应放行走原版逻辑。 */
    public sealed interface Pick permits Spawn, Suppress {
    }

    /** 用结构刷怪表抽到的生物。 */
    public record Spawn(EntityType<?> type, Candidate source) implements Pick {
    }

    /** 结构内禁止刷怪（刷怪表存在但为空，且配置为 SUPPRESS）。 */
    public record Suppress(Candidate source) implements Pick {
    }

    /** 某个"确实命中"的结构及其刷怪表，同时供调试命令展示。 */
    public record Candidate(ResourceLocation id,
                            Structure structure,
                            StructureSpawnOverride.BoundingBoxType boundingBox,
                            WeightedRandomList<MobSpawnSettings.SpawnerData> pool,
                            Source source) {
    }

    private StructureSpawnPools() {
    }

    /**
     * 解析 {@code pos} 所在结构并抽一只怪。
     *
     * @return {@code null} = 不接管（开关关闭 / 不在结构内 / 结构没声明表且没有自定义池）
     */
    @Nullable
    public static Pick pick(ServerLevel level, BlockPos pos) {
        if (!CsmConfig.structureAwareRandom()) {
            return null;
        }
        List<Candidate> candidates = candidates(level, pos);
        if (candidates.isEmpty()) {
            return null;
        }
        Candidate chosen = candidates.get(0);
        if (chosen.pool().isEmpty()) {
            if (CsmConfig.emptyOverrideBehavior() == CsmConfig.EmptyOverrideBehavior.SUPPRESS) {
                return new Suppress(chosen);
            }
            return null;
        }
        Optional<MobSpawnSettings.SpawnerData> rolled = roll(chosen.pool(), level.random);
        return rolled.<Pick>map(data -> new Spawn(data.type, chosen)).orElse(null);
    }

    /**
     * 从池子里抽一个。
     * <p>
     * 原版的 {@code weight} 抽的是"一次刷新事件"，事件内再按 {@code minCount~maxCount} 成组生成；
     * 而刷怪笼一个周期只产 1 只。直接沿用原始权重会按【事件占比】抽取，
     * 导致"权重低但成组数量大"的生物被系统性压低。
     * <p>
     * 开启 {@code weightBySpawnCount} 时改用【个体占比】：
     * {@code 有效权重 = weight × (minCount + maxCount)}，
     * 等价于 {@code 2 × weight × 平均数量}（全体同乘 2，不影响分布，且避免浮点）。
     */
    private static Optional<MobSpawnSettings.SpawnerData> roll(
            WeightedRandomList<MobSpawnSettings.SpawnerData> pool, RandomSource random) {
        List<MobSpawnSettings.SpawnerData> entries = pool.unwrap();
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        if (!CsmConfig.weightBySpawnCount()) {
            return pool.getRandom(random);
        }

        int[] effective = new int[entries.size()];
        int total = 0;
        for (int i = 0; i < entries.size(); i++) {
            MobSpawnSettings.SpawnerData data = entries.get(i);
            int weight = Math.max(0, data.getWeight().asInt());
            int count = Math.max(0, data.minCount + data.maxCount);
            effective[i] = weight * count;
            total += effective[i];
        }
        if (total <= 0) {
            // 全是 0 数量/0 权重时退回原始权重，至少还能抽出东西
            return pool.getRandom(random);
        }

        int roll = random.nextInt(total);
        for (int i = 0; i < entries.size(); i++) {
            roll -= effective[i];
            if (roll < 0) {
                return Optional.of(entries.get(i));
            }
        }
        return Optional.of(entries.get(entries.size() - 1));
    }

    /**
     * 列出 {@code pos} 处所有"确实生效"的刷怪池，按「自定义优先、其次结构 id」排序。
     * <p>
     * 原版表的生效判定跟随数据里的 {@code bounding_box}：
     * <ul>
     *   <li>{@code STRUCTURE}（JSON 的 {@code full}）：点在结构包围盒内即可</li>
     *   <li>{@code PIECE}（JSON 的 {@code piece}）：点必须落在某一块拼图 piece 内</li>
     * </ul>
     * 自定义池没有 {@code bounding_box} 概念，按「包围盒或任一块拼图命中」宽松判定。
     */
    public static List<Candidate> candidates(ServerLevel level, BlockPos pos) {
        StructureManager manager = level.structureManager();
        Map<Structure, ?> all = manager.getAllStructuresAt(pos);
        if (all.isEmpty()) {
            return List.of();
        }
        List<Candidate> out = new ArrayList<>();
        for (Structure structure : all.keySet()) {
            ResourceLocation id = idOf(level, structure);

            WeightedRandomList<MobSpawnSettings.SpawnerData> custom = CustomStructurePools.poolFor(id);
            if (custom != null) {
                if (!insideAny(manager, pos, structure)) {
                    continue;
                }
                out.add(new Candidate(id, structure, StructureSpawnOverride.BoundingBoxType.STRUCTURE, custom, Source.CUSTOM));
                continue;
            }

            if (isBlacklisted(level, structure, id)) {
                continue;
            }

            StructureSpawnOverride override = structure.spawnOverrides().get(MobCategory.MONSTER);
            if (override == null) {
                continue;
            }
            if (!contains(manager, pos, structure, override.boundingBox())) {
                continue;
            }
            out.add(new Candidate(id, structure, override.boundingBox(), override.spawns(), Source.VANILLA));
        }
        out.sort(Comparator.<Candidate>comparingInt(candidate ->
                        candidate.source() == Source.CUSTOM ? 0 : 1)
                .thenComparing(candidate -> candidate.id().toString()));
        return out;
    }

    /**
     * 该位置**真正命中**的结构 id（按包围盒或拼图判定），供调试命令使用。
     * <p>
     * 不能直接用 {@code getAllStructuresAt}：那是"该区块引用了哪些结构"，范围是整个结构占地，
     * 高空位置同样会返回，用它会让命令给出误导性结果。
     */
    public static List<ResourceLocation> structuresAt(ServerLevel level, BlockPos pos) {
        StructureManager manager = level.structureManager();
        return manager.getAllStructuresAt(pos).keySet().stream()
                .filter(structure -> insideAny(manager, pos, structure))
                .map(structure -> idOf(level, structure))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    /**
     * 供配方条件使用：判断 {@code pos} 是否落在指定结构内。
     * <p>
     * 比刷怪池的判定宽松一档——包围盒命中或任一块拼图命中都算，
     * 因为配方条件表达的是"在哪能用"，不该被 piece 边界卡住。
     */
    public static boolean insideStructure(ServerLevel level, BlockPos pos, ResourceLocation structureId) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structureId);
        if (structure == null) {
            return false;
        }
        return insideAny(level.structureManager(), pos, structure);
    }

    /** 同上，但按结构标签匹配。 */
    public static boolean insideStructureTag(ServerLevel level, BlockPos pos, TagKey<Structure> tag) {
        Optional<HolderSet.Named<Structure>> holders =
                level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(tag);
        if (holders.isEmpty()) {
            return false;
        }
        StructureManager manager = level.structureManager();
        for (Holder<Structure> holder : holders.get()) {
            if (insideAny(manager, pos, holder.value())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 配方条件用的判定位置。
     * <p>
     * 用方块实体自身坐标：刷怪笼的刷新点最多比本体高 {@code maxRange} 格（默认 2），
     * 判定差异可以忽略；不引用 {@code SpawnerBlockEntity} 也避免了把 Ponder
     * （只以 jar-in-jar 存在于 Create 内部）拖进编译期依赖。
     */
    public static BlockPos requirementPos(BlockEntity blockEntity) {
        return blockEntity.getBlockPos();
    }

    /** 结构黑名单：id 按名字、{@code #tag} 按标签匹配。 */
    private static boolean isBlacklisted(ServerLevel level, Structure structure, ResourceLocation id) {
        List<String> blacklist = CsmConfig.structureBlacklist();
        if (blacklist.isEmpty()) {
            return false;
        }
        for (String entry : blacklist) {
            if (entry.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(entry.substring(1));
                if (tagId == null) {
                    continue;
                }
                Holder<Structure> holder = level.registryAccess()
                        .registryOrThrow(Registries.STRUCTURE)
                        .wrapAsHolder(structure);
                if (holder.is(TagKey.create(Registries.STRUCTURE, tagId))) {
                    return true;
                }
            } else {
                ResourceLocation blacklistedId = ResourceLocation.tryParse(entry);
                if (blacklistedId != null && blacklistedId.equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 是否命中结构。注意：{@code StructureManager} 的两个查询在"未命中"时返回的是
     * {@link StructureStart#INVALID_START} 这个非 null 哨兵，**必须用 {@code isValid()} 判断**，
     * 否则 {@code != null} 永远成立——那会让结构正上方任意高度都被判成"在结构里"。
     */
    private static boolean contains(StructureManager manager, BlockPos pos, Structure structure,
                                    StructureSpawnOverride.BoundingBoxType boundingBox) {
        if (boundingBox == StructureSpawnOverride.BoundingBoxType.PIECE) {
            return manager.getStructureWithPieceAt(pos, structure).isValid();
        }
        return manager.getStructureAt(pos, structure).isValid();
    }

    /** 包围盒命中或任一块拼图命中都算。 */
    private static boolean insideAny(StructureManager manager, BlockPos pos, Structure structure) {
        return manager.getStructureAt(pos, structure).isValid()
                || manager.getStructureWithPieceAt(pos, structure).isValid();
    }

    private static ResourceLocation idOf(ServerLevel level, Structure structure) {
        ResourceLocation id = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getKey(structure);
        return id != null ? id : ResourceLocation.withDefaultNamespace("unknown");
    }
}
