package de.mcbesser.tradingcards.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public final class MetadataMapRenderer extends MapRenderer {

    private static final int MAP_SIZE = 128;

    private final BufferedImage image;
    private boolean rendered;

    public MetadataMapRenderer(LoadedMotif motif) {
        super(true);
        this.image = createCardImage(motif);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) {
            return;
        }
        canvas.drawImage(0, 0, image);
        rendered = true;
    }

    public static BufferedImage createCardImage(LoadedMotif motif) {
        BufferedImage card = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = card.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        TradingCardMetadata metadata = motif.metadata();
        graphics.setColor(new Color(42, 42, 46));
        graphics.fillRect(0, 0, MAP_SIZE, MAP_SIZE);
        graphics.setColor(new Color(70, 70, 78));
        graphics.fillRect(10, 30, MAP_SIZE - 20, 1);
        graphics.fillRect(10, 52, MAP_SIZE - 20, 1);
        graphics.fillRect(10, 112, MAP_SIZE - 20, 1);

        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        drawCenteredLines(graphics, limitLines(wrapText(displayTitle(motif, metadata), 13), 2), 18, 12);

        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        List<String> header = new ArrayList<>();
        if (metadata.number() != null) {
            header.add("#" + metadata.number());
        }
        if (metadata.rarity() != null) {
            header.add(metadata.rarity());
        }
        if (!header.isEmpty()) {
            drawCenteredLines(graphics, List.of(String.join("  ", header)), 44, 10);
        }

        drawCenteredColoredLines(
            graphics,
            limitLines(wrapText(metadata.description(), 16), 4),
            76,
            10,
            new Color(235, 235, 235)
        );

        if (metadata.series() != null) {
            drawCenteredLines(graphics, limitLines(wrapText(metadata.series(), 16), 1), 123, 9);
        }

        graphics.dispose();
        return MapPalette.resizeImage(card);
    }

    private static String displayTitle(LoadedMotif motif, TradingCardMetadata metadata) {
        return metadata.title() != null ? metadata.title() : motif.displayName();
    }

    private static void drawCenteredLines(Graphics2D graphics, List<String> lines, int startY, int lineHeight) {
        int y = startY;
        for (String line : lines) {
            int width = graphics.getFontMetrics().stringWidth(line);
            graphics.drawString(line, Math.max(12, (MAP_SIZE - width) / 2), y);
            y += lineHeight;
        }
    }

    private static int drawLeftLines(Graphics2D graphics, List<String> lines, int startY, int lineHeight, Color color) {
        graphics.setColor(color);
        for (String line : lines) {
            graphics.drawString(line, 16, startY);
            startY += lineHeight;
        }
        return startY;
    }

    private static void drawCenteredColoredLines(Graphics2D graphics, List<String> lines, int startY, int lineHeight, Color color) {
        graphics.setColor(color);
        drawCenteredLines(graphics, lines, startY, lineHeight);
    }

    private static List<String> wrapText(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() == 0) {
                line.append(word);
                continue;
            }
            if (line.length() + 1 + word.length() > maxChars) {
                lines.add(line.toString());
                line = new StringBuilder(word);
                continue;
            }
            line.append(' ').append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static List<String> limitLines(List<String> lines, int maxLines) {
        if (lines.size() <= maxLines) {
            return lines;
        }
        List<String> limited = new ArrayList<>(lines.subList(0, maxLines));
        String last = limited.get(maxLines - 1);
        if (last.length() > 13) {
            last = last.substring(0, 12);
        }
        limited.set(maxLines - 1, last + "...");
        return limited;
    }
}
