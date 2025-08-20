package net.minestom.server.instance;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.identity.Identified;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.pointer.Pointered;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.Tickable;
import net.minestom.server.adventure.audience.PacketGroupingAudience;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventHandler;
import net.minestom.server.event.trait.InstanceEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.generator.Generator;
import net.minestom.server.network.packet.server.play.InitializeWorldBorderPacket;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.snapshot.Snapshotable;
import net.minestom.server.tag.Taggable;
import net.minestom.server.timer.Schedulable;
import net.minestom.server.world.DimensionType;
import net.minestom.server.world.biome.Biome;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ExpInstance extends Block.Getter, Block.Setter, Tickable, Schedulable, Snapshotable,
        EventHandler<InstanceEvent>, Taggable, PacketGroupingAudience, Pointered, Identified {
    void scheduleNextTick(@NotNull Consumer<ExpInstance> callback);

    @ApiStatus.Internal
    default boolean placeBlock(@NotNull BlockHandler.Placement placement) {
        return placeBlock(placement, true);
    }

    @ApiStatus.Internal
    boolean placeBlock(@NotNull BlockHandler.Placement placement, boolean doBlockUpdates);

    @ApiStatus.Internal
    default boolean breakBlock(@NotNull Player player, @NotNull Point blockPosition, @NotNull BlockFace blockFace) {
        return breakBlock(player, blockPosition, blockFace, true);
    }

    @ApiStatus.Internal
    boolean breakBlock(@NotNull Player player,
                       @NotNull Point blockPosition,
                       @NotNull BlockFace blockFace,
                       boolean doBlockUpdates);

    boolean isRegistered();

    RegistryKey<DimensionType> getDimensionType();

    @ApiStatus.Internal
    default @NotNull DimensionType getCachedDimensionType() {
        return Objects.requireNonNull(MinecraftServer.getDimensionTypeRegistry().get(getDimensionType()));
    }

    @NotNull String getDimensionName();

    void setWorldAge(long worldAge);

    long getWorldAge();

    void setTime(long time);

    long getTime();

    void setTimeRate(int timeRate);

    long getTimeRate();

    int getTimeSynchronizationTicks();

    void setTimeSynchronizationTicks(int timeSynchronizationTicks);

    @NotNull WorldBorder getWorldBorder();

    void setWorldBorder(@NotNull WorldBorder worldBorder, double transitionTime);

    default void setWorldBorder(@NotNull WorldBorder worldBorder) {
        setWorldBorder(worldBorder, 0);
    }

    @NotNull InitializeWorldBorderPacket createInitializeWorldBorderPacket();

    @NotNull UUID getUuid();

    @NotNull Weather getWeather();

    void setWeather(@NotNull Weather weather, int transitionTicks);

    void setWeather(@NotNull Weather weather);

    void explode(float centerX, float centerY, float centerZ, float strength);

    void explode(float centerX, float centerY, float centerZ, float strength, @Nullable CompoundBinaryTag additionalData);

    void setExplosionSupplier(@Nullable ExplosionSupplier supplier);

    @Nullable ExplosionSupplier getExplosionSupplier();

    interface Entities extends PacketGroupingAudience, Snapshotable {
        @NotNull EntityTracker getEntityTracker();

        @NotNull Set<@NotNull Entity> getEntities();

        @Nullable Entity getEntityById(int id);

        @Nullable Entity getEntityByUuid(UUID uuid);

        @Nullable Player getPlayerByUuid(UUID uuid);

        @NotNull Set<@NotNull Entity> getChunkEntities(Chunk chunk);

        @NotNull Collection<Entity> getNearbyEntities(@NotNull Point point, double range);

        @UnmodifiableView
        Set<BossBar> bossBars();

        void playSoundExcept(@Nullable Player excludedPlayer, @NotNull Sound sound, @NotNull Point point);

        void playSoundExcept(@Nullable Player excludedPlayer, @NotNull Sound sound, double x, double y, double z);

        void playSoundExcept(@Nullable Player excludedPlayer, @NotNull Sound sound, Sound.@NotNull Emitter emitter);
    }

    interface Chunks extends Block.Getter, Block.Setter, Biome.Getter, Biome.Setter, Snapshotable {
        @Override
        default void setBlock(int x, int y, int z, @NotNull Block block) {
            setBlock(x, y, z, block, true);
        }

        default void setBlock(@NotNull Point blockPosition, @NotNull Block block, boolean doBlockUpdates) {
            setBlock(blockPosition.blockX(), blockPosition.blockY(), blockPosition.blockZ(), block, doBlockUpdates);
        }

        void setBlock(int x, int y, int z, @NotNull Block block, boolean doBlockUpdates);

        void sendBlockAction(@NotNull Point blockPosition, byte actionId, byte actionParam);

        int getBlockLight(int blockX, int blockY, int blockZ);

        int getSkyLight(int blockX, int blockY, int blockZ);

        boolean isInVoid(@NotNull Point point);

        // Chunk functions
        void enableAutoChunkLoad(boolean enable);

        boolean hasEnabledAutoChunkLoad();

        @NotNull CompletableFuture<@NotNull ExpChunk> loadChunk(int chunkX, int chunkZ);

        @NotNull CompletableFuture<@Nullable ExpChunk> loadOptionalChunk(int chunkX, int chunkZ);

        void unloadChunk(@NotNull ExpChunk chunk);

        @Nullable ExpChunk getChunk(int chunkX, int chunkZ);

        @NotNull Collection<@NotNull Chunk> getChunks();

        boolean isChunkLoaded(int chunkX, int chunkZ);

        // Owned Chunks functions
        boolean ownsChunks();

        @NotNull CompletableFuture<Void> saveChunkToStorage(@NotNull Chunk chunk);

        @NotNull CompletableFuture<Void> saveChunksToStorage();

        default void setChunkLoader(@NotNull IChunkLoader chunkLoader) {
            throw new UnsupportedOperationException("Chunk loader API can only be used if chunks are owned");
        }

        @NotNull IChunkLoader getChunkLoader();

        void setGenerator(@Nullable Generator generator);

        @Nullable Generator getGenerator();
    }
}
