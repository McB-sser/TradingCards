package de.mcbesser.tradingcards;

import de.mcbesser.tradingcards.image.LoadedMotif;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class QuartettService {

    private static final int[] LEFT_OWN_SLOTS = {9, 10, 11, 18, 19, 20};
    private static final int[] RIGHT_OWN_SLOTS = {15, 16, 17, 24, 25, 26};
    private static final int[] LEFT_NEW_SLOTS = {36, 37, 38, 45, 46, 47};
    private static final int[] RIGHT_NEW_SLOTS = {42, 43, 44, 51, 52, 53};
    private static final int LEFT_PREV_SLOT = 27;
    private static final int LEFT_PAGE_SLOT = 28;
    private static final int LEFT_NEXT_SLOT = 29;
    private static final int MODE_SLOT = 30;
    private static final int TURN_SLOT = 32;
    private static final int RIGHT_PREV_SLOT = 33;
    private static final int RIGHT_PAGE_SLOT = 34;
    private static final int RIGHT_NEXT_SLOT = 35;
    private static final int COLLECT_SLOT = 21;
    private static final int RETURN_SLOT = 23;

    private final TradingCardsPlugin plugin;
    private final NamespacedKey quartettSessionKey;
    private final NamespacedKey quartettTypeKey;
    private final NamespacedKey quartettSideKey;
    private final Map<String, Session> sessions = new HashMap<>();

    public QuartettService(TradingCardsPlugin plugin) {
        this.plugin = plugin;
        this.quartettSessionKey = new NamespacedKey(plugin, "quartett_session");
        this.quartettTypeKey = new NamespacedKey(plugin, "quartett_type");
        this.quartettSideKey = new NamespacedKey(plugin, "quartett_side");
    }

    public Session ensureSession(Block block) {
        Chest chest = resolveQuartettChest(block);
        if (chest == null) {
            return null;
        }

        if (!(chest.getInventory().getHolder() instanceof DoubleChest doubleChest)) {
            return null;
        }
        Chest leftChest = (Chest) doubleChest.getLeftSide();
        Chest rightChest = (Chest) doubleChest.getRightSide();
        String sessionId = sessionId(leftChest.getBlock(), rightChest.getBlock());
        Session session = sessions.get(sessionId);
        if (session == null) {
            session = new Session(sessionId, leftChest.getBlock(), rightChest.getBlock(), facingOf(leftChest.getBlock()));
            sessions.put(sessionId, session);
            spawnHolograms(session);
        }

        if (!session.isValid()) {
            session.clearWorldState();
            spawnHolograms(session);
        }

        refresh(session);
        return session;
    }

    public Session ensureSession(Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof DoubleChest doubleChest)) {
            return null;
        }
        Chest leftChest = (Chest) doubleChest.getLeftSide();
        return ensureSession(leftChest.getBlock());
    }

    public boolean isQuartettHologram(Entity entity) {
        if (!(entity instanceof ArmorStand stand)) {
            return false;
        }
        return stand.getPersistentDataContainer().has(quartettSessionKey, PersistentDataType.STRING);
    }

    public boolean handleHologramInteract(Player player, ArmorStand stand) {
        PersistentDataContainer data = stand.getPersistentDataContainer();
        String sessionId = data.get(quartettSessionKey, PersistentDataType.STRING);
        String type = data.get(quartettTypeKey, PersistentDataType.STRING);
        String side = data.get(quartettSideKey, PersistentDataType.STRING);
        if (sessionId == null || type == null) {
            return false;
        }

        Session session = sessions.get(sessionId);
        if (session == null || !session.isValid()) {
            return false;
        }

        if ("center".equals(type)) {
            return chooseMode(player, session);
        }
        if ("slot".equals(type) && side != null) {
            return join(player, session, Side.valueOf(side));
        }
        return false;
    }

    public boolean handleChestInteract(Player player, Block clickedBlock, ItemStack heldItem) {
        Session session = ensureSession(clickedBlock);
        if (session == null || !plugin.getCardService().isTradingCardItem(heldItem)) {
            return false;
        }

        Side side = session.sideOf(player.getUniqueId());
        if (side == null) {
            player.sendMessage("Du spielst an dieser Quartett-Kiste nicht mit.");
            return true;
        }
        if (session.cardFrames.containsKey(side)) {
            player.sendMessage("Du hast bereits eine Karte in diesem Quartett gelegt.");
            return true;
        }

        Block cardBlock = session.cardBlock(side);
        if (cardBlock.getType() != Material.AIR && cardBlock.getType() != Material.BARRIER) {
            player.sendMessage("Vor deiner Seite ist kein Platz fuer die Datenkarte.");
            return true;
        }

        String motifId = plugin.getCardService().getMotifId(heldItem);
        LoadedMotif motif = motifId == null ? null : plugin.getMotifRegistry().find(motifId);
        if (motif == null) {
            player.sendMessage("Diese Karte hat kein gueltiges Motiv.");
            return true;
        }

        CardStats stats = plugin.getCardService().getStats(heldItem);
        ItemStack displayCard = plugin.getCardService().createPlacedDisplayItems(motif, UUID.randomUUID().toString(), 1, true, stats).get(0);
        plugin.getCardService().setOwner(displayCard, player.getUniqueId());

        ItemStack storedCard = plugin.getCardService().createCardItem(motif, stats);
        plugin.getCardService().setOwner(storedCard, player.getUniqueId());

        cardBlock.setType(Material.BARRIER, false);
        ItemFrame frame = cardBlock.getWorld().spawn(cardBlock.getLocation(), ItemFrame.class, spawned -> {
            spawned.setFacingDirection(session.facing, true);
            spawned.setVisible(false);
            spawned.setItem(displayCard, false);
        });

        session.cardFrames.put(side, frame);
        session.roundCards.put(side, storedCard);
        session.ownCards.get(side).add(storedCard.clone());
        consumeOne(player, heldItem);
        refresh(session);
        player.sendMessage("Deine Quartett-Karte wurde verdeckt platziert.");
        return true;
    }

    public void refresh(Session session) {
        if (session == null) {
            return;
        }
        updateHolograms(session);
        renderInventory(session);
    }

    public void handleInventoryClick(Player player, Inventory inventory, int rawSlot) {
        Session session = ensureSession(inventory);
        if (session == null) {
            return;
        }

        if (rawSlot == LEFT_PREV_SLOT) {
            changePage(session, Side.LEFT, -1);
            return;
        }
        if (rawSlot == LEFT_NEXT_SLOT) {
            changePage(session, Side.LEFT, 1);
            return;
        }
        if (rawSlot == RIGHT_PREV_SLOT) {
            changePage(session, Side.RIGHT, -1);
            return;
        }
        if (rawSlot == RIGHT_NEXT_SLOT) {
            changePage(session, Side.RIGHT, 1);
            return;
        }
        if (rawSlot == COLLECT_SLOT) {
            session.transferMode = TransferMode.COLLECT;
            refresh(session);
            player.sendMessage("Quartett ist auf Einsammeln gestellt.");
            return;
        }
        if (rawSlot == RETURN_SLOT) {
            session.transferMode = TransferMode.RETURN;
            refresh(session);
            player.sendMessage("Quartett ist auf Gegnerkarten wiedergeben gestellt.");
            return;
        }

        ClickTarget target = clickTarget(rawSlot);
        if (target == null) {
            return;
        }

        int page = session.pages.get(target.side);
        List<ItemStack> source = target.newCards ? session.newCards.get(target.side) : session.ownCards.get(target.side);
        int index = (page * target.slots.length) + target.index;
        if (index < 0 || index >= source.size()) {
            return;
        }

        ItemStack item = source.get(index);
        if (!mayTake(player, session, target.side, item)) {
            player.sendMessage("Diese Karte kannst du gerade nicht nehmen.");
            return;
        }

        source.remove(index);
        player.getInventory().addItem(item.clone()).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        refresh(session);
    }

    private boolean mayTake(Player player, Session session, Side displaySide, ItemStack item) {
        if (session.transferMode == TransferMode.NONE) {
            return false;
        }
        if (session.transferMode == TransferMode.COLLECT) {
            return session.sideOf(player.getUniqueId()) == displaySide;
        }
        UUID owner = plugin.getCardService().getOwner(item);
        return owner != null && owner.equals(player.getUniqueId());
    }

    private ClickTarget clickTarget(int rawSlot) {
        ClickTarget target = matchSlots(rawSlot, Side.LEFT, false, LEFT_OWN_SLOTS);
        if (target != null) {
            return target;
        }
        target = matchSlots(rawSlot, Side.RIGHT, false, RIGHT_OWN_SLOTS);
        if (target != null) {
            return target;
        }
        target = matchSlots(rawSlot, Side.LEFT, true, LEFT_NEW_SLOTS);
        if (target != null) {
            return target;
        }
        return matchSlots(rawSlot, Side.RIGHT, true, RIGHT_NEW_SLOTS);
    }

    private ClickTarget matchSlots(int rawSlot, Side side, boolean newCards, int[] slots) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == rawSlot) {
                return new ClickTarget(side, newCards, slots, i);
            }
        }
        return null;
    }

    private void changePage(Session session, Side side, int delta) {
        int page = session.pages.get(side) + delta;
        int maxPage = Math.max(maxPage(session.ownCards.get(side), side == Side.LEFT ? LEFT_OWN_SLOTS.length : RIGHT_OWN_SLOTS.length),
            maxPage(session.newCards.get(side), side == Side.LEFT ? LEFT_NEW_SLOTS.length : RIGHT_NEW_SLOTS.length));
        session.pages.put(side, Math.max(0, Math.min(maxPage, page)));
        refresh(session);
    }

    private int maxPage(List<ItemStack> items, int pageSize) {
        return Math.max(0, (items.size() - 1) / pageSize);
    }

    private boolean join(Player player, Session session, Side side) {
        UUID current = session.players.get(side);
        if (current != null && !current.equals(player.getUniqueId())) {
            player.sendMessage("Diese Seite ist bereits belegt.");
            return true;
        }
        if (session.sideOf(player.getUniqueId()) != null) {
            player.sendMessage("Du spielst bereits an dieser Quartett-Kiste mit.");
            return true;
        }
        session.players.put(side, player.getUniqueId());
        if (session.chooser == null) {
            session.chooser = side;
        }
        refresh(session);
        player.sendMessage("Du spielst jetzt im Quartett auf der " + side.label + " Seite mit.");
        return true;
    }

    private boolean chooseMode(Player player, Session session) {
        Side chooser = session.chooser;
        if (chooser == null || !player.getUniqueId().equals(session.players.get(chooser))) {
            player.sendMessage("Du bist nicht mit der Attributswahl dran.");
            return true;
        }
        ItemFrame leftFrame = session.cardFrames.get(Side.LEFT);
        ItemFrame rightFrame = session.cardFrames.get(Side.RIGHT);
        if (leftFrame == null || rightFrame == null) {
            player.sendMessage("Beide Spieler muessen zuerst eine Karte legen.");
            return true;
        }
        if (plugin.getCardService().isHidden(leftFrame.getItem()) || plugin.getCardService().isHidden(rightFrame.getItem())) {
            player.sendMessage("Beide Karten muessen zuerst aufgedeckt sein.");
            return true;
        }

        session.mode = session.mode.next();
        session.winner = compare(session.mode, leftFrame.getItem(), rightFrame.getItem());
        settleRound(session);
        session.chooser = session.winner != null ? session.winner : session.chooser.other();
        refresh(session);
        player.sendMessage("Vergleichsmodus: " + session.mode.label);
        return true;
    }

    private Side compare(CompareMode mode, ItemStack leftItem, ItemStack rightItem) {
        int leftValue = modeValue(mode, leftItem);
        int rightValue = modeValue(mode, rightItem);
        if (leftValue == rightValue) {
            return null;
        }
        return leftValue > rightValue ? Side.LEFT : Side.RIGHT;
    }

    private void settleRound(Session session) {
        ItemStack leftStored = session.roundCards.remove(Side.LEFT);
        ItemStack rightStored = session.roundCards.remove(Side.RIGHT);

        if (session.winner == Side.LEFT && leftStored != null && rightStored != null) {
            removeLatestOwned(session.ownCards.get(Side.RIGHT), plugin.getCardService().getOwner(rightStored));
            session.newCards.get(Side.LEFT).add(rightStored.clone());
        } else if (session.winner == Side.RIGHT && leftStored != null && rightStored != null) {
            removeLatestOwned(session.ownCards.get(Side.LEFT), plugin.getCardService().getOwner(leftStored));
            session.newCards.get(Side.RIGHT).add(leftStored.clone());
        }

        clearFrame(session, Side.LEFT);
        clearFrame(session, Side.RIGHT);
    }

    private void removeLatestOwned(List<ItemStack> items, UUID owner) {
        for (int i = items.size() - 1; i >= 0; i--) {
            UUID itemOwner = plugin.getCardService().getOwner(items.get(i));
            if (owner != null && owner.equals(itemOwner)) {
                items.remove(i);
                return;
            }
        }
    }

    private void clearFrame(Session session, Side side) {
        ItemFrame frame = session.cardFrames.remove(side);
        if (frame == null) {
            return;
        }
        Block cardBlock = session.cardBlock(side);
        if (cardBlock.getType() == Material.BARRIER) {
            cardBlock.setType(Material.AIR, false);
        }
        frame.setItem(null, false);
        frame.remove();
    }

    private int modeValue(CompareMode mode, ItemStack item) {
        CardStats stats = plugin.getCardService().getStats(item);
        return switch (mode) {
            case LEBEN -> stats.health();
            case HUNGER -> stats.hunger();
            case RUESTUNG -> stats.armor();
            case KRAFT -> stats.strength();
            case WERTIGKEIT -> cardValue(item, stats);
        };
    }

    private int cardValue(ItemStack item, CardStats stats) {
        String motifId = plugin.getCardService().getMotifId(item);
        LoadedMotif motif = motifId == null ? null : plugin.getMotifRegistry().find(motifId);
        String rarity = motif == null ? null : motif.metadata().rarity();
        double multiplier = switch (rarity == null ? "" : rarity.toLowerCase(java.util.Locale.ROOT)) {
            case "uncommon" -> 1.15D;
            case "rare" -> 1.35D;
            case "epic" -> 1.65D;
            case "legendary" -> 2.00D;
            default -> 1.00D;
        };
        int total = stats.health() + stats.hunger() + stats.armor() + stats.strength();
        return (int) Math.round(total * multiplier * 10);
    }

    private void spawnHolograms(Session session) {
        session.centerStand = spawnStand(session.centerLocation(), "center", null);
        session.slotStands.put(Side.LEFT, spawnStand(session.sideLocation(Side.LEFT), "slot", Side.LEFT));
        session.slotStands.put(Side.RIGHT, spawnStand(session.sideLocation(Side.RIGHT), "slot", Side.RIGHT));
    }

    private ArmorStand spawnStand(Location location, String type, Side side) {
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setInvulnerable(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setCustomNameVisible(true);
            PersistentDataContainer data = stand.getPersistentDataContainer();
            data.set(quartettSessionKey, PersistentDataType.STRING, "");
            data.set(quartettTypeKey, PersistentDataType.STRING, type);
            if (side != null) {
                data.set(quartettSideKey, PersistentDataType.STRING, side.name());
            }
        });
    }

    private void updateHolograms(Session session) {
        stamp(session.centerStand, session.id, "center", null);
        session.centerStand.setCustomName("Quartett: " + session.mode.label);

        updateSideStand(session, Side.LEFT);
        updateSideStand(session, Side.RIGHT);
    }

    private void updateSideStand(Session session, Side side) {
        ArmorStand stand = session.slotStands.get(side);
        stamp(stand, session.id, "slot", side);
        stand.setGlowing(false);
        stand.getEquipment().setHelmet(null);

        UUID playerId = session.players.get(side);
        if (playerId == null) {
            stand.setCustomName("?");
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        String name = player != null ? player.getName() : "Spieler";
        if (session.winner == side) {
            stand.setCustomName("\u00A7a" + name);
        } else if (session.chooser == side) {
            stand.setCustomName("\u00A7e" + name);
            stand.setGlowing(true);
        } else {
            stand.setCustomName("\u00A7f" + name);
        }

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null && player != null) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        stand.getEquipment().setHelmet(head);
    }

    private void stamp(ArmorStand stand, String sessionId, String type, Side side) {
        PersistentDataContainer data = stand.getPersistentDataContainer();
        data.set(quartettSessionKey, PersistentDataType.STRING, sessionId);
        data.set(quartettTypeKey, PersistentDataType.STRING, type);
        if (side != null) {
            data.set(quartettSideKey, PersistentDataType.STRING, side.name());
        } else {
            data.remove(quartettSideKey);
        }
    }

    private void renderInventory(Session session) {
        Inventory inventory = session.inventory();
        if (inventory == null) {
            return;
        }
        inventory.clear();

        fillSeparator(inventory);
        renderHeader(session, Side.LEFT);
        renderHeader(session, Side.RIGHT);
        renderButtons(session, inventory);
        renderCards(session, inventory, Side.LEFT, false, LEFT_OWN_SLOTS);
        renderCards(session, inventory, Side.RIGHT, false, RIGHT_OWN_SLOTS);
        renderCards(session, inventory, Side.LEFT, true, LEFT_NEW_SLOTS);
        renderCards(session, inventory, Side.RIGHT, true, RIGHT_NEW_SLOTS);
    }

    private void fillSeparator(Inventory inventory) {
        ItemStack pane = namedItem(Material.GRAY_STAINED_GLASS_PANE, "\u00A77Trennung");
        for (int slot : new int[] {4, 13, 22, 31, 40, 49}) {
            inventory.setItem(slot, pane);
        }
    }

    private void renderHeader(Session session, Side side) {
        Inventory inventory = session.inventory();
        if (inventory == null) {
            return;
        }

        int[] headerSlots = side == Side.LEFT ? new int[] {0, 1, 2} : new int[] {6, 7, 8};
        int headSlot = side == Side.LEFT ? 3 : 5;
        int nameSlot = side == Side.LEFT ? 12 : 14;
        int pageSlot = side == Side.LEFT ? LEFT_PAGE_SLOT : RIGHT_PAGE_SLOT;
        UUID playerId = session.players.get(side);
        String name = "Fragezeichen";
        ItemStack head = namedItem(Material.PAPER, "?");
        if (playerId != null) {
            Player player = Bukkit.getPlayer(playerId);
            name = player != null ? player.getName() : "Spieler";
            head = playerHead(player, name, session.chooser == side, session.winner == side);
        }

        inventory.setItem(headSlot, head);
        inventory.setItem(nameSlot, namedItem(Material.NAME_TAG, colorForSide(session, side) + name));
        inventory.setItem(headerSlots[0], namedItem(Material.PAPER, "\u00A7fEigene Karten"));
        inventory.setItem(headerSlots[1], namedItem(Material.MAP, "\u00A7bNeue Karten"));
        inventory.setItem(headerSlots[2], namedItem(Material.BOOK, "\u00A77Seite " + (session.pages.get(side) + 1)));
        inventory.setItem(pageSlot, namedItem(Material.PAPER, "\u00A77Seite " + (session.pages.get(side) + 1)));
    }

    private void renderButtons(Session session, Inventory inventory) {
        inventory.setItem(COLLECT_SLOT, button(Material.LIME_DYE, "\u00A7aEinsammeln", session.transferMode == TransferMode.COLLECT));
        inventory.setItem(RETURN_SLOT, button(Material.ORANGE_DYE, "\u00A76Gegnerkarten wiedergeben", session.transferMode == TransferMode.RETURN));
        inventory.setItem(MODE_SLOT, namedItem(Material.COMPASS, "\u00A7eModus: " + session.mode.label));
        inventory.setItem(TURN_SLOT, namedItem(Material.CLOCK, "\u00A7fDran: " + chooserName(session)));
        inventory.setItem(LEFT_PREV_SLOT, namedItem(Material.ARROW, "\u00A77<"));
        inventory.setItem(LEFT_NEXT_SLOT, namedItem(Material.ARROW, "\u00A77>"));
        inventory.setItem(RIGHT_PREV_SLOT, namedItem(Material.ARROW, "\u00A77<"));
        inventory.setItem(RIGHT_NEXT_SLOT, namedItem(Material.ARROW, "\u00A77>"));
    }

    private void renderCards(Session session, Inventory inventory, Side side, boolean newCards, int[] slots) {
        List<ItemStack> source = newCards ? session.newCards.get(side) : session.ownCards.get(side);
        int page = session.pages.get(side);
        int startIndex = page * slots.length;
        for (int i = 0; i < slots.length; i++) {
            int itemIndex = startIndex + i;
            if (itemIndex >= source.size()) {
                continue;
            }
            inventory.setItem(slots[i], source.get(itemIndex).clone());
        }
    }

    private String chooserName(Session session) {
        if (session.chooser == null) {
            return "-";
        }
        UUID chooserId = session.players.get(session.chooser);
        Player player = chooserId == null ? null : Bukkit.getPlayer(chooserId);
        return player != null ? player.getName() : session.chooser.label;
    }

    private ItemStack button(Material material, String name, boolean active) {
        ItemStack item = namedItem(material, name);
        if (!active) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.addEnchant(Enchantment.LURE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerHead(Player player, String name, boolean chooser, boolean winner) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }
        if (player != null) {
            meta.setOwningPlayer(player);
        }
        String color = winner ? "\u00A7a" : chooser ? "\u00A7e" : "\u00A7f";
        meta.setDisplayName(color + name);
        if (chooser) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String colorForSide(Session session, Side side) {
        if (session.winner == side) {
            return "\u00A7a";
        }
        if (session.chooser == side) {
            return "\u00A7e";
        }
        return "\u00A7f";
    }

    private Chest resolveQuartettChest(Block block) {
        if (block == null || block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
            return null;
        }
        if (!(block.getState() instanceof Chest chest)) {
            return null;
        }
        if (!(chest.getInventory().getHolder() instanceof DoubleChest)) {
            return null;
        }
        String customName = chest.getCustomName();
        return "Quartett".equalsIgnoreCase(customName) ? chest : null;
    }

    private BlockFace facingOf(Block block) {
        org.bukkit.block.data.type.Chest chestData = (org.bukkit.block.data.type.Chest) block.getBlockData();
        return chestData.getFacing();
    }

    private String sessionId(Block left, Block right) {
        String a = left.getWorld().getName() + ":" + left.getX() + ":" + left.getY() + ":" + left.getZ();
        String b = right.getWorld().getName() + ":" + right.getX() + ":" + right.getY() + ":" + right.getZ();
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private void consumeOne(Player player, ItemStack item) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        item.setAmount(item.getAmount() - 1);
    }

    public enum Side {
        LEFT("linken"),
        RIGHT("rechten");

        private final String label;

        Side(String label) {
            this.label = label;
        }

        public Side other() {
            return this == LEFT ? RIGHT : LEFT;
        }
    }

    private enum TransferMode {
        NONE,
        COLLECT,
        RETURN
    }

    private enum CompareMode {
        LEBEN("Leben"),
        HUNGER("Hunger"),
        RUESTUNG("Ruestung"),
        KRAFT("Kraft"),
        WERTIGKEIT("Wertigkeit");

        private final String label;

        CompareMode(String label) {
            this.label = label;
        }

        public CompareMode next() {
            CompareMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private record ClickTarget(Side side, boolean newCards, int[] slots, int index) {
    }

    public final class Session {
        private final String id;
        private final Block leftBlock;
        private final Block rightBlock;
        private final BlockFace facing;
        private final Map<Side, UUID> players = new EnumMap<>(Side.class);
        private final Map<Side, ArmorStand> slotStands = new EnumMap<>(Side.class);
        private final Map<Side, ItemFrame> cardFrames = new EnumMap<>(Side.class);
        private final Map<Side, ItemStack> roundCards = new EnumMap<>(Side.class);
        private final Map<Side, List<ItemStack>> ownCards = new EnumMap<>(Side.class);
        private final Map<Side, List<ItemStack>> newCards = new EnumMap<>(Side.class);
        private final Map<Side, Integer> pages = new EnumMap<>(Side.class);
        private ArmorStand centerStand;
        private CompareMode mode = CompareMode.LEBEN;
        private TransferMode transferMode = TransferMode.NONE;
        private Side chooser = Side.LEFT;
        private Side winner;

        private Session(String id, Block leftBlock, Block rightBlock, BlockFace facing) {
            this.id = id;
            this.leftBlock = leftBlock;
            this.rightBlock = rightBlock;
            this.facing = facing;
            ownCards.put(Side.LEFT, new ArrayList<>());
            ownCards.put(Side.RIGHT, new ArrayList<>());
            newCards.put(Side.LEFT, new ArrayList<>());
            newCards.put(Side.RIGHT, new ArrayList<>());
            pages.put(Side.LEFT, 0);
            pages.put(Side.RIGHT, 0);
        }

        private boolean isValid() {
            return centerStand != null && centerStand.isValid();
        }

        private void clearWorldState() {
            clearFrame(this, Side.LEFT);
            clearFrame(this, Side.RIGHT);
            if (centerStand != null) {
                centerStand.remove();
            }
            slotStands.values().forEach(ArmorStand::remove);
            slotStands.clear();
            centerStand = null;
        }

        private Side sideOf(UUID playerId) {
            for (Map.Entry<Side, UUID> entry : players.entrySet()) {
                if (entry.getValue() != null && entry.getValue().equals(playerId)) {
                    return entry.getKey();
                }
            }
            return null;
        }

        private Location centerLocation() {
            double x = (leftBlock.getX() + rightBlock.getX()) / 2.0D + 0.5D;
            double y = leftBlock.getY() + 1.8D;
            double z = (leftBlock.getZ() + rightBlock.getZ()) / 2.0D + 0.5D;
            return new Location(leftBlock.getWorld(), x, y, z);
        }

        private Location sideLocation(Side side) {
            Block block = side == Side.LEFT ? leftBlock : rightBlock;
            return block.getLocation().add(0.5, 1.55, 0.5);
        }

        private Block cardBlock(Side side) {
            Block block = side == Side.LEFT ? leftBlock : rightBlock;
            return block.getRelative(facing);
        }

        private Inventory inventory() {
            if (!(leftBlock.getState() instanceof Chest chest)) {
                return null;
            }
            return chest.getInventory();
        }
    }
}
