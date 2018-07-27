package com.velocitypowered.proxy.config;

import com.google.common.collect.ImmutableMap;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.proxy.util.LegacyChatColorUtils;
import net.kyori.text.Component;
import net.kyori.text.serializer.ComponentSerializers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VelocityConfiguration {
    private static final Logger logger = LogManager.getLogger(VelocityConfiguration.class);

    private final String bind;
    private final String motd;
    private final int showMaxPlayers;
    private final boolean onlineMode;
    private final IPForwardingMode ipForwardingMode;
    private final Map<String, String> servers;
    private final List<String> attemptConnectionOrder;

    private Component motdAsComponent;

    private VelocityConfiguration(String bind, String motd, int showMaxPlayers, boolean onlineMode,
                                  IPForwardingMode ipForwardingMode, Map<String, String> servers,
                                  List<String> attemptConnectionOrder) {
        this.bind = bind;
        this.motd = motd;
        this.showMaxPlayers = showMaxPlayers;
        this.onlineMode = onlineMode;
        this.ipForwardingMode = ipForwardingMode;
        this.servers = servers;
        this.attemptConnectionOrder = attemptConnectionOrder;
    }

    public boolean validate() {
        boolean valid = true;

        if (bind.isEmpty()) {
            logger.error("'bind' option is empty.");
            valid = false;
        }

        if (!onlineMode) {
            logger.info("Proxy is running in offline mode!");
        }

        switch (ipForwardingMode) {
            case NONE:
                logger.info("IP forwarding is disabled! All players will appear to be connecting from the proxy and will have offline-mode UUIDs.");
                break;
            case MODERN:
                logger.warn("Modern IP forwarding is not currently implemented.");
                break;
        }

        if (servers.isEmpty()) {
            logger.error("You have no servers configured. :(");
            valid = false;
        } else {
            if (attemptConnectionOrder.isEmpty()) {
                logger.error("No fallback servers are configured!");
                valid = false;
            }

            for (String s : attemptConnectionOrder) {
                if (!servers.containsKey(s)) {
                    logger.error("Fallback server " + s + " doesn't exist!");
                    valid = false;
                }
            }
        }

        return valid;
    }

    public String getBind() {
        return bind;
    }

    public String getMotd() {
        return motd;
    }

    public Component getMotdComponent() {
        if (motdAsComponent == null) {
            if (motd.startsWith("{")) {
                motdAsComponent = ComponentSerializers.JSON.deserialize(motd);
            } else {
                motdAsComponent = ComponentSerializers.LEGACY.deserialize(LegacyChatColorUtils.translate('&', motd));
            }
        }
        return motdAsComponent;
    }

    public int getShowMaxPlayers() {
        return showMaxPlayers;
    }

    public boolean isOnlineMode() {
        return onlineMode;
    }

    public IPForwardingMode getIpForwardingMode() {
        return ipForwardingMode;
    }

    public Map<String, String> getServers() {
        return servers;
    }

    public List<String> getAttemptConnectionOrder() {
        return attemptConnectionOrder;
    }

    @Override
    public String toString() {
        return "VelocityConfiguration{" +
                "bind='" + bind + '\'' +
                ", motd='" + motd + '\'' +
                ", showMaxPlayers=" + showMaxPlayers +
                ", onlineMode=" + onlineMode +
                ", ipForwardingMode=" + ipForwardingMode +
                ", servers=" + servers +
                ", attemptConnectionOrder=" + attemptConnectionOrder +
                '}';
    }

    public static VelocityConfiguration read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Toml toml = new Toml().read(reader);

            Map<String, String> servers = new HashMap<>();
            for (Map.Entry<String, Object> entry : toml.getTable("servers").entrySet()) {
                if (entry.getValue() instanceof String) {
                    servers.put(entry.getKey(), (String) entry.getValue());
                } else {
                    if (!entry.getKey().equalsIgnoreCase("try")) {
                        throw new IllegalArgumentException("Server entry " + entry.getKey() + " is not a string!");
                    }
                }
            }

            return new VelocityConfiguration(
                    toml.getString("bind"),
                    toml.getString("motd"),
                    toml.getLong("show-max-players").intValue(),
                    toml.getBoolean("online-mode"),
                    IPForwardingMode.valueOf(toml.getString("ip-forwarding").toUpperCase()),
                    ImmutableMap.copyOf(servers),
                    toml.getTable("servers").getList("try"));
        }
    }
}
