package net.minestom.server.network;

import com.google.gson.JsonObject;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.packet.client.play.ClientVehicleMovePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.common.DisconnectPacket;
import net.minestom.server.network.packet.server.common.PingResponsePacket;
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket;
import net.minestom.server.network.packet.server.login.LoginSuccessPacket;
import net.minestom.server.network.packet.server.login.SetCompressionPacket;
import net.minestom.server.network.packet.server.play.*;
import net.minestom.server.network.packet.server.status.ResponsePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.recipe.Ingredient;
import net.minestom.server.recipe.RecipeBookCategory;
import net.minestom.server.recipe.RecipeProperty;
import net.minestom.server.recipe.display.RecipeDisplay;
import net.minestom.server.recipe.display.SlotDisplay;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ensures that packet can be written and read correctly.
 */
public class PacketWriteReadTest {
    private static final List<ServerPacket> SERVER_PACKETS = new ArrayList<>();
    private static final List<ClientPacket> CLIENT_PACKETS = new ArrayList<>();

    private static final Component COMPONENT = Component.text("Hey");
    private static final Vec VEC = new Vec(5, 5, 5);

    @BeforeAll
    public static void setupServer() {
        MinecraftServer.init(); // Need some tags in here, pretty gross.

        // Handshake
        SERVER_PACKETS.add(new ResponsePacket(new JsonObject().toString()));
        // Status
        SERVER_PACKETS.add(new PingResponsePacket(5));
        // Login
        //SERVER_PACKETS.add(new EncryptionRequestPacket("server", generateByteArray(16), generateByteArray(16)));
        SERVER_PACKETS.add(new LoginDisconnectPacket(COMPONENT));
        //SERVER_PACKETS.add(new LoginPluginRequestPacket(5, "id", generateByteArray(16)));
        SERVER_PACKETS.add(new LoginSuccessPacket(new GameProfile(UUID.randomUUID(), "TheMode911")));
        SERVER_PACKETS.add(new SetCompressionPacket(256));
        // Play
        SERVER_PACKETS.add(new AcknowledgeBlockChangePacket(0));
        SERVER_PACKETS.add(new ActionBarPacket(COMPONENT));
        SERVER_PACKETS.add(new AttachEntityPacket(5, 10));
        SERVER_PACKETS.add(new BlockActionPacket(VEC, (byte) 5, (byte) 5, 5));
        SERVER_PACKETS.add(new BlockBreakAnimationPacket(5, VEC, (byte) 5));
        SERVER_PACKETS.add(new BlockChangePacket(VEC, 0));
        SERVER_PACKETS.add(new BlockEntityDataPacket(VEC, 5, CompoundBinaryTag.builder().putString("key", "value").build()));
        SERVER_PACKETS.add(new BossBarPacket(UUID.randomUUID(), new BossBarPacket.AddAction(COMPONENT, 5f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS, (byte) 2)));
        SERVER_PACKETS.add(new BossBarPacket(UUID.randomUUID(), new BossBarPacket.RemoveAction()));
        SERVER_PACKETS.add(new BossBarPacket(UUID.randomUUID(), new BossBarPacket.UpdateHealthAction(5f)));
        SERVER_PACKETS.add(new BossBarPacket(UUID.randomUUID(), new BossBarPacket.UpdateTitleAction(COMPONENT)));
        SERVER_PACKETS.add(new BossBarPacket(UUID.randomUUID(), new BossBarPacket.UpdateStyleAction(BossBar.Color.BLUE, BossBar.Overlay.PROGRESS)));
        SERVER_PACKETS.add(new BossBarPacket(UUID.randomUUID(), new BossBarPacket.UpdateFlagsAction((byte) 5)));
        SERVER_PACKETS.add(new CameraPacket(5));
        SERVER_PACKETS.add(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.RAIN_LEVEL_CHANGE, 2));
        SERVER_PACKETS.add(new SystemChatPacket(COMPONENT, false));
        SERVER_PACKETS.add(new ClearTitlesPacket(false));
        SERVER_PACKETS.add(new CloseWindowPacket((byte) 2));
        SERVER_PACKETS.add(new CollectItemPacket(5, 5, 5));
        var recipeDisplay = new RecipeDisplay.CraftingShapeless(
                List.of(new SlotDisplay.Item(Material.STONE)),
                new SlotDisplay.Item(Material.STONE_BRICKS),
                new SlotDisplay.Item(Material.CRAFTING_TABLE)
        );
        SERVER_PACKETS.add(new PlaceGhostRecipePacket(0, recipeDisplay));
        SERVER_PACKETS.add(new DeathCombatEventPacket(5, COMPONENT));
        SERVER_PACKETS.add(new DeclareRecipesPacket(Map.of(
                RecipeProperty.SMITHING_BASE, List.of(Material.STONE),
                RecipeProperty.SMITHING_TEMPLATE, List.of(Material.STONE),
                RecipeProperty.SMITHING_ADDITION, List.of(Material.STONE),
                RecipeProperty.FURNACE_INPUT, List.of(Material.STONE),
                RecipeProperty.BLAST_FURNACE_INPUT, List.of(Material.IRON_HOE, Material.DANDELION),
                RecipeProperty.SMOKER_INPUT, List.of(Material.STONE),
                RecipeProperty.CAMPFIRE_INPUT, List.of(Material.STONE)),
                List.of(new DeclareRecipesPacket.StonecutterRecipe(new Ingredient(Material.DIAMOND),
                        new SlotDisplay.ItemStack(ItemStack.of(Material.GOLD_BLOCK))))
        ));
        SERVER_PACKETS.add(new RecipeBookAddPacket(List.of(new RecipeBookAddPacket.Entry(1, recipeDisplay, null,
                RecipeBookCategory.CRAFTING_MISC, List.of(new Ingredient(Material.STONE)), true, true)), false));
        SERVER_PACKETS.add(new RecipeBookRemovePacket(List.of(1)));

        SERVER_PACKETS.add(new DestroyEntitiesPacket(List.of(5, 5, 5)));
        SERVER_PACKETS.add(new DisconnectPacket(COMPONENT));
        SERVER_PACKETS.add(new DisplayScoreboardPacket((byte) 5, "scoreboard"));
        SERVER_PACKETS.add(new WorldEventPacket(5, VEC, 5, false));
        SERVER_PACKETS.add(new EndCombatEventPacket(5));
        SERVER_PACKETS.add(new EnterCombatEventPacket());
        SERVER_PACKETS.add(new EntityAnimationPacket(5, EntityAnimationPacket.Animation.TAKE_DAMAGE));
        SERVER_PACKETS.add(new EntityEquipmentPacket(6, Map.of(EquipmentSlot.MAIN_HAND, ItemStack.of(Material.DIAMOND_SWORD))));
        SERVER_PACKETS.add(new EntityHeadLookPacket(5, 90f));
        SERVER_PACKETS.add(new EntityMetaDataPacket(5, Map.of()));
        SERVER_PACKETS.add(new EntityMetaDataPacket(5, Map.of(1, Metadata.VarInt(5))));
        SERVER_PACKETS.add(new EntityPositionAndRotationPacket(5, (short) 0, (short) 0, (short) 0, 45f, 45f, false));
        SERVER_PACKETS.add(new EntityPositionPacket(5, (short) 0, (short) 0, (short) 0, true));
        SERVER_PACKETS.add(new EntityAttributesPacket(5, List.of()));
        SERVER_PACKETS.add(new EntityRotationPacket(5, 45f, 45f, false));

        final PlayerSkin skin = new PlayerSkin("hh", "hh");
        List<PlayerInfoUpdatePacket.Property> prop = List.of(new PlayerInfoUpdatePacket.Property("textures", skin.textures(), skin.signature()));

        SERVER_PACKETS.add(new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.ADD_PLAYER,
                new PlayerInfoUpdatePacket.Entry(UUID.randomUUID(), "TheMode911", prop, false, 0, GameMode.SURVIVAL, null, null, 0)));
        SERVER_PACKETS.add(new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                new PlayerInfoUpdatePacket.Entry(UUID.randomUUID(), "", List.of(), false, 0, GameMode.SURVIVAL, Component.text("NotTheMode911"), null, 0)));
        SERVER_PACKETS.add(new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                new PlayerInfoUpdatePacket.Entry(UUID.randomUUID(), "", List.of(), false, 0, GameMode.CREATIVE, null, null, 0)));
        SERVER_PACKETS.add(new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                new PlayerInfoUpdatePacket.Entry(UUID.randomUUID(), "", List.of(), false, 20, GameMode.SURVIVAL, null, null, 0)));
        SERVER_PACKETS.add(new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                new PlayerInfoUpdatePacket.Entry(UUID.randomUUID(), "", List.of(), true, 0, GameMode.SURVIVAL, null, null, 0)));
        SERVER_PACKETS.add(new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER,
                new PlayerInfoUpdatePacket.Entry(UUID.randomUUID(), "", List.of(), false, 0, GameMode.SURVIVAL, null, null, 42)));
        SERVER_PACKETS.add(new PlayerInfoRemovePacket(UUID.randomUUID()));
    }

    @BeforeAll
    public static void setupClient() {
        CLIENT_PACKETS.add(new ClientHandshakePacket(755, "localhost", 25565, ClientHandshakePacket.Intent.LOGIN));
        CLIENT_PACKETS.add(new ClientVehicleMovePacket(new Pos(5, 5, 5, 45f, 45f), true));
        CLIENT_PACKETS.add(new ClientVehicleMovePacket(new Pos(6, 5, 6, 82f, 12.5f), false));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void serverTest() throws NoSuchFieldException, IllegalAccessException {
        for (var packet : SERVER_PACKETS) {
            var packetClass = packet.getClass();
            NetworkBuffer.Type<ServerPacket> serializer = (NetworkBuffer.Type<ServerPacket>) packetClass.getField("SERIALIZER").get(packetClass);
            testPacket(serializer, packet);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void clientTest() throws NoSuchFieldException, IllegalAccessException {
        for (var packet : CLIENT_PACKETS) {
            var packetClass = packet.getClass();
            NetworkBuffer.Type<ClientPacket> serializer = (NetworkBuffer.Type<ClientPacket>) packetClass.getField("SERIALIZER").get(packetClass);
            testPacket(serializer, packet);
        }
    }

    private static <T> void testPacket(NetworkBuffer.Type<T> networkType, T packet) {
        byte[] bytes = NetworkBuffer.makeArray(networkType, packet);
        NetworkBuffer reader = NetworkBuffer.resizableBuffer();
        reader.write(NetworkBuffer.RAW_BYTES, bytes);
        var createdPacket = networkType.read(reader);
        assertEquals(packet, createdPacket);
    }
}
