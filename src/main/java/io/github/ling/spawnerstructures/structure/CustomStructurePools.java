package io.github.ling.spawnerstructures.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ling.spawnerstructures.MechanicalSpawnerStructures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据包自定义结构刷怪池。
 * <p>
 * 文件位置：{@code data/<命名空间>/structure_spawn_pool/<任意名>.json}
 * <pre>{@code
 * {
 *   "structures": ["minecraft:fortress", "minecraft:bastion_remnant"],
 *   "pool": [
 *     { "type": "minecraft:piglin",  "weight": 10, "minCount": 1, "maxCount": 3 },
 *     { "type": "minecraft:hoglin",  "weight": 4,  "minCount": 1, "maxCount": 2 }
 *   ]
 * }
 * }</pre>
 * 命中该结构的刷怪笼会优先使用这里的池子，盖过原版 {@code spawn_overrides}。
 * 这样配合 {@code structureBlacklist} 就能做到：先禁掉默认行为，再自己定义。
 */
@EventBusSubscriber(modid = MechanicalSpawnerStructures.MODID)
public final class CustomStructurePools extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();

    /** 数据包解析失败不应当让整合包崩溃，只记日志。 */
    private static volatile Map<ResourceLocation, WeightedRandomList<MobSpawnSettings.SpawnerData>> pools = Map.of();

    public CustomStructurePools() {
        super(GSON, "structure_spawn_pool");
    }

    /** 单个数据包文件的形态。 */
    public record PoolFile(List<ResourceLocation> structures, List<MobSpawnSettings.SpawnerData> pool) {
        public static final Codec<PoolFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.listOf().fieldOf("structures").forGetter(PoolFile::structures),
                MobSpawnSettings.SpawnerData.CODEC.listOf().fieldOf("pool").forGetter(PoolFile::pool)
        ).apply(instance, PoolFile::new));
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CustomStructurePools());
    }

    /** 该结构是否有玩家自定义池；没有则返回 {@code null}。 */
    @Nullable
    public static WeightedRandomList<MobSpawnSettings.SpawnerData> poolFor(ResourceLocation structureId) {
        return pools.get(structureId);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, WeightedRandomList<MobSpawnSettings.SpawnerData>> parsed = new HashMap<>();

        // 按文件 id 排序，保证同一结构被多个文件声明时结果稳定（后写覆盖先写）。
        List<ResourceLocation> order = new ArrayList<>(files.keySet());
        Collections.sort(order);

        for (ResourceLocation fileId : order) {
            DataResult<PoolFile> result = PoolFile.CODEC.parse(JsonOps.INSTANCE, files.get(fileId));
            result.resultOrPartial(error ->
                            LOGGER.error("[{}] 无法解析自定义结构池 {}：{}", MechanicalSpawnerStructures.MODID, fileId, error))
                    .ifPresent(poolFile -> {
                        if (poolFile.structures().isEmpty() || poolFile.pool().isEmpty()) {
                            LOGGER.warn("[{}] 自定义结构池 {} 为空，已忽略", MechanicalSpawnerStructures.MODID, fileId);
                            return;
                        }
                        WeightedRandomList<MobSpawnSettings.SpawnerData> list =
                                WeightedRandomList.create(poolFile.pool());
                        for (ResourceLocation structureId : poolFile.structures()) {
                            parsed.put(structureId, list);
                        }
                        LOGGER.info("[{}] 载入自定义结构池 {} → {} 个结构", MechanicalSpawnerStructures.MODID, fileId,
                                poolFile.structures().size());
                    });
        }

        pools = Map.copyOf(parsed);
        if (!pools.isEmpty()) {
            LOGGER.info("[{}] 共载入 {} 条自定义结构池", MechanicalSpawnerStructures.MODID, pools.size());
        }
    }
}
