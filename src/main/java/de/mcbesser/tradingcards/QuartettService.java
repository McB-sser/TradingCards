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
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class QuartettService {

    private static final int LEFT_SELECTOR_SLOT = 0;
    private static final int LEFT_HINT_SLOT = 1;
    private static final int LEFT_STATE_SLOT = 2;
    private static final int INFO_SLOT = 4;
    private static final int RESET_SLOT = 13;
    private static final int RIGHT_STATE_SLOT = 6;
    private static final int RIGHT_HINT_SLOT = 7;
    private static final int RIGHT_SELECTOR_SLOT = 8;
    private static final int STATUS_SLOT = 45;
    private static final int MODE_SLOT = 22;
    private static final int TURN_SLOT = 49;
    private static final int COLLECT_SLOT = 40;
    private static final int RETURN_SLOT = 49;
    private static final int LEFT_PREV_SLOT = 45;
    private static final int LEFT_PAGE_SLOT = 46;
    private static final int LEFT_NEXT_SLOT = 47;
    private static final int RIGHT_PREV_SLOT = 51;
    private static final int RIGHT_PAGE_SLOT = 52;
    private static final int RIGHT_NEXT_SLOT = 53;
    private static final int[] LEFT_OWN_SLOTS = {9, 10, 11, 18, 19, 20};
    private static final int[] RIGHT_OWN_SLOTS = {15, 16, 17, 24, 25, 26};
    private static final int[] LEFT_NEW_SLOTS = {27, 28, 29, 36, 37, 38};
    private static final int[] RIGHT_NEW_SLOTS = {33, 34, 35, 42, 43, 44};

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
        if (chest == null || !(chest.getInventory().getHolder() instanceof DoubleChest doubleChest)) {
            return null;
        }

        Chest leftChest = (Chest) doubleChest.getLeftSide();
        Chest rightChest = (Chest) doubleChest.getRightSide();
        String sessionId = sessionId(leftChest.getBlock(), rightChest.getBlock());
        Session session = sessions.get(sessionId);
        if (session == null) {
            session = new Session(sessionId, leftChest.getBlock(), rightChest.getBlock(), facingOf(leftChest.getBlock()));
            sessions.put(sessionId, session);
            spawnDisplay(session);
        }
        if (!session.isValid()) {
            session.clearWorldState();
            spawnDisplay(session);
        }
        render(session);
        return session;
    }

    public Session ensureSession(Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof DoubleChest doubleChest)) {
            return null;
        }
        return ensureSession(((Chest) doubleChest.getLeftSide()).getBlock());
    }

    public void ensureSessionsInChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        clearQuartettEntitiesInChunk(chunk);
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Chest chest) {
                ensureSession(chest.getBlock());
            }
        }
    }

    public void ensureSessionsInLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                ensureSessionsInChunk(chunk);
            }
        }
    }

    public void clearQuartettEntitiesInChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        for (Entity entity : chunk.getEntities()) {
            if (isQuartettEntity(entity)) {
                entity.remove();
            }
        }
    }

    public boolean isQuartettChest(Block block) {
        return resolveQuartettChest(block) != null;
    }

    public boolean isQuartettEntity(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(quartettSessionKey, PersistentDataType.STRING);
    }

    public void handleHeadInteract(Player player, Entity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String sessionId = data.get(quartettSessionKey, PersistentDataType.STRING);
        String type = data.get(quartettTypeKey, PersistentDataType.STRING);
        String rawSide = data.get(quartettSideKey, PersistentDataType.STRING);
        if (sessionId == null || type == null) {
            return;
        }

        Session session = sessions.get(sessionId);
        if (session == null || !session.isValid()) {
            return;
        }

        if ("mode".equals(type)) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (plugin.getCardService().isTradingCardItem(held)) {
                Side side = session.sideOf(player.getUniqueId());
                if (side == null) {
                    side = assignFreeSide(session, player);
                    if (side == null) {
                        player.sendMessage("Es ist keine freie Quartett-Seite mehr verf\u00fcgbar.");
                        return;
                    }
                }
                if (session.roundFrames.containsKey(side)) {
                    player.sendMessage("Auf deiner Seite liegt bereits eine Karte.");
                    return;
                }
                placeRoundCard(session, side, player, held);
                return;
            }
            if (session.sideOf(player.getUniqueId()) == null) {
                Side side = assignFreeSide(session, player);
                if (side != null) {
                    player.sendMessage("Du spielst jetzt auf der " + side.label + " Seite.");
                    return;
                }
            }
            if (session.currentTurn != null) {
                UUID currentPlayer = session.players.get(session.currentTurn);
                if (currentPlayer != null && !currentPlayer.equals(player.getUniqueId())) {
                    player.sendMessage("Gerade ist der andere Spieler mit der Attributwahl dran.");
                    return;
                }
            }
            if (session.roundResolved) {
                finalizeRound(session);
                player.sendMessage("Die Runde wurde abger\u00e4umt.");
                return;
            }
            cycleMode(session);
            player.sendMessage("Quartett-Attribut: " + modeLabel(session));
            return;
        }
        if (!"head".equals(type) || rawSide == null) {
            return;
        }

        Side side = Side.valueOf(rawSide);
        UUID assigned = session.players.get(side);
        if (assigned == null || !assigned.equals(player.getUniqueId())) {
            player.sendMessage("Das ist nicht deine Quartett-Seite.");
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!plugin.getCardService().isTradingCardItem(held)) {
            player.sendMessage("Halte eine TradingCard in der Hand, um sie zu hinterlegen.");
            return;
        }
        if (session.roundFrames.containsKey(side)) {
            player.sendMessage("Auf deiner Seite liegt bereits eine Karte.");
            return;
        }

        placeRoundCard(session, side, player, held);
    }

    public void handleInventoryClick(Player player, Inventory inventory, int rawSlot) {
        Session session = ensureSession(inventory);
        if (session == null) {
            return;
        }

        if (rawSlot == LEFT_SELECTOR_SLOT) {
            selectSide(session, Side.LEFT, player);
            return;
        }
        if (rawSlot == RIGHT_SELECTOR_SLOT) {
            selectSide(session, Side.RIGHT, player);
            return;
        }
        if (rawSlot == MODE_SLOT) {
            if (session.currentTurn != null) {
                UUID currentPlayer = session.players.get(session.currentTurn);
                if (currentPlayer != null && !currentPlayer.equals(player.getUniqueId())) {
                    player.sendMessage("Gerade ist der andere Spieler mit der Attributwahl dran.");
                    return;
                }
            }
            if (session.roundResolved) {
                finalizeRound(session);
                player.sendMessage("Die Runde wurde abger\u00e4umt.");
                return;
            }
            cycleMode(session);
            player.sendMessage("Quartett-Attribut: " + modeLabel(session));
            return;
        }
        if (rawSlot == RESET_SLOT) {
            resetSession(session, true);
            render(session);
            player.sendMessage("Quartett wurde zur\u00fcckgesetzt. Spieler wurden abgew\u00e4hlt und Karten zur\u00fcckgegeben.");
            return;
        }
        if (rawSlot == COLLECT_SLOT) {
            session.transferMode = TransferMode.COLLECT;
            payoutStoredCards(session);
            render(session);
            player.sendMessage("Alle Karten wurden verteilt. Gewonnene Karten bleiben beim Gewinner.");
            return;
        }
        if (rawSlot == RETURN_SLOT) {
            session.transferMode = TransferMode.RETURN;
            payoutStoredCards(session);
            render(session);
            player.sendMessage("Alle Karten wurden an ihre Besitzer zur\u00fcckgegeben.");
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

        ClickTarget target = resolveClickTarget(rawSlot);
        if (target == null) {
            return;
        }
        List<ItemStack> source = target.newCards ? session.newCards.get(target.side) : session.ownCards.get(target.side);
        int page = session.pages.get(target.side);
        int index = page * target.slots.length + target.index;
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
        normalizePages(session);
        render(session);
    }

    public boolean depositFromInventory(Player player, Inventory inventory, ItemStack item) {
        Session session = ensureSession(inventory);
        if (session == null || !plugin.getCardService().isTradingCardItem(item)) {
            return false;
        }
        Side side = session.sideOf(player.getUniqueId());
        if (side == null) {
            player.sendMessage("W\u00e4hle zuerst eine Seite in der Quartett-Kiste.");
            return true;
        }
        if (session.roundFrames.containsKey(side)) {
            player.sendMessage("Auf deiner Seite liegt bereits eine Karte.");
            return true;
        }
        placeRoundCard(session, side, player, item);
        return true;
    }

    public boolean isQuartettRoundFrame(ItemFrame frame) {
        if (frame == null) {
            return false;
        }
        for (Session session : sessions.values()) {
            if (session.roundFrames.containsValue(frame)) {
                return true;
            }
        }
        return false;
    }

    public boolean canRevealQuartettFrame(Player player, ItemFrame frame) {
        if (frame == null) {
            return true;
        }
        for (Session session : sessions.values()) {
            for (Map.Entry<Side, ItemFrame> entry : session.roundFrames.entrySet()) {
                if (!frame.equals(entry.getValue())) {
                    continue;
                }
                Side side = entry.getKey();
                UUID owner = session.players.get(side);
                if (owner != null && !owner.equals(player.getUniqueId())) {
                    player.sendMessage("Nur der Besitzer kann diese Karte aufdecken.");
                    return false;
                }
                if (session.mode == null) {
                    player.sendMessage("W\u00e4hle zuerst in der Mitte ein Attribut.");
                    return false;
                }
                ItemFrame leftFrame = session.roundFrames.get(Side.LEFT);
                ItemFrame rightFrame = session.roundFrames.get(Side.RIGHT);
                boolean leftHidden = leftFrame != null && plugin.getCardService().isHidden(leftFrame.getItem());
                boolean rightHidden = rightFrame != null && plugin.getCardService().isHidden(rightFrame.getItem());
                if (leftHidden && rightHidden && session.currentTurn != null && session.currentTurn != side) {
                    player.sendMessage("Der Spieler mit dem Pfeil muss zuerst aufdecken.");
                    return false;
                }
                return true;
            }
        }
        return true;
    }

    public void refreshRoundState(ItemFrame frame) {
        if (frame == null) {
            return;
        }
        for (Session session : sessions.values()) {
            if (session.roundFrames.containsValue(frame)) {
                updateWinner(session);
                render(session);
                return;
            }
        }
    }

    public void cleanupSession(Block block, boolean dropCards) {
        Session session = sessionForBlock(block);
        if (session == null) {
            return;
        }
        if (dropCards) {
            Location location = block.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack item : session.allStoredCards()) {
                location.getWorld().dropItemNaturally(location, item);
            }
        }
        Inventory inventory = session.inventory();
        if (inventory != null) {
            inventory.clear();
        }
        session.clearWorldState();
        sessions.remove(session.id);
    }

    public void shutdown() {
        List<Session> copy = new ArrayList<>(sessions.values());
        for (Session session : copy) {
            Inventory inventory = session.inventory();
            if (inventory != null) {
                inventory.clear();
            }
            session.clearWorldState();
        }
        sessions.clear();
    }

    private void placeRoundCard(Session session, Side side, Player player, ItemStack held) {
        String motifId = plugin.getCardService().getMotifId(held);
        LoadedMotif motif = motifId == null ? null : plugin.getMotifRegistry().find(motifId);
        if (motif == null) {
            player.sendMessage("Diese Karte hat kein g\u00fcltiges Motiv.");
            return;
        }

        CardStats stats = plugin.getCardService().getStats(held);
        String displayId = UUID.randomUUID().toString();
        ItemStack displayCard = plugin.getCardService().createPlacedDisplayItems(motif, displayId, 1, true, stats).get(0);
        plugin.getCardService().setOwner(displayCard, player.getUniqueId());

        ItemStack storedCard = plugin.getCardService().createCardItem(motif, stats);
        plugin.getCardService().setOwner(storedCard, player.getUniqueId());

        Block cardBlock = session.cardBlock(side);
        if (cardBlock.getType() != Material.AIR) {
            player.sendMessage("Vor deiner Seite ist kein Platz f\u00fcr die Karte.");
            return;
        }

        ItemFrame frame = cardBlock.getWorld().spawn(cardBlock.getLocation(), ItemFrame.class, spawned -> {
            spawned.setFacingDirection(session.facing, true);
            spawned.setVisible(false);
            spawned.setItem(displayCard, false);
            PersistentDataContainer data = spawned.getPersistentDataContainer();
            data.set(plugin.getCardService().getDisplayIdKey(), PersistentDataType.STRING, displayId);
            data.set(plugin.getCardService().getPanelIndexKey(), PersistentDataType.INTEGER, 0);
            data.set(plugin.getCardService().getMotifIdKey(), PersistentDataType.STRING, motif.id());
        });

        session.roundFrames.put(side, frame);
        session.roundCards.put(side, storedCard.clone());
        session.ownCards.get(side).add(storedCard.clone());
        consumeOne(player, held);
        updateWinner(session);
        render(session);
        player.sendMessage("Deine Karte wurde vor der Kiste hinterlegt.");
    }

    private void selectSide(Session session, Side side, Player player) {
        UUID current = session.players.get(side);
        if (current != null && !current.equals(player.getUniqueId())) {
            player.sendMessage("Diese Seite ist bereits belegt.");
            return;
        }
        Side already = session.sideOf(player.getUniqueId());
        if (already != null && already != side) {
            player.sendMessage("Du hast bereits die andere Seite gew\u00e4hlt.");
            return;
        }
        session.players.put(side, player.getUniqueId());
        if (session.currentTurn == null && session.players.get(Side.LEFT) != null && session.players.get(Side.RIGHT) != null) {
            session.currentTurn = Side.LEFT;
        }
        render(session);
        player.sendMessage("Du spielst jetzt auf der " + side.label + " Seite.");
    }

    private Side assignFreeSide(Session session, Player player) {
        if (session.sideOf(player.getUniqueId()) != null) {
            return session.sideOf(player.getUniqueId());
        }
        if (session.players.get(Side.LEFT) == null) {
            selectSide(session, Side.LEFT, player);
            return Side.LEFT;
        }
        if (session.players.get(Side.RIGHT) == null) {
            selectSide(session, Side.RIGHT, player);
            return Side.RIGHT;
        }
        return null;
    }

    private void cycleMode(Session session) {
        session.mode = session.mode == null ? CompareMode.LEBEN : session.mode.next();
        updateWinner(session);
        render(session);
    }

    private void updateWinner(Session session) {
        ItemFrame left = session.roundFrames.get(Side.LEFT);
        ItemFrame right = session.roundFrames.get(Side.RIGHT);
        if (left == null || right == null) {
            session.winner = null;
            session.roundResolved = false;
            return;
        }
        if (plugin.getCardService().isHidden(left.getItem()) || plugin.getCardService().isHidden(right.getItem())) {
            session.winner = null;
            session.roundResolved = false;
            return;
        }
        if (session.mode == null) {
            session.winner = null;
            session.roundResolved = false;
            return;
        }

        int leftValue = attributeValue(session.mode, left.getItem());
        int rightValue = attributeValue(session.mode, right.getItem());
        session.roundResolved = true;
        if (leftValue == rightValue) {
            session.winner = null;
            return;
        }
        session.winner = leftValue > rightValue ? Side.LEFT : Side.RIGHT;
    }

    private void finalizeRound(Session session) {
        Side roundWinner = session.winner;
        ItemStack left = session.roundCards.remove(Side.LEFT);
        ItemStack right = session.roundCards.remove(Side.RIGHT);
        clearRoundFrame(session, Side.LEFT);
        clearRoundFrame(session, Side.RIGHT);
        if (left == null || right == null) {
            session.mode = null;
            session.winner = null;
            session.roundResolved = false;
            render(session);
            return;
        }

        if (roundWinner == Side.LEFT) {
            removeLastOwned(session.ownCards.get(Side.RIGHT), plugin.getCardService().getOwner(right));
            session.newCards.get(Side.LEFT).add(right.clone());
            session.currentTurn = roundWinner;
        } else if (roundWinner == Side.RIGHT) {
            removeLastOwned(session.ownCards.get(Side.LEFT), plugin.getCardService().getOwner(left));
            session.newCards.get(Side.RIGHT).add(left.clone());
            session.currentTurn = roundWinner;
        }
        session.mode = null;
        session.winner = null;
        session.roundResolved = false;
        render(session);
    }

    private void clearRoundFrame(Session session, Side side) {
        ItemFrame frame = session.roundFrames.remove(side);
        if (frame == null) {
            return;
        }
        frame.setItem(null, false);
        frame.remove();
    }

    private void payoutStoredCards(Session session) {
        Location payoutLocation = session.modeLocation();
        for (Side side : Side.values()) {
            UUID playerId = session.players.get(side);
            for (ItemStack item : drainCards(session.ownCards.get(side))) {
                giveCardToPlayer(item, playerId, payoutLocation);
            }
        }
        for (Side side : Side.values()) {
            Side recipientSide = session.transferMode == TransferMode.COLLECT ? side : opposite(side);
            UUID playerId = session.players.get(recipientSide);
            for (ItemStack item : drainCards(session.newCards.get(side))) {
                giveCardToPlayer(item, playerId, payoutLocation);
            }
        }
        for (ItemStack item : new ArrayList<>(session.roundCards.values())) {
            giveCardToOwner(item, payoutLocation);
        }

        clearRoundFrame(session, Side.LEFT);
        clearRoundFrame(session, Side.RIGHT);
        session.roundCards.clear();
        session.mode = null;
        session.winner = null;
        session.roundResolved = false;
        session.transferMode = TransferMode.NONE;
        session.currentTurn = session.players.get(Side.LEFT) != null && session.players.get(Side.RIGHT) != null ? Side.LEFT : null;
        normalizePages(session);
    }

    private List<ItemStack> drainCards(List<ItemStack> cards) {
        List<ItemStack> items = new ArrayList<>();
        cards.forEach(card -> items.add(card.clone()));
        cards.clear();
        return items;
    }

    private void giveCardToPlayer(ItemStack item, UUID playerId, Location fallbackLocation) {
        Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
        if (player != null) {
            player.getInventory().addItem(item.clone()).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            return;
        }
        giveCardToOwner(item, fallbackLocation);
    }

    private void giveCardToOwner(ItemStack item, Location fallbackLocation) {
        UUID ownerId = plugin.getCardService().getOwner(item);
        Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
        if (owner != null) {
            owner.getInventory().addItem(item.clone()).values()
                .forEach(leftover -> owner.getWorld().dropItemNaturally(owner.getLocation(), leftover));
            return;
        }
        if (fallbackLocation.getWorld() != null) {
            fallbackLocation.getWorld().dropItemNaturally(fallbackLocation, item.clone());
        }
    }

    private Side opposite(Side side) {
        return side == Side.LEFT ? Side.RIGHT : Side.LEFT;
    }

    private void removeLastOwned(List<ItemStack> cards, UUID ownerId) {
        for (int i = cards.size() - 1; i >= 0; i--) {
            UUID owner = plugin.getCardService().getOwner(cards.get(i));
            if (ownerId != null && ownerId.equals(owner)) {
                cards.remove(i);
                return;
            }
        }
    }

    private int attributeValue(CompareMode mode, ItemStack item) {
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

    private void render(Session session) {
        renderInventory(session);
        renderDisplay(session);
    }

    private void spawnDisplay(Session session) {
        session.modeStand = spawnTextStand(session.id, session.modeLocation(), "mode", null);
        session.modeInteraction = spawnInteraction(session.id, session.modeLocation().clone().add(0.0, -0.15, 0.0), "mode", null);

        ArmorStand leftHead = spawnHeadStand(session.id, session.headLocation(Side.LEFT), Side.LEFT);
        leftHead.setRotation(yawForFacing(session.facing), 0.0F);
        session.headStands.put(Side.LEFT, leftHead);
        ArmorStand rightHead = spawnHeadStand(session.id, session.headLocation(Side.RIGHT), Side.RIGHT);
        rightHead.setRotation(yawForFacing(session.facing), 0.0F);
        session.headStands.put(Side.RIGHT, rightHead);

        session.headInteractions.put(Side.LEFT, spawnInteraction(session.id, session.headClickLocation(Side.LEFT), "head", Side.LEFT));
        session.headInteractions.put(Side.RIGHT, spawnInteraction(session.id, session.headClickLocation(Side.RIGHT), "head", Side.RIGHT));
    }

    private void renderDisplay(Session session) {
        session.modeStand.setCustomName(session.roundResolved ? "Klick mich" : modeLabel(session));
        session.modeStand.setCustomNameVisible(true);
        for (Side side : Side.values()) {
            ArmorStand stand = session.headStands.get(side);
            if (stand == null) {
                continue;
            }
            stand.getEquipment().setHelmet(null);
            Side markerSide = session.roundResolved ? session.winner : session.currentTurn;
            String marker = session.roundResolved ? "\u00A76\u2191" : "\u2191";
            stand.setCustomName(markerSide == side ? marker : null);
            stand.setCustomNameVisible(markerSide == side);
            UUID playerId = session.players.get(side);
            if (playerId == null) {
                stand.getEquipment().setHelmet(new ItemStack(side == Side.LEFT ? Material.SKELETON_SKULL : Material.WITHER_SKELETON_SKULL));
                continue;
            }

            Player player = Bukkit.getPlayer(playerId);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                if (player != null) {
                    meta.setOwningPlayer(player);
                }
                if (session.winner == side) {
                    meta.addEnchant(Enchantment.LURE, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                head.setItemMeta(meta);
            }
            stand.getEquipment().setHelmet(head);
        }
    }

    private ArmorStand spawnTextStand(String sessionId, Location location, String type, Side side) {
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setInvulnerable(true);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setCustomNameVisible(true);
            stamp(stand, sessionId, type, side);
        });
    }

    private ArmorStand spawnHeadStand(String sessionId, Location location, Side side) {
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setInvulnerable(true);
            stand.setMarker(true);
            stand.setSmall(false);
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setCustomNameVisible(false);
            stamp(stand, sessionId, "head", side);
        });
    }

    private Interaction spawnInteraction(String sessionId, Location location, String type, Side side) {
        return location.getWorld().spawn(location, Interaction.class, interaction -> {
            interaction.setResponsive(true);
            interaction.setInteractionWidth(0.35F);
            interaction.setInteractionHeight(0.35F);
            stamp(interaction, sessionId, type, side);
        });
    }

    private void stamp(Entity entity, String sessionId, String type, Side side) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
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

        fillSeparators(inventory);
        inventory.setItem(INFO_SLOT, updatedInfoBook());
        inventory.setItem(RESET_SLOT, namedItem(Material.BARRIER, "\u00A7cReset"));
        inventory.setItem(LEFT_SELECTOR_SLOT, selectorItem(session, Side.LEFT));
        inventory.setItem(RIGHT_SELECTOR_SLOT, selectorItem(session, Side.RIGHT));
        inventory.setItem(MODE_SLOT, namedItem(Material.COMPASS, "\u00A7e" + (session.roundResolved ? "Klick mich: Runde abr\u00e4umen" : "Attribut: " + modeLabel(session))));
        inventory.setItem(COLLECT_SLOT, activeButton(Material.LIME_DYE, "\u00A7aGewonnene Karten behalten", session.transferMode == TransferMode.COLLECT));
        inventory.setItem(RETURN_SLOT, activeButton(Material.ORANGE_DYE, "\u00A76Gewonnene Karten zur\u00fcckgeben", session.transferMode == TransferMode.RETURN));
        inventory.setItem(LEFT_PREV_SLOT, namedItem(Material.ARROW, "\u00A77<"));
        inventory.setItem(LEFT_PAGE_SLOT, namedItem(Material.PAPER, "\u00A77Seite " + (session.pages.get(Side.LEFT) + 1)));
        inventory.setItem(LEFT_NEXT_SLOT, namedItem(Material.ARROW, "\u00A77>"));
        inventory.setItem(RIGHT_PREV_SLOT, namedItem(Material.ARROW, "\u00A77<"));
        inventory.setItem(RIGHT_PAGE_SLOT, namedItem(Material.PAPER, "\u00A77Seite " + (session.pages.get(Side.RIGHT) + 1)));
        inventory.setItem(RIGHT_NEXT_SLOT, namedItem(Material.ARROW, "\u00A77>"));

        renderCardArea(session, inventory, Side.LEFT, false, LEFT_OWN_SLOTS);
        renderCardArea(session, inventory, Side.RIGHT, false, RIGHT_OWN_SLOTS);
        renderCardArea(session, inventory, Side.LEFT, true, LEFT_NEW_SLOTS);
        renderCardArea(session, inventory, Side.RIGHT, true, RIGHT_NEW_SLOTS);
    }

    private void fillSeparators(Inventory inventory) {
        ItemStack pane = namedItem(Material.GRAY_STAINED_GLASS_PANE, "\u00A77Trennung");
        for (int slot : new int[] {1, 2, 3, 5, 6, 7, 31, 48, 50}) {
            inventory.setItem(slot, pane);
        }
    }

    private ItemStack selectorItem(Session session, Side side) {
        UUID playerId = session.players.get(side);
        if (playerId == null) {
            return namedItem(side == Side.LEFT ? Material.SKELETON_SKULL : Material.WITHER_SKELETON_SKULL,
                side == Side.LEFT ? "\u00A7fLinke Seite" : "\u00A7fRechte Seite");
        }

        Player player = Bukkit.getPlayer(playerId);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            if (player != null) {
                meta.setOwningPlayer(player);
            }
            meta.setDisplayName("\u00A7e" + playerText(session, side));
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack infoBook() {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00A7eQuartett-Regeln");
            meta.setLore(List.of(
                "\u00A771. In der Kiste linke/rechte Seite w\u00e4hlen.",
                "\u00A772. Mit TradingCard auf den Kopf \u00fcber der Kiste klicken.",
                "\u00A773. Mitte klicken und Attribut w\u00e4hlen.",
                "\u00A774. Beide Spieler decken ihre Karte selbst auf.",
                "\u00A775. Gewinnerpfeil erscheint, Mitte klickt zum Abr\u00e4umen.",
                "\u00A776. Gewonnene Karten behalten oder zur\u00fcckgeben."
            ));
            book.setItemMeta(meta);
        }
        return book;
    }

    private ItemStack activeButton(Material material, String name, boolean active) {
        ItemStack item = namedItem(material, name);
        if (!active) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack updatedInfoBook() {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00A7eQuartett-Regeln");
            meta.setLore(List.of(
                "\u00A771. In der Kiste linke/rechte Seite w\u00e4hlen.",
                "\u00A772. Karte auf deiner Seite ablegen und in der Mitte auf Klick mich klicken.",
                "\u00A773. In der Mitte das Attribut w\u00e4hlen.",
                "\u00A774. Beide Spieler decken ihre Karte selbst auf.",
                "\u00A775. Der orange Pfeil zeigt den Gewinner, dann wieder Mitte klicken.",
                "\u00A776. Unten Behalten oder Zur\u00fcckgeben dr\u00fccken."
            ));
            book.setItemMeta(meta);
        }
        return book;
    }

    private void renderCardArea(Session session, Inventory inventory, Side side, boolean newCards, int[] slots) {
        List<ItemStack> source = newCards ? session.newCards.get(side) : session.ownCards.get(side);
        int page = session.pages.get(side);
        int start = page * slots.length;
        for (int i = 0; i < slots.length; i++) {
            int sourceIndex = start + i;
            if (sourceIndex < source.size()) {
                inventory.setItem(slots[i], source.get(sourceIndex).clone());
            }
        }
    }

    private String winnerText(Session session) {
        if (session.winner == null) {
            return modeLabel(session);
        }
        UUID winnerId = session.players.get(session.winner);
        Player winner = winnerId == null ? null : Bukkit.getPlayer(winnerId);
        return winner != null ? winner.getName() : session.winner.label;
    }

    private String modeLabel(Session session) {
        return session.mode == null ? "Klick mich" : session.mode.label;
    }

    private String transferText(TransferMode mode) {
        return switch (mode) {
            case COLLECT -> "Behalten";
            case RETURN -> "Zur\u00fcckgeben";
            case NONE -> "Noch nicht gew\u00e4hlt";
        };
    }

    private String playerText(Session session, Side side) {
        UUID playerId = session.players.get(side);
        if (playerId == null) {
            return "frei";
        }
        Player player = Bukkit.getPlayer(playerId);
        return player != null ? player.getName() : "belegt";
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

    private ClickTarget resolveClickTarget(int rawSlot) {
        ClickTarget target = match(rawSlot, Side.LEFT, false, LEFT_OWN_SLOTS);
        if (target != null) {
            return target;
        }
        target = match(rawSlot, Side.RIGHT, false, RIGHT_OWN_SLOTS);
        if (target != null) {
            return target;
        }
        target = match(rawSlot, Side.LEFT, true, LEFT_NEW_SLOTS);
        if (target != null) {
            return target;
        }
        return match(rawSlot, Side.RIGHT, true, RIGHT_NEW_SLOTS);
    }

    private ClickTarget match(int rawSlot, Side side, boolean newCards, int[] slots) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == rawSlot) {
                return new ClickTarget(side, newCards, slots, i);
            }
        }
        return null;
    }

    private boolean mayTake(Player player, Session session, Side side, ItemStack item) {
        if (session.transferMode == TransferMode.NONE) {
            return false;
        }
        if (session.transferMode == TransferMode.COLLECT) {
            return session.sideOf(player.getUniqueId()) == side;
        }
        UUID owner = plugin.getCardService().getOwner(item);
        return owner != null && owner.equals(player.getUniqueId());
    }

    private void changePage(Session session, Side side, int delta) {
        int nextPage = session.pages.get(side) + delta;
        int maxPage = Math.max(maxPage(session.ownCards.get(side), side == Side.LEFT ? LEFT_OWN_SLOTS.length : RIGHT_OWN_SLOTS.length),
            maxPage(session.newCards.get(side), side == Side.LEFT ? LEFT_NEW_SLOTS.length : RIGHT_NEW_SLOTS.length));
        session.pages.put(side, Math.max(0, Math.min(maxPage, nextPage)));
        render(session);
    }

    private void normalizePages(Session session) {
        for (Side side : Side.values()) {
            int maxPage = Math.max(maxPage(session.ownCards.get(side), side == Side.LEFT ? LEFT_OWN_SLOTS.length : RIGHT_OWN_SLOTS.length),
                maxPage(session.newCards.get(side), side == Side.LEFT ? LEFT_NEW_SLOTS.length : RIGHT_NEW_SLOTS.length));
            session.pages.put(side, Math.max(0, Math.min(session.pages.get(side), maxPage)));
        }
    }

    private int maxPage(List<ItemStack> items, int pageSize) {
        return Math.max(0, (items.size() - 1) / pageSize);
    }

    private void resetParticipants(Session session) {
        resetSession(session, false);
    }

    private void resetSession(Session session, boolean returnCardsToOwners) {
        if (returnCardsToOwners) {
            returnCardsToOwners(session);
        } else {
            clearRoundFrame(session, Side.LEFT);
            clearRoundFrame(session, Side.RIGHT);
            session.roundCards.clear();
            clearStoredCards(session);
        }
        session.players.clear();
        session.currentTurn = null;
        session.mode = null;
        session.winner = null;
        session.roundResolved = false;
        session.transferMode = TransferMode.NONE;
        normalizePages(session);
    }

    private void returnCardsToOwners(Session session) {
        Location payoutLocation = session.modeLocation();
        for (Side side : Side.values()) {
            for (ItemStack item : drainCards(session.ownCards.get(side))) {
                giveCardToOwner(item, payoutLocation);
            }
        }
        for (Side side : Side.values()) {
            for (ItemStack item : drainCards(session.newCards.get(side))) {
                giveCardToOwner(item, payoutLocation);
            }
        }
        for (ItemStack item : new ArrayList<>(session.roundCards.values())) {
            giveCardToOwner(item, payoutLocation);
        }
        clearRoundFrame(session, Side.LEFT);
        clearRoundFrame(session, Side.RIGHT);
        session.roundCards.clear();
        clearStoredCards(session);
    }

    private void clearStoredCards(Session session) {
        session.ownCards.values().forEach(List::clear);
        session.newCards.values().forEach(List::clear);
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
        return "Quartett".equalsIgnoreCase(chest.getCustomName()) ? chest : null;
    }

    private BlockFace facingOf(Block block) {
        org.bukkit.block.data.type.Chest chestData = (org.bukkit.block.data.type.Chest) block.getBlockData();
        return chestData.getFacing();
    }

    private float yawForFacing(BlockFace facing) {
        return switch (facing) {
            case NORTH -> 180.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 0.0F;
        };
    }

    private String sessionId(Block left, Block right) {
        String a = left.getWorld().getName() + ":" + left.getX() + ":" + left.getY() + ":" + left.getZ();
        String b = right.getWorld().getName() + ":" + right.getX() + ":" + right.getY() + ":" + right.getZ();
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private Session sessionForBlock(Block block) {
        if (block == null) {
            return null;
        }
        for (Session session : sessions.values()) {
            if (session.leftBlock.equals(block) || session.rightBlock.equals(block)) {
                return session;
            }
        }
        return null;
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
    }

    private enum TransferMode {
        NONE,
        COLLECT,
        RETURN
    }

    private enum CompareMode {
        LEBEN("Leben"),
        HUNGER("Hunger"),
        RUESTUNG("R\u00fcstung"),
        KRAFT("Kraft"),
        WERTIGKEIT("Wertigkeit");

        private final String label;

        CompareMode(String label) {
            this.label = label;
        }

        private CompareMode next() {
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
        private final Map<Side, ArmorStand> headStands = new EnumMap<>(Side.class);
        private final Map<Side, Interaction> headInteractions = new EnumMap<>(Side.class);
        private final Map<Side, ItemFrame> roundFrames = new EnumMap<>(Side.class);
        private final Map<Side, ItemStack> roundCards = new EnumMap<>(Side.class);
        private final Map<Side, List<ItemStack>> ownCards = new EnumMap<>(Side.class);
        private final Map<Side, List<ItemStack>> newCards = new EnumMap<>(Side.class);
        private final Map<Side, Integer> pages = new EnumMap<>(Side.class);
        private ArmorStand modeStand;
        private Interaction modeInteraction;
        private Side currentTurn;
        private CompareMode mode;
        private TransferMode transferMode = TransferMode.NONE;
        private Side winner;
        private boolean roundResolved;

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
            return modeStand != null && modeStand.isValid();
        }

        private void clearWorldState() {
            clearRoundFrame(this, Side.LEFT);
            clearRoundFrame(this, Side.RIGHT);
            roundCards.clear();
            if (modeStand != null) {
                modeStand.remove();
            }
            if (modeInteraction != null) {
                modeInteraction.remove();
            }
            headStands.values().forEach(ArmorStand::remove);
            headInteractions.values().forEach(Interaction::remove);
            headStands.clear();
            headInteractions.clear();
            modeStand = null;
            modeInteraction = null;
        }

        private Side sideOf(UUID playerId) {
            for (Map.Entry<Side, UUID> entry : players.entrySet()) {
                if (entry.getValue() != null && entry.getValue().equals(playerId)) {
                    return entry.getKey();
                }
            }
            return null;
        }

        private Location modeLocation() {
            double x = (leftBlock.getX() + rightBlock.getX()) / 2.0D + 0.5D;
            double z = (leftBlock.getZ() + rightBlock.getZ()) / 2.0D + 0.5D;
            return new Location(leftBlock.getWorld(), x, leftBlock.getY() + 1.15D, z);
        }

        private Location headLocation(Side side) {
            Block block = side == Side.LEFT ? leftBlock : rightBlock;
            return block.getLocation().add(0.5, 0.72, 0.5);
        }

        private Location headClickLocation(Side side) {
            return headLocation(side).clone().add(0.0, 0.05, 0.0);
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

        private List<ItemStack> allStoredCards() {
            List<ItemStack> cards = new ArrayList<>();
            ownCards.values().forEach(list -> list.forEach(item -> cards.add(item.clone())));
            newCards.values().forEach(list -> list.forEach(item -> cards.add(item.clone())));
            roundCards.values().forEach(item -> cards.add(item.clone()));
            return cards;
        }
    }
}
