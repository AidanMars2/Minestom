package net.minestom.server.network.player;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.crypto.PlayerPublicKey;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.monitoring.EventsJFR;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.common.CookieRequestPacket;
import net.minestom.server.network.packet.server.common.CookieStorePacket;
import net.minestom.server.network.packet.server.common.DisconnectPacket;
import net.minestom.server.network.packet.server.configuration.SelectKnownPacksPacket;
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket;
import net.minestom.server.network.plugin.LoginPluginMessageProcessor;
import net.minestom.server.utils.validate.Check;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A PlayerConnection is an object needed for all created {@link Player}.
 * It can be extended to create a new kind of player (NPC for instance).
 */
public abstract class PlayerConnection {
    private Player player;
    private volatile ConnectionState connectionState;
    private PlayerPublicKey playerPublicKey;
    volatile boolean online;

    private LoginPluginMessageProcessor loginPluginMessageProcessor = new LoginPluginMessageProcessor(this);

    private CompletableFuture<List<SelectKnownPacksPacket.Entry>> knownPacksFuture = null; // Present only when waiting for a response from the client.

    private final Map<Key, CompletableFuture<byte @Nullable []>> pendingCookieRequests = new ConcurrentHashMap<>();

    public PlayerConnection() {
        this.online = true;
        this.connectionState = ConnectionState.HANDSHAKE;
    }

    /**
     * Returns a printable identifier for this connection, will be the player username
     * or the connection remote address.
     *
     * @return this connection identifier
     */
    public @NotNull String getIdentifier() {
        final Player player = getPlayer();
        return player != null ?
                player.getUsername() :
                getRemoteAddress().toString();
    }

    /**
     * Serializes the packet and send it to the client.
     *
     * @param packet the packet to send
     */
    public abstract void sendPacket(@NotNull SendablePacket packet);

    public void sendPackets(@NotNull Collection<SendablePacket> packets) {
        packets.forEach(this::sendPacket);
    }

    public void sendPackets(@NotNull SendablePacket... packets) {
        sendPackets(List.of(packets));
    }

    /**
     * Gets the remote address of the client.
     *
     * @return the remote address
     */
    public abstract @NotNull SocketAddress getRemoteAddress();

    /**
     * Gets protocol version of client.
     *
     * @return the protocol version
     */
    public int getProtocolVersion() {
        return MinecraftServer.PROTOCOL_VERSION;
    }

    /**
     * Gets the server address that the client used to connect.
     * <p>
     * WARNING: it is given by the client, it is possible for it to be wrong.
     *
     * @return the server address used
     */
    public @Nullable String getServerAddress() {
        return MinecraftServer.getServer().getAddress();
    }


    /**
     * Gets the server port that the client used to connect.
     * <p>
     * WARNING: it is given by the client, it is possible for it to be wrong.
     *
     * @return the server port used
     */
    public int getServerPort() {
        return MinecraftServer.getServer().getPort();
    }


    /**
     * Kicks the player with a reason.
     *
     * @param component the reason
     */
    public void kick(@NotNull Component component) {
        // Packet type depends on the current player connection state
        final ServerPacket disconnectPacket;
        if (connectionState == ConnectionState.LOGIN) {
            disconnectPacket = new LoginDisconnectPacket(component);
        } else {
            disconnectPacket = new DisconnectPacket(component);
        }
        sendPacket(disconnectPacket);
        disconnect();
    }

    /**
     * Forcing the player to disconnect.
     */
    public void disconnect() {
        this.online = false;
        final Player player = MinecraftServer.getConnectionManager().getPlayer(this);
        if (player != null) {
            MinecraftServer.getConnectionManager().removePlayer(this);
            if (connectionState == ConnectionState.PLAY && !player.isRemoved())
                player.scheduleNextTick(Entity::remove);
            else {
                EventDispatcher.call(new PlayerDisconnectEvent(player));
                new EventsJFR.PlayerLeave(player.getUuid().toString()).commit();
            }
        }
    }

    /**
     * Gets the player linked to this connection.
     *
     * @return the player, can be null if not initialized yet
     */
    public @Nullable Player getPlayer() {
        return player;
    }

    /**
     * Changes the player linked to this connection.
     * <p>
     * WARNING: unsafe.
     *
     * @param player the player
     */
    public void setPlayer(Player player) {
        this.player = player;
    }

    /**
     * Gets if the client is still connected to the server.
     *
     * @return true if the player is online, false otherwise
     */
    public boolean isOnline() {
        return online;
    }

    public void setConnectionState(@NotNull ConnectionState connectionState) {
        this.connectionState = connectionState;
        if (connectionState == ConnectionState.CONFIGURATION) {
            // Clear the plugin request map (it is not used beyond login)
            this.loginPluginMessageProcessor = null;
        }
    }

    /**
     * Gets the client connection state.
     *
     * @return the client connection state
     */
    public @NotNull ConnectionState getConnectionState() {
        return connectionState;
    }

    public PlayerPublicKey playerPublicKey() {
        return playerPublicKey;
    }

    public void setPlayerPublicKey(PlayerPublicKey playerPublicKey) {
        this.playerPublicKey = playerPublicKey;
    }

    public void storeCookie(@NotNull String key, byte @NotNull [] data) {
        sendPacket(new CookieStorePacket(key, data));
    }

    public CompletableFuture<byte @Nullable []> fetchCookie(@NotNull String key) {
        if (getConnectionState() == ConnectionState.CONFIGURATION && getPlayer() == null) {
            // This is a bit of an unfortunate limitation. The player provider blocks the player read virtual
            // thread waiting for the player provider so a cookie response would never be received and the
            // process would deadlock.
            // We cannot create the player provider without blocking the read thread because the client
            // has already sent the initial settings packet, and we need the Player to process the response.
            // We could store the settings on the connection, but it does not seem worth to get around this case.
            throw new IllegalStateException("Cannot fetch cookie in PlayerProvider, use AsyncPlayerPreLoginEvent or AsyncPlayerConfigurationEvent");
        }
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        pendingCookieRequests.put(Key.key(key), future);
        sendPacket(new CookieRequestPacket(key));
        return future;
    }

    @ApiStatus.Internal
    public void receiveCookieResponse(@NotNull String key, byte @Nullable [] data) {
        CompletableFuture<byte[]> future = pendingCookieRequests.remove(Key.key(key));
        if (future != null) {
            future.complete(data);
        }
    }

    /**
     * Gets the login plugin message processor, only available during the login state.
     */
    @ApiStatus.Internal
    public @NotNull LoginPluginMessageProcessor loginPluginMessageProcessor() {
        return Objects.requireNonNull(this.loginPluginMessageProcessor,
                "Login plugin message processor is only available during the login state.");
    }

    @ApiStatus.Internal
    public @NotNull CompletableFuture<List<SelectKnownPacksPacket.Entry>> requestKnownPacks(@NotNull List<SelectKnownPacksPacket.Entry> serverPacks) {
        Check.stateCondition(knownPacksFuture != null, "Known packs already pending");
        sendPacket(new SelectKnownPacksPacket(serverPacks));
        return knownPacksFuture = new CompletableFuture<>();
    }

    @ApiStatus.Internal
    public void receiveKnownPacksResponse(@NotNull List<SelectKnownPacksPacket.Entry> clientPacks) {
        final var future = knownPacksFuture;
        if (future != null) {
            future.complete(clientPacks);
            knownPacksFuture = null;
        }
    }

    @Override
    public String toString() {
        return "PlayerConnection{" +
                "connectionState=" + connectionState +
                ", identifier=" + getIdentifier() +
                '}';
    }
}
