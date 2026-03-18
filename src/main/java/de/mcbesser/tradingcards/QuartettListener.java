package de.mcbesser.tradingcards;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;

public final class QuartettListener implements Listener {

    private final TradingCardsPlugin plugin;
    private final QuartettService quartettService;

    public QuartettListener(TradingCardsPlugin plugin, QuartettService quartettService) {
        this.plugin = plugin;
        this.quartettService = quartettService;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onQuartettChestInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        quartettService.ensureSession(clicked);
        if (quartettService.handleChestInteract(event.getPlayer(), clicked, event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onQuartettInventoryOpen(InventoryOpenEvent event) {
        quartettService.ensureSession(event.getInventory());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onQuartettChestBreak(BlockBreakEvent event) {
        if (!quartettService.isQuartettChest(event.getBlock())) {
            return;
        }
        quartettService.cleanupSession(event.getBlock(), true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onQuartettInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        QuartettService.Session session = quartettService.ensureSession(top);
        if (session == null) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        quartettService.handleInventoryClick(player, top, event.getRawSlot());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onQuartettHologramInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!quartettService.isQuartettHologram(clicked)) {
            return;
        }
        event.setCancelled(true);
        quartettService.handleHologramInteract(event.getPlayer(), clicked);
    }
}
