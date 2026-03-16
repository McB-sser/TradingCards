package de.mcbesser.tradingcards;

import de.mcbesser.tradingcards.image.LoadedMotif;
import de.mcbesser.tradingcards.image.MetadataMapRenderer;
import de.mcbesser.tradingcards.image.PosterMapRenderer;
import de.mcbesser.tradingcards.image.TradingCardMetadata;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
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

    public CardService(TradingCardsPlugin plugin) {
        this.plugin = plugin;
        this.cardItemKey = new NamespacedKey(plugin, "card_item");
        this.motifIdKey = new NamespacedKey(plugin, "motif_id");
        this.displayIdKey = new NamespacedKey(plugin, "display_id");
        this.panelIndexKey = new NamespacedKey(plugin, "panel_index");
    }

    public ItemStack createCardItem(LoadedMotif motif) {
        ItemStack item = createMapItem(motif, 0, true);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("MapMeta is not available.");
        }

        meta.setDisplayName(motif.displayName());
        List<String> lore = new ArrayList<>(buildLore(motif));
        lore.add(ChatColor.YELLOW + "Place on a wall to create");
        lore.add(ChatColor.YELLOW + "a 1x2 painting display.");
        meta.setLore(lore);

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(cardItemKey, PersistentDataType.BYTE, (byte) 1);
        data.set(motifIdKey, PersistentDataType.STRING, motif.id());
        item.setItemMeta(meta);
        return item;
    }

    public List<ItemStack> createPlacedDisplayItems(LoadedMotif motif, String displayId) {
        List<ItemStack> items = new ArrayList<>();
        for (int panelIndex = 0; panelIndex < 2; panelIndex++) {
            ItemStack item = createMapItem(motif, panelIndex, false);
            MapMeta meta = (MapMeta) item.getItemMeta();
            if (meta == null) {
                throw new IllegalStateException("MapMeta is not available.");
            }
            if (panelIndex == 0) {
                meta.setDisplayName(buildTopPanelName(motif));
                meta.setLore(buildTopPanelLore(motif));
            } else {
                meta.setLore(null);
            }

            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(displayIdKey, PersistentDataType.STRING, displayId);
            data.set(panelIndexKey, PersistentDataType.INTEGER, panelIndex);
            data.set(motifIdKey, PersistentDataType.STRING, motif.id());
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
            lore.add(ChatColor.YELLOW + "Number: " + ChatColor.WHITE + metadata.number());
        }
        if (metadata.rarity() != null) {
            lore.add(ChatColor.AQUA + "Rarity: " + ChatColor.WHITE + metadata.rarity());
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

    private ItemStack createMapItem(LoadedMotif motif, int panelIndex, boolean metadataView) {
        World world = resolveWorld();
        if (world == null) {
            throw new IllegalStateException("No loaded world available for creating maps.");
        }

        MapView mapView = Bukkit.createMap(world);
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);
        mapView.setLocked(true);

        List<MapRenderer> renderers = new ArrayList<>(mapView.getRenderers());
        for (MapRenderer renderer : renderers) {
            mapView.removeRenderer(renderer);
        }
        mapView.addRenderer(metadataView ? new MetadataMapRenderer(motif) : new PosterMapRenderer(motif, panelIndex));

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("MapMeta is not available.");
        }
        meta.setMapView(mapView);
        item.setItemMeta(meta);
        return item;
    }

    private World resolveWorld() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    private List<String> buildLore(LoadedMotif motif) {
        TradingCardMetadata metadata = motif.metadata();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GOLD + "Trading Card");

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
            lore.add(ChatColor.AQUA + "Rarity: " + ChatColor.WHITE + metadata.rarity());
        }
        if (metadata.artist() != null) {
            lore.add(ChatColor.GRAY + "Artist: " + ChatColor.WHITE + metadata.artist());
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
        lore.add(ChatColor.DARK_GRAY + "Motif: " + motif.id());
        return lore;
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
}
