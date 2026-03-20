package de.mcbesser.tradingcards;

import de.mcbesser.tradingcards.image.LoadedMotif;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class TradingCardListener implements Listener {

    private final TradingCardsPlugin plugin;

    public TradingCardListener(TradingCardsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlaceCard(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null || event.getBlockFace() == null) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (plugin.getQuartettService() != null && plugin.getQuartettService().isQuartettChest(event.getClickedBlock())) {
            return;
        }

        ItemStack item = event.getItem();
        if (!plugin.getCardService().isTradingCardItem(item)) {
            return;
        }
        if (!event.getPlayer().hasPermission("tradingcards.place")) {
            event.getPlayer().sendMessage("Du darfst diese Sammelkarten nicht platzieren.");
            event.setCancelled(true);
            return;
        }

        BlockFace face = event.getBlockFace();
        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            event.getPlayer().sendMessage("Diese Karte muss an einer Wand platziert werden.");
            event.setCancelled(true);
            return;
        }

        String motifId = plugin.getCardService().getMotifId(item);
        LoadedMotif motif = motifId == null ? null : plugin.getMotifRegistry().find(motifId);
        if (motif == null) {
            event.getPlayer().sendMessage("Unbekanntes Kartenmotiv. Lade die Plugin-Daten neu.");
            event.setCancelled(true);
            return;
        }

        DisplayMode mode = resolveDisplayMode(event);
        if (mode == null) {
            return;
        }
        CardStats stats = plugin.getCardService().getStats(item);

        Block supportBottom = event.getClickedBlock();
        Block supportMiddle = supportBottom.getRelative(BlockFace.UP);
        Block supportTop = mode == DisplayMode.FULL ? supportMiddle.getRelative(BlockFace.UP) : null;
        Block displayBottom = supportBottom.getRelative(face);
        Block displayMiddle = supportMiddle.getRelative(face);
        Block displayTop = mode == DisplayMode.FULL ? supportTop.getRelative(face) : null;
        if (!canPlaceDisplay(mode, supportBottom, supportMiddle, supportTop, displayBottom, displayMiddle, displayTop)) {
            event.getPlayer().sendMessage(switch (mode) {
                case FULL -> "Nicht genug Platz f\u00fcr eine 1x3 Kartenanzeige.";
                case IMAGE_ONLY -> "Nicht genug Platz f\u00fcr eine 1x2 Bildanzeige.";
                case CARD_ONLY -> "Nicht genug Platz f\u00fcr eine Wertekarte.";
            });
            event.setCancelled(true);
            return;
        }

        String displayId = UUID.randomUUID().toString();
        List<ItemStack> panelItems = plugin.getCardService().createPlacedDisplayItems(motif, displayId, mode.panelCount(), mode == DisplayMode.CARD_ONLY, stats);
        if (mode == DisplayMode.FULL) {
            spawnFrame(displayBottom.getLocation(), face, panelItems.get(2), displayId, 2);
            spawnFrame(displayMiddle.getLocation(), face, panelItems.get(1), displayId, 1);
            spawnFrame(displayTop.getLocation(), face, panelItems.get(0), displayId, 0);
        } else if (mode == DisplayMode.IMAGE_ONLY) {
            spawnFrame(displayBottom.getLocation(), face, panelItems.get(1), displayId, 1);
            spawnFrame(displayMiddle.getLocation(), face, panelItems.get(0), displayId, 0);
        } else {
            spawnFrame(displayBottom.getLocation(), face, panelItems.get(0), displayId, 0);
        }

        consumeOne(event.getPlayer(), item);
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getCardService().rebindPlayerInventory(event.getPlayer());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof ItemFrame frame) {
                plugin.getCardService().rebindFrame(frame);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getCardService().rebindItem(event.getCurrentItem());
            plugin.getCardService().rebindItem(event.getCursor());
            plugin.getCardService().rebindPlayerInventory((Player) event.getWhoClicked());
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getCardService().rebindPlayerInventory((Player) event.getWhoClicked());
            event.getNewItems().values().forEach(plugin.getCardService()::rebindItem);
        });
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getCardService().rebindItem(event.getItem().getItemStack());
            plugin.getCardService().rebindPlayerInventory(player);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractFrame(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame frame) || !isTradingCardFrame(frame)) {
            return;
        }
        event.setCancelled(true);
        if (!event.getPlayer().hasPermission("tradingcards.place")) {
            return;
        }
        if (plugin.getQuartettService() != null
            && plugin.getQuartettService().isQuartettRoundFrame(frame)
            && !plugin.getQuartettService().canRevealQuartettFrame(event.getPlayer(), frame)) {
            return;
        }
        ItemStack item = frame.getItem();
        java.util.UUID owner = plugin.getCardService().getOwner(item);
        if (owner != null && !owner.equals(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage("Nur der Besitzer kann diese Karte aufdecken.");
            return;
        }
        if (plugin.getCardService().toggleHidden(item)) {
            frame.setItem(item, false);
            if (plugin.getQuartettService() != null && plugin.getQuartettService().isQuartettRoundFrame(frame)) {
                plugin.getQuartettService().refreshRoundState(frame);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamageFrame(EntityDamageByEntityEvent event) {
        if (!isTradingCardFrame(event.getEntity())) {
            return;
        }
        if (plugin.getCardService().getOwner(((ItemFrame) event.getEntity()).getItem()) != null) {
            event.setCancelled(true);
            return;
        }
        Player player = event.getDamager() instanceof Player ? (Player) event.getDamager() : null;
        if (player == null || !player.hasPermission("tradingcards.break")) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        breakDisplay((ItemFrame) event.getEntity(), player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreakFrame(HangingBreakEvent event) {
        if (!isTradingCardFrame(event.getEntity())) {
            return;
        }
        if (plugin.getCardService().getOwner(((ItemFrame) event.getEntity()).getItem()) != null) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        breakDisplay((ItemFrame) event.getEntity(), null);
    }

    private boolean canPlaceDisplay(DisplayMode mode, Block supportBottom, Block supportMiddle, Block supportTop, Block displayBottom, Block displayMiddle, Block displayTop) {
        if (!supportBottom.getType().isSolid() || !displayBottom.getType().isAir()) {
            return false;
        }
        if (mode == DisplayMode.CARD_ONLY) {
            return true;
        }
        if (!supportMiddle.getType().isSolid() || !displayMiddle.getType().isAir()) {
            return false;
        }
        if (mode == DisplayMode.IMAGE_ONLY) {
            return true;
        }
        return supportTop != null && supportTop.getType().isSolid() && displayTop != null && displayTop.getType().isAir();
    }

    private DisplayMode resolveDisplayMode(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            return event.getPlayer().isSneaking() ? DisplayMode.IMAGE_ONLY : DisplayMode.FULL;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getPlayer().isSneaking()) {
            return DisplayMode.CARD_ONLY;
        }
        return null;
    }

    private void spawnFrame(Location location, BlockFace face, ItemStack item, String displayId, int panelIndex) {
        location.getWorld().spawn(location, ItemFrame.class, spawned -> {
            spawned.setFacingDirection(face, true);
            spawned.setVisible(false);
            spawned.setItem(item, false);
            PersistentDataContainer data = spawned.getPersistentDataContainer();
            data.set(plugin.getCardService().getDisplayIdKey(), PersistentDataType.STRING, displayId);
            data.set(plugin.getCardService().getPanelIndexKey(), PersistentDataType.INTEGER, panelIndex);
            data.set(plugin.getCardService().getMotifIdKey(), PersistentDataType.STRING, plugin.getCardService().getMotifId(item));
        });
    }

    private boolean isTradingCardFrame(Entity entity) {
        if (!(entity instanceof ItemFrame frame)) {
            return false;
        }
        return frame.getPersistentDataContainer().has(plugin.getCardService().getDisplayIdKey(), PersistentDataType.STRING);
    }

    private void breakDisplay(ItemFrame sourceFrame, Player breaker) {
        PersistentDataContainer data = sourceFrame.getPersistentDataContainer();
        String displayId = data.get(plugin.getCardService().getDisplayIdKey(), PersistentDataType.STRING);
        String motifId = data.get(plugin.getCardService().getMotifIdKey(), PersistentDataType.STRING);
        if (displayId == null || motifId == null) {
            return;
        }

        LoadedMotif motif = plugin.getMotifRegistry().find(motifId);
        if (motif == null) {
            return;
        }

        ItemStack sourceItem = sourceFrame.getItem();
        CardStats stats = plugin.getCardService().getStats(sourceItem);

        for (Entity entity : sourceFrame.getWorld().getNearbyEntities(sourceFrame.getLocation(), 2.0, 3.5, 2.0)) {
            if (entity instanceof ItemFrame frame) {
                String otherDisplayId = frame.getPersistentDataContainer().get(plugin.getCardService().getDisplayIdKey(), PersistentDataType.STRING);
                if (displayId.equals(otherDisplayId)) {
                    frame.setItem(null, false);
                    frame.remove();
                }
            }
        }

        ItemStack drop = plugin.getCardService().createCardItem(motif, stats);
        if (breaker != null) {
            java.util.HashMap<Integer, ItemStack> leftovers = breaker.getInventory().addItem(drop);
            leftovers.values().forEach(item -> breaker.getWorld().dropItemNaturally(sourceFrame.getLocation(), item));
        } else {
            sourceFrame.getWorld().dropItemNaturally(sourceFrame.getLocation(), drop);
        }
    }

    private void consumeOne(Player player, ItemStack item) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        item.setAmount(item.getAmount() - 1);
    }

    private enum DisplayMode {
        FULL(3),
        IMAGE_ONLY(2),
        CARD_ONLY(1);

        private final int panelCount;

        DisplayMode(int panelCount) {
            this.panelCount = panelCount;
        }

        public int panelCount() {
            return panelCount;
        }
    }
}
