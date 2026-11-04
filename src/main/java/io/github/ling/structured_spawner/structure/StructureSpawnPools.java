package io.github.ling.structured_spawner.structure;

import io.github.ling.structured_spawner.config.CsmConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 把「刷新点所在结构的原版刷怪表」翻译成一次随机抽取。
 * <p>
 * 只读取原版数据（{@link Structure#spawnOverrides()}），不内置任何生物映射表：
 * 结构声明了什么，这里就只能抽到什么；结构没声明，就交回原版群系逻辑。
 */
public final class StructureSpawnPools {

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
                            WeightedRandomList<MobSpawnSettings.SpawnerData> pool) {
    }

    private StructureSpawnPools() {
    }

    /**
     * 解析 {@code pos} 所在结构并抽一只怪。
     *
     * @return {@code null} = 不接管（开关关闭 / 不在结构内 / 结构没声明 monster 刷怪表）
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
        Optional<MobSpawnSettings.SpawnerData> rolled = chosen.pool().getRandom(level.random);
        return rolled.<Pick>map(data -> new Spawn(data.type, chosen)).orElse(null);
    }

    /**
     * 列出 {@code pos} 处所有"确实生效"的结构刷怪表。
     * <p>
     * 生效判定跟随原版数据里的 {@code bounding_box}：
     * <ul>
     *   <li>{@code STRUCTURE}（JSON 的 {@code full}）：点在结构包围盒内即可</li>
     *   <li>{@code PIECE}（JSON 的 {@code piece}）：点必须落在某一块拼图 piece 内</li>
     * </ul>
     * 结果按结构 id 排序，保证同一位置解析结果确定。
     */
    public static List<Candidate> candidates(ServerLevel level, BlockPos pos) {
        StructureManager manager = level.structureManager();
        Map<Structure, ?> all = manager.getAllStructuresAt(pos);
        if (all.isEmpty()) {
            return List.of();
        }
        List<Candidate> out = new ArrayList<>();
        for (Structure structure : all.keySet()) {
            StructureSpawnOverride override = structure.spawnOverrides().get(MobCategory.MONSTER);
            if (override == null) {
                continue;
            }
            if (!contains(manager, pos, structure, override.boundingBox())) {
                continue;
            }
            out.add(new Candidate(idOf(level, structure), structure, override.boundingBox(), override.spawns()));
        }
        out.sort(Comparator.comparing(candidate -> candidate.id().toString()));
        return out;
    }

    /** 该位置命中的所有结构 id（不论有没有刷怪表），供调试命令使用。 */
    public static List<ResourceLocation> structuresAt(ServerLevel level, BlockPos pos) {
        return level.structureManager().getAllStructuresAt(pos).keySet().stream()
                .map(structure -> idOf(level, structure))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    private static boolean contains(StructureManager manager, BlockPos pos, Structure structure,
                                    StructureSpawnOverride.BoundingBoxType boundingBox) {
        if (boundingBox == StructureSpawnOverride.BoundingBoxType.PIECE) {
            return manager.getStructureWithPieceAt(pos, structure) != null;
        }
        StructureStart start = manager.getStructureAt(pos, structure);
        return start != null && start.isValid();
    }

    /**
     * 供配方条件使用：判断 {@code pos} 是否落在指定结构内。
     * <p>
     * 这里比刷怪池的判定宽松一档——包围盒命中或任一块拼图命中都算，
     * 因为配方条件表达的是"在哪能用"，不该被 piece 边界卡住。
     */
    public static boolean insideStructure(ServerLevel level, BlockPos pos, ResourceLocation structureId) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structureId);
        if (structure == null) {
            return false;
        }
        StructureManager manager = level.structureManager();
        return manager.getStructureAt(pos, structure) != null
                || manager.getStructureWithPieceAt(pos, structure) != null;
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
            Structure structure = holder.value();
            if (manager.getStructureAt(pos, structure) != null
                    || manager.getStructureWithPieceAt(pos, structure) != null) {
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

    private static ResourceLocation idOf(ServerLevel level, Structure structure) {
        ResourceLocation id = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getKey(structure);
        return id != null ? id : ResourceLocation.withDefaultNamespace("unknown");
    }
}
