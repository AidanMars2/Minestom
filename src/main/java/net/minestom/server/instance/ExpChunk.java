package net.minestom.server.instance;

import net.minestom.server.Tickable;
import net.minestom.server.Viewable;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.heightmap.Heightmap;
import net.minestom.server.snapshot.Snapshotable;
import net.minestom.server.tag.Taggable;
import net.minestom.server.world.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public interface ExpChunk extends Block.Getter, Block.Setter, Biome.Getter, Biome.Setter,
        Viewable, Tickable, Taggable, Snapshotable {
    // Block Manipulation
    void setBlock(int x, int y, int z, @NotNull Block block,
                  @Nullable BlockHandler.Placement placement,
                  @Nullable BlockHandler.Destroy destroy);

    void reset();

    @NotNull Chunk copy(@NotNull Instance instance, int chunkX, int chunkZ);

    // Chunk info
    @NotNull Heightmap motionBlockingHeightmap();

    @NotNull Heightmap worldSurfaceHeightmap();

    @NotNull UUID getUuid();

    @NotNull Instance getInstance();

    int getChunkX();

    int getChunkZ();

    int getMinSection();

    int getMaxSection();

    @NotNull Point toPosition();

    // Writing privileges
    void setReadOnly(boolean readOnly);

    boolean isReadOnly();

    // Chunk loading
    boolean isLoaded();

    void unload();

    void onLoad();

    boolean shouldGenerate();

    void onGenerate();

    // Packet stuff
    void sendChunk(@NotNull Player player);

    void sendChunk();

    void invalidate();

    // Owned only
    boolean ();

    void invalidateSection(int section);

    @NotNull Section getSection(int section);

    @NotNull List<Section> getSections();
}
