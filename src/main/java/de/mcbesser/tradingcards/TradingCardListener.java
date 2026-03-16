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
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!plugin.getCardService().isTradingCardItem(item)) {
            return;
        }
        if (!event.getPlayer().hasPermission("tradingcards.place")) {
            event.getPlayer().sendMessage("You do not have permission to place trading cards.");
            event.setCancelled(true);
            return;
        }

        BlockFace face = event.getBlockFace();
        if (face == BlockFace.UP || face == BlockFace.DOWN) {
            event.getPlayer().sendMessage("This card must be placed on a wall.");
            event.setCancelled(true);
            return;
        }

        String motifId = plugin.getCardService().getMotifId(item);
        LoadedMotif motif = motifId == null ? null : plugin.getMotifRegistry().find(motifId);
        if (motif == null) {
            event.getPlayer().sendMessage("Unknown card motif. Reload the plugin data first.");
            event.setCancelled(true);
            return;
        }

        Block supportBottom = event.getClickedBlock();
        Block supportTop = supportBottom.getRelative(BlockFace.UP);
        Block displayBottom = supportBottom.getRelative(face);
        Block displayTop = supportTop.getRelative(face);
        if (!canPlaceDisplay(supportBottom, supportTop, displayBottom, displayTop)) {
            event.getPlayer().sendMessage("Not enough space for a 1x2 card display.");
            event.setCancelled(true);
            return;
        }

        String displayId = UUID.randomUUID().toString();
        List<ItemStack> panelItems = plugin.getCardService().createPlacedDisplayItems(motif, displayId);
        spawnFrame(displayBottom.getLocation(), face, panelItems.get(1), displayId, 1);
        spawnFrame(displayTop.getLocation(), face, panelItems.get(0), displayId, 0);

        consumeOne(event.getPlayer(), item);
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractFrame(PlayerInteractEntityEvent event) {
        if (isTradingCardFrame(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamageFrame(EntityDamageByEntityEvent event) {
        if (!isTradingCardFrame(event.getEntity())) {
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
        event.setCancelled(true);
        breakDisplay((ItemFrame) event.getEntity(), null);
    }

    private boolean canPlaceDisplay(Block supportBottom, Block supportTop, Block displayBottom, Block displayTop) {
        return supportBottom.getType().isSolid()
            && supportTop.getType().isSolid()
            && displayBottom.getType().isAir()
            && displayTop.getType().isAir();
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

        for (Entity entity : sourceFrame.getWorld().getNearbyEntities(sourceFrame.getLocation(), 2.0, 2.5, 2.0)) {
            if (entity instanceof ItemFrame frame) {
                String otherDisplayId = frame.getPersistentDataContainer().get(plugin.getCardService().getDisplayIdKey(), PersistentDataType.STRING);
                if (displayId.equals(otherDisplayId)) {
                    frame.setItem(null, false);
                    frame.remove();
                }
            }
        }

        ItemStack drop = plugin.getCardService().createCardItem(motif);
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
}
