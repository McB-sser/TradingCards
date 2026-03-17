package de.mcbesser.tradingcards.command;

import de.mcbesser.tradingcards.TradingCardsPlugin;
import de.mcbesser.tradingcards.image.LoadedMotif;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class TradingCardsCommand implements CommandExecutor, TabCompleter {

    private final TradingCardsPlugin plugin;

    public TradingCardsCommand(TradingCardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tradingcards.admin")) {
            sender.sendMessage(ChatColor.RED + "Du darfst diesen Befehl nicht verwenden.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("reload")) {
            plugin.reloadConfig();
            plugin.reloadMotifs();
            sender.sendMessage(ChatColor.GREEN + "TradingCards-Motive wurden neu geladen.");
            return true;
        }

        if (subCommand.equals("list")) {
            List<String> motifIds = plugin.getMotifRegistry().getMotifIds();
            if (motifIds.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "Keine Motive gefunden. Lege zuerst Bilddateien in den Motivordner.");
                return true;
            }

            sender.sendMessage(ChatColor.GOLD + "Verfuegbare Motive: " + String.join(", ", motifIds));
            return true;
        }

        if (subCommand.equals("give")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Verwendung: /" + label + " give <spieler> <motiv>");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Spieler nicht gefunden: " + args[1]);
                return true;
            }

            LoadedMotif motif = plugin.getMotifRegistry().find(args[2]);
            if (motif == null) {
                sender.sendMessage(ChatColor.RED + "Unbekanntes Motiv: " + args[2]);
                return true;
            }

            ItemStack item = plugin.getCardService().createCardItem(motif);
            target.getInventory().addItem(item);
            sender.sendMessage(ChatColor.GREEN + "Sammelkarte " + motif.id() + " an " + target.getName() + " gegeben.");
            return true;
        }

        sendUsage(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            addIfMatches(completions, args[0], "give");
            addIfMatches(completions, args[0], "list");
            addIfMatches(completions, args[0], "reload");
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                addIfMatches(completions, args[1], player.getName());
            }
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (String motifId : plugin.getMotifRegistry().getMotifIds()) {
                addIfMatches(completions, args[2], motifId);
            }
        }

        return completions;
    }

    private void addIfMatches(List<String> completions, String input, String value) {
        if (value.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT))) {
            completions.add(value);
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "/tradingcards list");
        sender.sendMessage(ChatColor.YELLOW + "/tradingcards reload");
        sender.sendMessage(ChatColor.YELLOW + "/tradingcards give <spieler> <motiv>");
    }
}
