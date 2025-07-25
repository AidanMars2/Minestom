package net.minestom.server.network.packet;

import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.common.*;
import net.minestom.server.network.packet.client.configuration.ClientFinishConfigurationPacket;
import net.minestom.server.network.packet.client.configuration.ClientSelectKnownPacksPacket;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.packet.client.login.ClientEncryptionResponsePacket;
import net.minestom.server.network.packet.client.login.ClientLoginAcknowledgedPacket;
import net.minestom.server.network.packet.client.login.ClientLoginPluginResponsePacket;
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket;
import net.minestom.server.network.packet.client.play.*;
import net.minestom.server.network.packet.client.status.StatusRequestPacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.common.*;
import net.minestom.server.network.packet.server.configuration.*;
import net.minestom.server.network.packet.server.login.*;
import net.minestom.server.network.packet.server.play.*;
import net.minestom.server.network.packet.server.status.ResponsePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

public interface PacketRegistry<T> {
    @UnknownNullability
    T create(int packetId, @NotNull NetworkBuffer reader);

    PacketInfo<T> packetInfo(@NotNull Class<?> packetClass);

    default PacketInfo<T> packetInfo(@NotNull T packet) {
        return packetInfo(packet.getClass());
    }

    PacketInfo<T> packetInfo(int packetId);

    @NotNull
    ConnectionState state();

    @NotNull ConnectionSide side();

    record PacketInfo<T>(Class<T> packetClass, int id, NetworkBuffer.Type<T> serializer) {
    }

    abstract sealed class Client extends PacketRegistryTemplate<ClientPacket> {
        @SafeVarargs Client(Entry<? extends ClientPacket>... suppliers) {
            super(suppliers);
        }

        @Override
        public @NotNull ConnectionSide side() {
            return ConnectionSide.CLIENT;
        }
    }

    final class ClientHandshake extends Client {
        public ClientHandshake() {
            super(
                    entry(ClientHandshakePacket.class, ClientHandshakePacket.SERIALIZER)
            );
        }

        @Override
        public @NotNull ConnectionState state() {
            return ConnectionState.HANDSHAKE;
        }
    }

    final class ClientStatus extends Client {
        public ClientStatus() {
            super(
                    entry(StatusRequestPacket.class, StatusRequestPacket.SERIALIZER),
                    entry(ClientPingRequestPacket.class, ClientPingRequestPacket.SERIALIZER)
            );
        }

        @Override
        public @NotNull ConnectionState state() {
            return ConnectionState.STATUS;
        }
    }

    final class ClientLogin extends Client {
        public ClientLogin() {
            super(
                    entry(ClientLoginStartPacket.class, ClientLoginStartPacket.SERIALIZER),
                    entry(ClientEncryptionResponsePacket.class, ClientEncryptionResponsePacket.SERIALIZER),
                    entry(ClientLoginPluginResponsePacket.class, ClientLoginPluginResponsePacket.SERIALIZER),
                    entry(ClientLoginAcknowledgedPacket.class, ClientLoginAcknowledgedPacket.SERIALIZER),
                    entry(ClientCookieResponsePacket.class, ClientCookieResponsePacket.SERIALIZER)
            );
        }

        @Override
        public @NotNull ConnectionState state() {
            return ConnectionState.LOGIN;
        }
    }

    final class ClientConfiguration extends Client {
        public ClientConfiguration() {
            super(
                    entry(ClientSettingsPacket.class, ClientSettingsPacket.SERIALIZER),
                    entry(ClientCookieResponsePacket.class, ClientCookieResponsePacket.SERIALIZER),
                    entry(ClientPluginMessagePacket.class, ClientPluginMessagePacket.SERIALIZER),
                    entry(ClientFinishConfigurationPacket.class, ClientFinishConfigurationPacket.SERIALIZER),
                    entry(ClientKeepAlivePacket.class, ClientKeepAlivePacket.SERIALIZER),
                    entry(ClientPongPacket.class, ClientPongPacket.SERIALIZER),
                    entry(ClientResourcePackStatusPacket.class, ClientResourcePackStatusPacket.SERIALIZER),
                    entry(ClientSelectKnownPacksPacket.class, ClientSelectKnownPacksPacket.SERIALIZER),
                    entry(ClientCustomClickActionPacket.class, ClientCustomClickActionPacket.SERIALIZER)
            );
        }

        @Override
        public @NotNull ConnectionState state() {
            return ConnectionState.CONFIGURATION;
        }
    }

    final class ClientPlay extends Client {
        public ClientPlay() {
            super(
                    entry(ClientTeleportConfirmPacket.class, ClientTeleportConfirmPacket.SERIALIZER),
                    entry(ClientQueryBlockNbtPacket.class, ClientQueryBlockNbtPacket.SERIALIZER),
                    entry(ClientSelectBundleItemPacket.class, ClientSelectBundleItemPacket.SERIALIZER),
                    entry(ClientChangeDifficultyPacket.class, ClientChangeDifficultyPacket.SERIALIZER),
                    entry(ClientChangeGameModePacket.class, ClientChangeGameModePacket.SERIALIZER),
                    entry(ClientChatAckPacket.class, ClientChatAckPacket.SERIALIZER),
                    entry(ClientCommandChatPacket.class, ClientCommandChatPacket.SERIALIZER),
                    entry(ClientSignedCommandChatPacket.class, ClientSignedCommandChatPacket.SERIALIZER),
                    entry(ClientChatMessagePacket.class, ClientChatMessagePacket.SERIALIZER),
                    entry(ClientChatSessionUpdatePacket.class, ClientChatSessionUpdatePacket.SERIALIZER),
                    entry(ClientChunkBatchReceivedPacket.class, ClientChunkBatchReceivedPacket.SERIALIZER),
                    entry(ClientStatusPacket.class, ClientStatusPacket.SERIALIZER),
                    entry(ClientTickEndPacket.class, ClientTickEndPacket.SERIALIZER),
                    entry(ClientSettingsPacket.class, ClientSettingsPacket.SERIALIZER),
                    entry(ClientTabCompletePacket.class, ClientTabCompletePacket.SERIALIZER),
                    entry(ClientConfigurationAckPacket.class, ClientConfigurationAckPacket.SERIALIZER),
                    entry(ClientClickWindowButtonPacket.class, ClientClickWindowButtonPacket.SERIALIZER),
                    entry(ClientClickWindowPacket.class, ClientClickWindowPacket.SERIALIZER),
                    entry(ClientCloseWindowPacket.class, ClientCloseWindowPacket.SERIALIZER),
                    entry(ClientWindowSlotStatePacket.class, ClientWindowSlotStatePacket.SERIALIZER),
                    entry(ClientCookieResponsePacket.class, ClientCookieResponsePacket.SERIALIZER),
                    entry(ClientPluginMessagePacket.class, ClientPluginMessagePacket.SERIALIZER),
                    entry(ClientDebugSampleSubscriptionPacket.class, ClientDebugSampleSubscriptionPacket.SERIALIZER),
                    entry(ClientEditBookPacket.class, ClientEditBookPacket.SERIALIZER),
                    entry(ClientQueryEntityNbtPacket.class, ClientQueryEntityNbtPacket.SERIALIZER),
                    entry(ClientInteractEntityPacket.class, ClientInteractEntityPacket.SERIALIZER),
                    entry(ClientGenerateStructurePacket.class, ClientGenerateStructurePacket.SERIALIZER),
                    entry(ClientKeepAlivePacket.class, ClientKeepAlivePacket.SERIALIZER),
                    entry(ClientLockDifficultyPacket.class, ClientLockDifficultyPacket.SERIALIZER),
                    entry(ClientPlayerPositionPacket.class, ClientPlayerPositionPacket.SERIALIZER),
                    entry(ClientPlayerPositionAndRotationPacket.class, ClientPlayerPositionAndRotationPacket.SERIALIZER),
                    entry(ClientPlayerRotationPacket.class, ClientPlayerRotationPacket.SERIALIZER),
                    entry(ClientPlayerPositionStatusPacket.class, ClientPlayerPositionStatusPacket.SERIALIZER),
                    entry(ClientVehicleMovePacket.class, ClientVehicleMovePacket.SERIALIZER),
                    entry(ClientSteerBoatPacket.class, ClientSteerBoatPacket.SERIALIZER),
                    entry(ClientPickItemFromBlockPacket.class, ClientPickItemFromBlockPacket.SERIALIZER),
                    entry(ClientPickItemFromEntityPacket.class, ClientPickItemFromEntityPacket.SERIALIZER),
                    entry(ClientPingRequestPacket.class, ClientPingRequestPacket.SERIALIZER),
                    entry(ClientPlaceRecipePacket.class, ClientPlaceRecipePacket.SERIALIZER),
                    entry(ClientPlayerAbilitiesPacket.class, ClientPlayerAbilitiesPacket.SERIALIZER),
                    entry(ClientPlayerDiggingPacket.class, ClientPlayerDiggingPacket.SERIALIZER),
                    entry(ClientEntityActionPacket.class, ClientEntityActionPacket.SERIALIZER),
                    entry(ClientInputPacket.class, ClientInputPacket.SERIALIZER),
                    entry(ClientPlayerLoadedPacket.class, ClientPlayerLoadedPacket.SERIALIZER),
                    entry(ClientPongPacket.class, ClientPongPacket.SERIALIZER),
                    entry(ClientSetRecipeBookStatePacket.class, ClientSetRecipeBookStatePacket.SERIALIZER),
                    entry(ClientRecipeBookSeenRecipePacket.class, ClientRecipeBookSeenRecipePacket.SERIALIZER),
                    entry(ClientNameItemPacket.class, ClientNameItemPacket.SERIALIZER),
                    entry(ClientResourcePackStatusPacket.class, ClientResourcePackStatusPacket.SERIALIZER),
                    entry(ClientAdvancementTabPacket.class, ClientAdvancementTabPacket.SERIALIZER),
                    entry(ClientSelectTradePacket.class, ClientSelectTradePacket.SERIALIZER),
                    entry(ClientSetBeaconEffectPacket.class, ClientSetBeaconEffectPacket.SERIALIZER),
                    entry(ClientHeldItemChangePacket.class, ClientHeldItemChangePacket.SERIALIZER),
                    entry(ClientUpdateCommandBlockPacket.class, ClientUpdateCommandBlockPacket.SERIALIZER),
                    entry(ClientUpdateCommandBlockMinecartPacket.class, ClientUpdateCommandBlockMinecartPacket.SERIALIZER),
                    entry(ClientCreativeInventoryActionPacket.class, ClientCreativeInventoryActionPacket.SERIALIZER),
                    entry(ClientUpdateJigsawBlockPacket.class, ClientUpdateJigsawBlockPacket.SERIALIZER),
                    entry(ClientUpdateStructureBlockPacket.class, ClientUpdateStructureBlockPacket.SERIALIZER),
                    entry(ClientSetTestBlockPacket.class, ClientSetTestBlockPacket.SERIALIZER),
                    entry(ClientUpdateSignPacket.class, ClientUpdateSignPacket.SERIALIZER),
                    entry(ClientAnimationPacket.class, ClientAnimationPacket.SERIALIZER),
                    entry(ClientSpectatePacket.class, ClientSpectatePacket.SERIALIZER),
                    entry(ClientTestInstanceBlockActionPacket.class, ClientTestInstanceBlockActionPacket.SERIALIZER),
                    entry(ClientPlayerBlockPlacementPacket.class, ClientPlayerBlockPlacementPacket.SERIALIZER),
                    entry(ClientUseItemPacket.class, ClientUseItemPacket.SERIALIZER),
                    entry(ClientCustomClickActionPacket.class, ClientCustomClickActionPacket.SERIALIZER)
            );
        }

        @Override
        public @NotNull ConnectionState state() {
            return ConnectionState.PLAY;
        }
    }

    abstract sealed class Server extends PacketRegistryTemplate<ServerPacket> {
        @SafeVarargs Server(Entry<? extends ServerPacket>... suppliers) {
            super(suppliers);
        }

        @Override
        public @NotNull ConnectionSide side() {
            return ConnectionSide.SERVER;
        }
    }

    final class ServerHandshake extends Server {
        public ServerHandshake() {
            super(
                    // Empty
            );
        }

        @Override
        public @NotNull ConnectionState state() {
            return ConnectionState.HANDSHAKE;
        }
    }

    final class ServerStatus extends Server {
        public ServerStatus() {
            super(
                    entry(ResponsePacket.class, ResponsePacket.SERIALIZER),
                    entry(PingResponsePacket.class, PingResponsePacket.SERIALIZER)
            );
        }

        @Override
        public @NotNull ConnectionState state() {
            return ConnectionState.STATUS;
        }
    }

    final class ServerLogin extends Server {
        public ServerLogin() {
            super(
                    entry(LoginDisconnectPacket.class, LoginDisconnectPacket.SERIALIZER),
                    entry(EncryptionRequestPacket.class, EncryptionRequestPacket.SERIALIZER),
                    entry(LoginSuccessPacket.class, LoginSuccessPacket.SERIALIZER),
                    entry(SetCompressionPacket.class, SetCompressionPacket.SERIALIZER),
                    entry(LoginPluginRequestPacket.class, LoginPluginRequestPacket.SERIALIZER),
                    entry(CookieRequestPacket.class, CookieRequestPacket.SERIALIZER)
            );
        }

        @Override
        public @NotNull ConnectionState state() {
            return ConnectionState.LOGIN;
        }
    }

    final class ServerConfiguration extends Server {
        public ServerConfiguration() {
            super(
                    entry(CookieRequestPacket.class, CookieRequestPacket.SERIALIZER),
                    entry(PluginMessagePacket.class, PluginMessagePacket.SERIALIZER),
                    entry(DisconnectPacket.class, DisconnectPacket.SERIALIZER),
                    entry(FinishConfigurationPacket.class, FinishConfigurationPacket.SERIALIZER),
                    entry(KeepAlivePacket.class, KeepAlivePacket.SERIALIZER),
                    entry(PingPacket.class, PingPacket.SERIALIZER),
                    entry(ResetChatPacket.class, ResetChatPacket.SERIALIZER),
                    entry(RegistryDataPacket.class, RegistryDataPacket.SERIALIZER),
                    entry(ResourcePackPopPacket.class, ResourcePackPopPacket.SERIALIZER),
                    entry(ResourcePackPushPacket.class, ResourcePackPushPacket.SERIALIZER),
                    entry(CookieStorePacket.class, CookieStorePacket.SERIALIZER),
                    entry(TransferPacket.class, TransferPacket.SERIALIZER),
                    entry(UpdateEnabledFeaturesPacket.class, UpdateEnabledFeaturesPacket.SERIALIZER),
                    entry(TagsPacket.class, TagsPacket.SERIALIZER),
                    entry(SelectKnownPacksPacket.class, SelectKnownPacksPacket.SERIALIZER),
                    entry(CustomReportDetailsPacket.class, CustomReportDetailsPacket.SERIALIZER),
                    entry(ServerLinksPacket.class, ServerLinksPacket.SERIALIZER),
                    entry(ClearDialogPacket.class, ClearDialogPacket.SERIALIZER),
                    entry(ShowDialogPacket.class, ShowDialogPacket.INLINE_SERIALIZER)
            );
        }

        @Override
        public @NotNull ConnectionState state() {
            return ConnectionState.CONFIGURATION;
        }
    }

    final class ServerPlay extends Server {
        public ServerPlay() {
            super(
                    entry(BundlePacket.class, BundlePacket.SERIALIZER),
                    entry(SpawnEntityPacket.class, SpawnEntityPacket.SERIALIZER),
                    entry(EntityAnimationPacket.class, EntityAnimationPacket.SERIALIZER),
                    entry(StatisticsPacket.class, StatisticsPacket.SERIALIZER),
                    entry(AcknowledgeBlockChangePacket.class, AcknowledgeBlockChangePacket.SERIALIZER),
                    entry(BlockBreakAnimationPacket.class, BlockBreakAnimationPacket.SERIALIZER),
                    entry(BlockEntityDataPacket.class, BlockEntityDataPacket.SERIALIZER),
                    entry(BlockActionPacket.class, BlockActionPacket.SERIALIZER),
                    entry(BlockChangePacket.class, BlockChangePacket.SERIALIZER),
                    entry(BossBarPacket.class, BossBarPacket.SERIALIZER),
                    entry(ServerDifficultyPacket.class, ServerDifficultyPacket.SERIALIZER),
                    entry(ChunkBatchFinishedPacket.class, ChunkBatchFinishedPacket.SERIALIZER),
                    entry(ChunkBatchStartPacket.class, ChunkBatchStartPacket.SERIALIZER),
                    entry(ChunkBiomesPacket.class, ChunkBiomesPacket.SERIALIZER),
                    entry(ClearTitlesPacket.class, ClearTitlesPacket.SERIALIZER),
                    entry(TabCompletePacket.class, TabCompletePacket.SERIALIZER),
                    entry(DeclareCommandsPacket.class, DeclareCommandsPacket.SERIALIZER),
                    entry(CloseWindowPacket.class, CloseWindowPacket.SERIALIZER),
                    entry(WindowItemsPacket.class, WindowItemsPacket.SERIALIZER),
                    entry(WindowPropertyPacket.class, WindowPropertyPacket.SERIALIZER),
                    entry(SetSlotPacket.class, SetSlotPacket.SERIALIZER),
                    entry(CookieRequestPacket.class, CookieRequestPacket.SERIALIZER),
                    entry(SetCooldownPacket.class, SetCooldownPacket.SERIALIZER),
                    entry(CustomChatCompletionPacket.class, CustomChatCompletionPacket.SERIALIZER),
                    entry(PluginMessagePacket.class, PluginMessagePacket.SERIALIZER),
                    entry(DamageEventPacket.class, DamageEventPacket.SERIALIZER),
                    entry(DebugSamplePacket.class, DebugSamplePacket.SERIALIZER),
                    entry(DeleteChatPacket.class, DeleteChatPacket.SERIALIZER),
                    entry(DisconnectPacket.class, DisconnectPacket.SERIALIZER),
                    entry(DisguisedChatPacket.class, DisguisedChatPacket.SERIALIZER),
                    entry(EntityStatusPacket.class, EntityStatusPacket.SERIALIZER),
                    entry(EntityPositionSyncPacket.class, EntityPositionSyncPacket.SERIALIZER),
                    entry(ExplosionPacket.class, ExplosionPacket.SERIALIZER),
                    entry(UnloadChunkPacket.class, UnloadChunkPacket.SERIALIZER),
                    entry(ChangeGameStatePacket.class, ChangeGameStatePacket.SERIALIZER),
                    entry(OpenHorseWindowPacket.class, OpenHorseWindowPacket.SERIALIZER),
                    entry(HitAnimationPacket.class, HitAnimationPacket.SERIALIZER),
                    entry(InitializeWorldBorderPacket.class, InitializeWorldBorderPacket.SERIALIZER),
                    entry(KeepAlivePacket.class, KeepAlivePacket.SERIALIZER),
                    entry(ChunkDataPacket.class, ChunkDataPacket.SERIALIZER),
                    entry(WorldEventPacket.class, WorldEventPacket.SERIALIZER),
                    entry(ParticlePacket.class, ParticlePacket.SERIALIZER),
                    entry(UpdateLightPacket.class, UpdateLightPacket.SERIALIZER),
                    entry(JoinGamePacket.class, JoinGamePacket.SERIALIZER),
                    entry(MapDataPacket.class, MapDataPacket.SERIALIZER),
                    entry(TradeListPacket.class, TradeListPacket.SERIALIZER),
                    entry(EntityPositionPacket.class, EntityPositionPacket.SERIALIZER),
                    entry(EntityPositionAndRotationPacket.class, EntityPositionAndRotationPacket.SERIALIZER),
                    entry(MoveMinecartPacket.class, MoveMinecartPacket.SERIALIZER),
                    entry(EntityRotationPacket.class, EntityRotationPacket.SERIALIZER),
                    entry(VehicleMovePacket.class, VehicleMovePacket.SERIALIZER),
                    entry(OpenBookPacket.class, OpenBookPacket.SERIALIZER),
                    entry(OpenWindowPacket.class, OpenWindowPacket.SERIALIZER),
                    entry(OpenSignEditorPacket.class, OpenSignEditorPacket.SERIALIZER),
                    entry(PingPacket.class, PingPacket.SERIALIZER),
                    entry(PingResponsePacket.class, PingResponsePacket.SERIALIZER),
                    entry(PlaceGhostRecipePacket.class, PlaceGhostRecipePacket.SERIALIZER),
                    entry(PlayerAbilitiesPacket.class, PlayerAbilitiesPacket.SERIALIZER),
                    entry(PlayerChatMessagePacket.class, PlayerChatMessagePacket.SERIALIZER),
                    entry(EndCombatEventPacket.class, EndCombatEventPacket.SERIALIZER),
                    entry(EnterCombatEventPacket.class, EnterCombatEventPacket.SERIALIZER),
                    entry(DeathCombatEventPacket.class, DeathCombatEventPacket.SERIALIZER),
                    entry(PlayerInfoRemovePacket.class, PlayerInfoRemovePacket.SERIALIZER),
                    entry(PlayerInfoUpdatePacket.class, PlayerInfoUpdatePacket.SERIALIZER),
                    entry(FacePlayerPacket.class, FacePlayerPacket.SERIALIZER),
                    entry(PlayerPositionAndLookPacket.class, PlayerPositionAndLookPacket.SERIALIZER),
                    entry(PlayerRotationPacket.class, PlayerRotationPacket.SERIALIZER),
                    entry(RecipeBookAddPacket.class, RecipeBookAddPacket.SERIALIZER),
                    entry(RecipeBookRemovePacket.class, RecipeBookRemovePacket.SERIALIZER),
                    entry(RecipeBookSettingsPacket.class, RecipeBookSettingsPacket.SERIALIZER),
                    entry(DestroyEntitiesPacket.class, DestroyEntitiesPacket.SERIALIZER),
                    entry(RemoveEntityEffectPacket.class, RemoveEntityEffectPacket.SERIALIZER),
                    entry(ResetScorePacket.class, ResetScorePacket.SERIALIZER),
                    entry(ResourcePackPopPacket.class, ResourcePackPopPacket.SERIALIZER),
                    entry(ResourcePackPushPacket.class, ResourcePackPushPacket.SERIALIZER),
                    entry(RespawnPacket.class, RespawnPacket.SERIALIZER),
                    entry(EntityHeadLookPacket.class, EntityHeadLookPacket.SERIALIZER),
                    entry(MultiBlockChangePacket.class, MultiBlockChangePacket.SERIALIZER),
                    entry(SelectAdvancementTabPacket.class, SelectAdvancementTabPacket.SERIALIZER),
                    entry(ServerDataPacket.class, ServerDataPacket.SERIALIZER),
                    entry(ActionBarPacket.class, ActionBarPacket.SERIALIZER),
                    entry(WorldBorderCenterPacket.class, WorldBorderCenterPacket.SERIALIZER),
                    entry(WorldBorderLerpSizePacket.class, WorldBorderLerpSizePacket.SERIALIZER),
                    entry(WorldBorderSizePacket.class, WorldBorderSizePacket.SERIALIZER),
                    entry(WorldBorderWarningDelayPacket.class, WorldBorderWarningDelayPacket.SERIALIZER),
                    entry(WorldBorderWarningReachPacket.class, WorldBorderWarningReachPacket.SERIALIZER),
                    entry(CameraPacket.class, CameraPacket.SERIALIZER),
                    entry(UpdateViewPositionPacket.class, UpdateViewPositionPacket.SERIALIZER),
                    entry(UpdateViewDistancePacket.class, UpdateViewDistancePacket.SERIALIZER),
                    entry(SetCursorItemPacket.class, SetCursorItemPacket.SERIALIZER),
                    entry(SpawnPositionPacket.class, SpawnPositionPacket.SERIALIZER),
                    entry(DisplayScoreboardPacket.class, DisplayScoreboardPacket.SERIALIZER),
                    entry(EntityMetaDataPacket.class, EntityMetaDataPacket.SERIALIZER),
                    entry(AttachEntityPacket.class, AttachEntityPacket.SERIALIZER),
                    entry(EntityVelocityPacket.class, EntityVelocityPacket.SERIALIZER),
                    entry(EntityEquipmentPacket.class, EntityEquipmentPacket.SERIALIZER),
                    entry(SetExperiencePacket.class, SetExperiencePacket.SERIALIZER),
                    entry(UpdateHealthPacket.class, UpdateHealthPacket.SERIALIZER),
                    entry(HeldItemChangePacket.class, HeldItemChangePacket.SERIALIZER),
                    entry(ScoreboardObjectivePacket.class, ScoreboardObjectivePacket.SERIALIZER),
                    entry(SetPassengersPacket.class, SetPassengersPacket.SERIALIZER),
                    entry(SetPlayerInventorySlotPacket.class, SetPlayerInventorySlotPacket.SERIALIZER),
                    entry(TeamsPacket.class, TeamsPacket.SERIALIZER),
                    entry(UpdateScorePacket.class, UpdateScorePacket.SERIALIZER),
                    entry(UpdateSimulationDistancePacket.class, UpdateSimulationDistancePacket.SERIALIZER),
                    entry(SetTitleSubTitlePacket.class, SetTitleSubTitlePacket.SERIALIZER),
                    entry(TimeUpdatePacket.class, TimeUpdatePacket.SERIALIZER),
                    entry(SetTitleTextPacket.class, SetTitleTextPacket.SERIALIZER),
                    entry(SetTitleTimePacket.class, SetTitleTimePacket.SERIALIZER),
                    entry(EntitySoundEffectPacket.class, EntitySoundEffectPacket.SERIALIZER),
                    entry(SoundEffectPacket.class, SoundEffectPacket.SERIALIZER),
                    entry(StartConfigurationPacket.class, StartConfigurationPacket.SERIALIZER),
                    entry(StopSoundPacket.class, StopSoundPacket.SERIALIZER),
                    entry(CookieStorePacket.class, CookieStorePacket.SERIALIZER),
                    entry(SystemChatPacket.class, SystemChatPacket.SERIALIZER),
                    entry(PlayerListHeaderAndFooterPacket.class, PlayerListHeaderAndFooterPacket.SERIALIZER),
                    entry(NbtQueryResponsePacket.class, NbtQueryResponsePacket.SERIALIZER),
                    entry(CollectItemPacket.class, CollectItemPacket.SERIALIZER),
                    entry(EntityTeleportPacket.class, EntityTeleportPacket.SERIALIZER),
                    entry(TestInstanceBlockStatus.class, TestInstanceBlockStatus.SERIALIZER),
                    entry(SetTickStatePacket.class, SetTickStatePacket.SERIALIZER),
                    entry(TickStepPacket.class, TickStepPacket.SERIALIZER),
                    entry(TransferPacket.class, TransferPacket.SERIALIZER),
                    entry(AdvancementsPacket.class, AdvancementsPacket.SERIALIZER),
                    entry(EntityAttributesPacket.class, EntityAttributesPacket.SERIALIZER),
                    entry(EntityEffectPacket.class, EntityEffectPacket.SERIALIZER),
                    entry(DeclareRecipesPacket.class, DeclareRecipesPacket.SERIALIZER),
                    entry(TagsPacket.class, TagsPacket.SERIALIZER),
                    entry(ProjectilePowerPacket.class, ProjectilePowerPacket.SERIALIZER),
                    entry(CustomReportDetailsPacket.class, CustomReportDetailsPacket.SERIALIZER),
                    entry(ServerLinksPacket.class, ServerLinksPacket.SERIALIZER),
                    entry(TrackedWaypointPacket.class, TrackedWaypointPacket.SERIALIZER),
                    entry(ClearDialogPacket.class, ClearDialogPacket.SERIALIZER),
                    entry(ShowDialogPacket.class, ShowDialogPacket.SERIALIZER)
            );
        }

        @Override
        public @NotNull ConnectionState state() {
            return ConnectionState.PLAY;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    abstract sealed class PacketRegistryTemplate<T> implements PacketRegistry<T> {
        private final PacketInfo<? extends T>[] suppliers;
        private final ClassValue<PacketInfo<T>> packetIds = new ClassValue<>() {
            @Override
            protected PacketInfo<T> computeValue(@NotNull Class<?> type) {
                for (PacketInfo<? extends T> info : suppliers) {
                    if (info != null && info.packetClass == type) {
                        return (PacketInfo<T>) info;
                    }
                }
                throw new IllegalStateException("Packet type " + type + " cannot be sent in state " + side().name() + "_" + state().name() + "!");
            }
        };

        @SafeVarargs PacketRegistryTemplate(Entry<? extends T>... suppliers) {
            PacketInfo<? extends T>[] packetInfos = new PacketInfo[suppliers.length];
            for (int i = 0; i < suppliers.length; i++) {
                final Entry<? extends T> entry = suppliers[i];
                if (entry == null) continue;
                packetInfos[i] = new PacketInfo(entry.type, i, entry.reader);
            }
            this.suppliers = packetInfos;
        }

        public @UnknownNullability T create(int packetId, @NotNull NetworkBuffer reader) {
            final PacketInfo<T> info = packetInfo(packetId);
            final NetworkBuffer.Type<T> supplier = info.serializer;
            final T packet = supplier.read(reader);
            if (packet == null) {
                throw new IllegalStateException("Packet " + info.packetClass + " failed to read!");
            }
            return packet;
        }

        @Override
        public PacketInfo<T> packetInfo(@NotNull Class<?> packetClass) {
            return packetIds.get(packetClass);
        }

        @Override
        public PacketInfo<T> packetInfo(int packetId) {
            final PacketInfo<T> info;
            if (packetId < 0 || packetId >= suppliers.length || (info = (PacketInfo<T>) suppliers[packetId]) == null) {
                throw new IllegalStateException("Packet id 0x" + Integer.toHexString(packetId) + " isn't registered!");
            }
            return info;
        }


        record Entry<T>(Class<T> type, NetworkBuffer.Type<T> reader) {
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        static <T> Entry<T> entry(Class<T> type, NetworkBuffer.Type<T> reader) {
            return new Entry<>((Class) type, reader);
        }
    }

    enum ConnectionSide {
        CLIENT,
        SERVER
    }
}
