package de.mcbesser.tradingcards;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class QuartettListener implements Listener {

    private final TradingCardsPlugin plugin;
    private final QuartettService quartettService;

    public QuartettListener(TradingCardsPlugin plugin, QuartettService quartettService) {
        this.plugin = plugin;
        this.quartettService = quartettService;
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onQuartettChestPlace(BlockPlaceEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            quartettService.ensureSession(event.getBlockPlaced());
            for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                quartettService.ensureSession(event.getBlockPlaced().getRelative(face));
            }
        });
    }

    @EventHandler
    public void onQuartettChunkLoad(ChunkLoadEvent event) {
        quartettService.ensureSessionsInChunk(event.getChunk());
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
        if (event.getClickedInventory() == null) {
            return;
        }
        if (!event.getClickedInventory().equals(top)) {
            ItemStack current = event.getCurrentItem();
            if (current != null && quartettService.depositFromInventory(player, top, current)) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        quartettService.handleInventoryClick(player, top, event.getRawSlot());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onQuartettHologramInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!quartettService.isQuartettEntity(clicked)) {
            return;
        }
        event.setCancelled(true);
        quartettService.handleHeadInteract(event.getPlayer(), clicked);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onQuartettArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (!quartettService.isQuartettEntity(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
    }
}
