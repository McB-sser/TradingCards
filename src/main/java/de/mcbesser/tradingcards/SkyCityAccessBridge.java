package de.mcbesser.tradingcards;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class SkyCityAccessBridge {
    private static final String SKYCITY_WORLD_NAME = "skycity_world";

    private final TradingCardsPlugin plugin;
    private boolean resolved;
    private boolean available;
    private Object islandService;
    private Object skyWorldService;
    private Method getIslandAtMethod;
    private Method isChunkUnlockedMethod;
    private Method canUseBuildSettingMethod;
    private Method canUseContainerSettingMethod;
    private Method isSkyCityWorldMethod;
    private Method isDecorationsMethod;
    private Method isContainersMethod;
    private boolean warningLogged;

    public SkyCityAccessBridge(TradingCardsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean canUseDecorations(Player player, Location location) {
        return canUseDecorations(player, location, false);
    }

    public boolean canUseDecorations(Player player, Location location, boolean requireChunkUnlocked) {
        return canUseSetting(player, location, requireChunkUnlocked, canUseBuildSettingMethod, isDecorationsMethod);
    }

    public boolean canUseContainers(Player player, Location location) {
        return canUseSetting(player, location, false, canUseContainerSettingMethod, isContainersMethod);
    }

    private boolean canUseSetting(Player player, Location location, boolean requireChunkUnlocked, Method accessMethod, Method settingMethod) {
        if (player == null || location == null || player.isOp()) {
            return true;
        }
        if (!ensureResolved() || accessMethod == null || settingMethod == null) {
            return !isSkyCityWorld(location.getWorld());
        }

        try {
            World world = location.getWorld();
            if (world == null || !(boolean) isSkyCityWorldMethod.invoke(skyWorldService, world)) {
                return true;
            }

            Object island = getIslandAtMethod.invoke(islandService, location);
            if (island == null) {
                return false;
            }

            Predicate<Object> predicate = settings -> invokeSetting(settingMethod, settings);
            boolean allowed = (boolean) accessMethod.invoke(islandService, player.getUniqueId(), location, predicate);
            if (!allowed) {
                return false;
            }
            return !requireChunkUnlocked || (boolean) isChunkUnlockedMethod.invoke(islandService, island, location);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logWarning(exception);
            return !isSkyCityWorld(location.getWorld());
        }
    }

    private boolean invokeSetting(Method settingMethod, Object settings) {
        try {
            return settings != null && (boolean) settingMethod.invoke(settings);
        } catch (ReflectiveOperationException | LinkageError exception) {
            logWarning(exception);
            return false;
        }
    }

    private boolean ensureResolved() {
        if (resolved) {
            return available;
        }
        resolved = true;

        Plugin skyCity = plugin.getServer().getPluginManager().getPlugin("SkyCity");
        if (skyCity == null || !skyCity.isEnabled()) {
            return false;
        }

        try {
            Class<?> pluginClass = skyCity.getClass();
            Class<?> islandServiceClass = Class.forName("de.mcbesser.skycity.service.IslandService", false, pluginClass.getClassLoader());
            Class<?> skyWorldServiceClass = Class.forName("de.mcbesser.skycity.service.SkyWorldService", false, pluginClass.getClassLoader());
            Class<?> accessSettingsClass = Class.forName("de.mcbesser.skycity.model.AccessSettings", false, pluginClass.getClassLoader());

            islandService = readField(pluginClass, skyCity, "islandService", islandServiceClass);
            skyWorldService = readField(pluginClass, skyCity, "skyWorldService", skyWorldServiceClass);
            if (islandService == null || skyWorldService == null) {
                return false;
            }

            getIslandAtMethod = islandServiceClass.getMethod("getIslandAt", Location.class);
            isChunkUnlockedMethod = islandServiceClass.getMethod("isChunkUnlocked", Class.forName("de.mcbesser.skycity.model.IslandData", false, pluginClass.getClassLoader()), Location.class);
            canUseBuildSettingMethod = islandServiceClass.getMethod("canUseBuildSetting", UUID.class, Location.class, Predicate.class);
            canUseContainerSettingMethod = islandServiceClass.getMethod("canUseContainerSetting", UUID.class, Location.class, Predicate.class);
            isSkyCityWorldMethod = skyWorldServiceClass.getMethod("isSkyCityWorld", World.class);
            isDecorationsMethod = accessSettingsClass.getMethod("isDecorations");
            isContainersMethod = accessSettingsClass.getMethod("isContainers");
            available = true;
            plugin.getLogger().info("SkyCity-Berechtigungen erkannt und aktiviert.");
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            logWarning(exception);
            return false;
        }
    }

    private Object readField(Class<?> ownerClass, Object instance, String fieldName, Class<?> expectedType)
        throws ReflectiveOperationException {
        Class<?> current = ownerClass;
        while (current != null) {
            Field[] fields;
            try {
                fields = current.getDeclaredFields();
            } catch (LinkageError ignored) {
                fields = new Field[0];
            }
            for (Field field : fields) {
                if (!fieldName.equals(field.getName())) {
                    continue;
                }
                Class<?> fieldType;
                try {
                    fieldType = field.getType();
                } catch (LinkageError ignored) {
                    continue;
                }
                if (!expectedType.equals(fieldType)) {
                    continue;
                }
                field.setAccessible(true);
                return field.get(instance);
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private void logWarning(Throwable exception) {
        if (warningLogged) {
            return;
        }
        warningLogged = true;
        plugin.getLogger().warning("SkyCity-Integration konnte nicht vollstaendig geladen werden: " + exception.getMessage());
    }

    private boolean isSkyCityWorld(World world) {
        return world != null && SKYCITY_WORLD_NAME.equalsIgnoreCase(world.getName());
    }
}
