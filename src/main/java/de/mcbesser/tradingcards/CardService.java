package de.mcbesser.tradingcards;

import de.mcbesser.tradingcards.image.LoadedMotif;
import de.mcbesser.tradingcards.image.MetadataMapRenderer;
import de.mcbesser.tradingcards.image.PosterMapRenderer;
import de.mcbesser.tradingcards.image.TradingCardMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class CardService {

    private final TradingCardsPlugin plugin;
    private final NamespacedKey cardItemKey;
    private final NamespacedKey motifIdKey;
    private final NamespacedKey displayIdKey;
    private final NamespacedKey panelIndexKey;
    private final NamespacedKey displayPanelCountKey;
    private final NamespacedKey metadataPanelKey;
    private final NamespacedKey hiddenPanelKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey healthKey;
    private final NamespacedKey hungerKey;
    private final NamespacedKey armorKey;
    private final NamespacedKey strengthKey;

    public CardService(TradingCardsPlugin plugin) {
        this.plugin = plugin;
        this.cardItemKey = new NamespacedKey(plugin, "card_item");
        this.motifIdKey = new NamespacedKey(plugin, "motif_id");
        this.displayIdKey = new NamespacedKey(plugin, "display_id");
        this.panelIndexKey = new NamespacedKey(plugin, "panel_index");
        this.displayPanelCountKey = new NamespacedKey(plugin, "display_panel_count");
        this.metadataPanelKey = new NamespacedKey(plugin, "metadata_panel");
        this.hiddenPanelKey = new NamespacedKey(plugin, "hidden_panel");
        this.ownerKey = new NamespacedKey(plugin, "card_owner");
        this.healthKey = new NamespacedKey(plugin, "stat_health");
        this.hungerKey = new NamespacedKey(plugin, "stat_hunger");
        this.armorKey = new NamespacedKey(plugin, "stat_armor");
        this.strengthKey = new NamespacedKey(plugin, "stat_strength");
    }

    public ItemStack createCardItem(LoadedMotif motif) {
        return createCardItem(motif, CardStats.random());
    }

    public ItemStack createCardItem(LoadedMotif motif, CardStats stats) {
        ItemStack item = createMapItem(motif, 0, true, false, stats, 1);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("MapMeta is not available.");
        }

        meta.setDisplayName(motif.displayName());
        List<String> lore = new ArrayList<>(buildLore(motif, stats));
        lore.add(ChatColor.YELLOW + "Rechtsklick f\u00fcr 1x3 Anzeige.");
        lore.add(ChatColor.YELLOW + "Schleichend rechts f\u00fcr 1x2 Bild.");
        lore.add(ChatColor.YELLOW + "Schleichend links f\u00fcr Wertekarte.");
        meta.setLore(lore);

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(cardItemKey, PersistentDataType.BYTE, (byte) 1);
        data.set(motifIdKey, PersistentDataType.STRING, motif.id());
        storeStats(data, stats);
        item.setItemMeta(meta);
        return item;
    }

    public List<ItemStack> createPlacedDisplayItems(LoadedMotif motif, String displayId, int panelCount, boolean metadataOnly, CardStats stats) {
        List<ItemStack> items = new ArrayList<>();
        for (int panelIndex = 0; panelIndex < panelCount; panelIndex++) {
            boolean metadataPanel = metadataOnly || (panelCount == 3 && panelIndex == 2);
            boolean hiddenPanel = metadataPanel;
            ItemStack item = createMapItem(motif, panelIndex, metadataPanel, hiddenPanel, stats, panelCount);
            MapMeta meta = (MapMeta) item.getItemMeta();
            if (meta == null) {
                throw new IllegalStateException("MapMeta is not available.");
            }
            if (!metadataPanel && panelIndex == 0) {
                meta.setDisplayName(buildTopPanelName(motif));
                meta.setLore(buildTopPanelLore(motif));
            } else {
                meta.setLore(null);
            }

            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(displayIdKey, PersistentDataType.STRING, displayId);
            data.set(panelIndexKey, PersistentDataType.INTEGER, panelIndex);
            data.set(displayPanelCountKey, PersistentDataType.INTEGER, panelCount);
            data.set(motifIdKey, PersistentDataType.STRING, motif.id());
            if (metadataPanel) {
                data.set(metadataPanelKey, PersistentDataType.BYTE, (byte) 1);
            }
            if (hiddenPanel) {
                data.set(hiddenPanelKey, PersistentDataType.BYTE, (byte) 1);
            }
            storeStats(data, stats);
            item.setItemMeta(meta);
            items.add(item);
        }
        return items;
    }

    private String buildTopPanelName(LoadedMotif motif) {
        TradingCardMetadata metadata = motif.metadata();
        String title = metadata.title() != null ? metadata.title() : motif.displayName();
        return ChatColor.GOLD + title;
    }

    private List<String> buildTopPanelLore(LoadedMotif motif) {
        TradingCardMetadata metadata = motif.metadata();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "----------------");
        if (metadata.number() != null) {
            lore.add(ChatColor.YELLOW + "Nummer: " + ChatColor.WHITE + metadata.number());
        }
        if (metadata.rarity() != null) {
            lore.add(ChatColor.AQUA + "Seltenheit: " + ChatColor.WHITE + metadata.rarity());
        }
        if (metadata.title() != null) {
            lore.add(ChatColor.GOLD + "Name: " + ChatColor.WHITE + metadata.title());
        }
        lore.add(ChatColor.DARK_GRAY + "----------------");
        return lore.isEmpty() ? null : lore;
    }

    public boolean isTradingCardItem(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer().get(cardItemKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public String getMotifId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(motifIdKey, PersistentDataType.STRING);
    }

    public NamespacedKey getDisplayIdKey() {
        return displayIdKey;
    }

    public NamespacedKey getMotifIdKey() {
        return motifIdKey;
    }

    public NamespacedKey getPanelIndexKey() {
        return panelIndexKey;
    }

    public boolean isMetadataPanel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        return data.has(metadataPanelKey, PersistentDataType.BYTE) || data.has(cardItemKey, PersistentDataType.BYTE);
    }

    public boolean isHidden(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(hiddenPanelKey, PersistentDataType.BYTE);
    }

    public void setOwner(ItemStack item, UUID ownerId) {
        if (item == null || !item.hasItemMeta() || ownerId == null) {
            return;
        }
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, ownerId.toString());
        item.setItemMeta(meta);
    }

    public UUID getOwner(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean toggleHidden(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (!data.has(metadataPanelKey, PersistentDataType.BYTE)) {
            return false;
        }
        boolean hidden = data.has(hiddenPanelKey, PersistentDataType.BYTE);
        if (hidden) {
            data.remove(hiddenPanelKey);
        } else {
            data.set(hiddenPanelKey, PersistentDataType.BYTE, (byte) 1);
        }
        item.setItemMeta(meta);
        rebindItem(item);
        return true;
    }

    public CardStats getStats(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return CardStats.random();
        }
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null) {
            return CardStats.random();
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        CardStats stats = readStats(data);
        if (stats != null) {
            return stats;
        }
        stats = CardStats.random();
        storeStats(data, stats);
        item.setItemMeta(meta);
        return stats;
    }

    public void rebindLoadedMaps() {
        for (World world : Bukkit.getWorlds()) {
            world.getEntitiesByClass(ItemFrame.class).forEach(this::rebindFrame);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            rebindPlayerInventory(player);
        }
    }

    public void rebindPlayerInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        rebindContents(inventory.getContents());
        rebindContents(inventory.getArmorContents());
        rebindItem(inventory.getItemInOffHand());
        rebindContents(player.getEnderChest().getContents());
    }

    public void rebindFrame(ItemFrame frame) {
        if (!frame.getPersistentDataContainer().has(displayIdKey, PersistentDataType.STRING)) {
            return;
        }
        ItemStack item = frame.getItem();
        if (item == null || item.getType() != Material.FILLED_MAP) {
            return;
        }
        rebindItem(item);
        frame.setItem(item, false);
    }

    public void rebindContents(ItemStack[] items) {
        if (items == null) {
            return;
        }
        for (ItemStack item : items) {
            rebindItem(item);
        }
    }

    public void rebindItem(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || !item.hasItemMeta()) {
            return;
        }
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null || meta.getMapView() == null) {
            return;
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        String motifId = data.get(motifIdKey, PersistentDataType.STRING);
        if (motifId == null) {
            return;
        }

        LoadedMotif motif = plugin.getMotifRegistry().find(motifId);
        if (motif == null) {
            return;
        }

        CardStats stats = readStats(data);
        if (stats == null) {
            stats = CardStats.random();
            storeStats(data, stats);
        }

        boolean metadataView = data.has(cardItemKey, PersistentDataType.BYTE)
            || data.has(metadataPanelKey, PersistentDataType.BYTE)
            || Integer.valueOf(1).equals(data.get(displayPanelCountKey, PersistentDataType.INTEGER));
        boolean hiddenPanel = data.has(hiddenPanelKey, PersistentDataType.BYTE);
        Integer storedPanelIndex = data.get(panelIndexKey, PersistentDataType.INTEGER);
        int panelIndex = storedPanelIndex != null ? storedPanelIndex : 0;
        Integer storedPanelCount = data.get(displayPanelCountKey, PersistentDataType.INTEGER);
        int panelCount;
        if (storedPanelCount != null) {
            panelCount = storedPanelCount;
        } else if (metadataView) {
            panelCount = 1;
        } else {
            panelCount = panelIndex >= 2 ? 3 : 2;
        }
        rebindMapView(meta.getMapView(), motif, panelIndex, metadataView, hiddenPanel, stats, panelCount);
        item.setItemMeta(meta);
    }

    private ItemStack createMapItem(LoadedMotif motif, int panelIndex, boolean metadataView, boolean hiddenPanel, CardStats stats, int panelCount) {
        World world = resolveWorld();
        if (world == null) {
            throw new IllegalStateException("No loaded world available for creating maps.");
        }

        MapView mapView = Bukkit.createMap(world);
        rebindMapView(mapView, motif, panelIndex, metadataView, hiddenPanel, stats, panelCount);

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("MapMeta is not available.");
        }
        meta.setMapView(mapView);
        item.setItemMeta(meta);
        return item;
    }

    private void rebindMapView(MapView mapView, LoadedMotif motif, int panelIndex, boolean metadataView, boolean hiddenPanel, CardStats stats, int panelCount) {
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);
        mapView.setLocked(true);

        List<MapRenderer> renderers = new ArrayList<>(mapView.getRenderers());
        for (MapRenderer renderer : renderers) {
            mapView.removeRenderer(renderer);
        }
        mapView.addRenderer(metadataView
            ? new MetadataMapRenderer(motif, stats, hiddenPanel, panelCount == 1)
            : new PosterMapRenderer(motif, panelIndex, stats, panelCount));
    }

    private World resolveWorld() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    private List<String> buildLore(LoadedMotif motif, CardStats stats) {
        TradingCardMetadata metadata = motif.metadata();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GOLD + "Sammelkarte");

        if (metadata.series() != null || metadata.number() != null) {
            StringBuilder line = new StringBuilder(ChatColor.YELLOW.toString());
            if (metadata.series() != null) {
                line.append(metadata.series());
            }
            if (metadata.number() != null) {
                if (line.length() > ChatColor.YELLOW.toString().length()) {
                    line.append(" ");
                }
                line.append("#").append(metadata.number());
            }
            lore.add(line.toString());
        }
        if (metadata.rarity() != null) {
            lore.add(ChatColor.AQUA + "Seltenheit: " + ChatColor.WHITE + metadata.rarity());
        }
        lore.add(ChatColor.RED + "Leben: " + ChatColor.WHITE + String.valueOf(stats.health()));
        lore.add(ChatColor.GOLD + "Hunger: " + ChatColor.WHITE + String.valueOf(stats.hunger()));
        lore.add(ChatColor.BLUE + "R\u00fcstung: " + ChatColor.WHITE + String.valueOf(stats.armor()));
        lore.add(ChatColor.LIGHT_PURPLE + "Kraft: " + ChatColor.WHITE + String.valueOf(stats.strength()));
        double multiplier = rarityMultiplier(metadata.rarity());
        lore.add(ChatColor.GRAY + "Multiplikator: " + ChatColor.WHITE + "x" + String.format(java.util.Locale.US, "%.2f", multiplier));
        lore.add(ChatColor.YELLOW + "Kartenwert: " + ChatColor.WHITE + String.valueOf(cardValue(stats, multiplier)));
        if (metadata.artist() != null) {
            lore.add(ChatColor.GRAY + "K\u00fcnstler: " + ChatColor.WHITE + metadata.artist());
        }
        if (metadata.description() != null) {
            lore.addAll(wrapLoreLine(ChatColor.WHITE, metadata.description()));
        }
        if (metadata.flavorText() != null) {
            lore.addAll(wrapLoreLine(ChatColor.DARK_GRAY, "\"" + metadata.flavorText() + "\""));
        }
        if (!metadata.tags().isEmpty()) {
            lore.add(ChatColor.DARK_GREEN + "Tags: " + ChatColor.GREEN + String.join(", ", metadata.tags()));
        }
        return lore;
    }


    private void storeStats(PersistentDataContainer data, CardStats stats) {
        data.set(healthKey, PersistentDataType.INTEGER, stats.health());
        data.set(hungerKey, PersistentDataType.INTEGER, stats.hunger());
        data.set(armorKey, PersistentDataType.INTEGER, stats.armor());
        data.set(strengthKey, PersistentDataType.INTEGER, stats.strength());
    }

    private CardStats readStats(PersistentDataContainer data) {
        Integer health = data.get(healthKey, PersistentDataType.INTEGER);
        Integer hunger = data.get(hungerKey, PersistentDataType.INTEGER);
        Integer armor = data.get(armorKey, PersistentDataType.INTEGER);
        Integer strength = data.get(strengthKey, PersistentDataType.INTEGER);
        if (health == null || hunger == null || armor == null || strength == null) {
            return null;
        }
        return new CardStats(health, hunger, armor, strength);
    }

    private List<String> wrapLoreLine(ChatColor color, String text) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder(color.toString());
        for (String word : words) {
            int visibleLength = ChatColor.stripColor(currentLine.toString()).length();
            if (visibleLength > 0 && visibleLength + word.length() + 1 > 32) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(color.toString()).append(word);
            } else {
                if (ChatColor.stripColor(currentLine.toString()).length() > 0) {
                    currentLine.append(' ');
                }
                currentLine.append(word);
            }
        }
        if (ChatColor.stripColor(currentLine.toString()).length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private double rarityMultiplier(String rarity) {
        if (rarity == null) {
            return 1.00D;
        }
        return switch (rarity.toLowerCase(java.util.Locale.ROOT)) {
            case "uncommon" -> 1.15D;
            case "rare" -> 1.35D;
            case "epic" -> 1.65D;
            case "legendary" -> 2.00D;
            default -> 1.00D;
        };
    }

    private int cardValue(CardStats stats, double multiplier) {
        int total = stats.health() + stats.hunger() + stats.armor() + stats.strength();
        return (int) Math.round(total * multiplier * 10);
    }
}
