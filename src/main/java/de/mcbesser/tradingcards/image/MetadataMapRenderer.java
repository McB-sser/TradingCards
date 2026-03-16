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
        this.image = buildCardImage(motif);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) {
            return;
        }
        canvas.drawImage(0, 0, image);
        rendered = true;
    }

    private BufferedImage buildCardImage(LoadedMotif motif) {
        BufferedImage card = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = card.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        TradingCardMetadata metadata = motif.metadata();
        graphics.setColor(new Color(20, 24, 30));
        graphics.fillRect(0, 0, MAP_SIZE, MAP_SIZE);

        graphics.setColor(new Color(215, 188, 111));
        graphics.fillRect(8, 8, MAP_SIZE - 16, MAP_SIZE - 16);

        graphics.setColor(new Color(34, 39, 48));
        graphics.fillRect(12, 12, MAP_SIZE - 24, MAP_SIZE - 24);

        graphics.setColor(new Color(52, 60, 72));
        graphics.fillRect(12, 34, MAP_SIZE - 24, 2);
        graphics.fillRect(12, 54, MAP_SIZE - 24, 1);
        graphics.fillRect(12, 92, MAP_SIZE - 24, 1);

        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        drawCenteredLines(graphics, limitLines(wrapText(displayTitle(motif, metadata), 14), 2), 21, 10);

        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
        List<String> header = new ArrayList<>();
        if (metadata.number() != null) {
            header.add("#" + metadata.number());
        }
        if (metadata.rarity() != null) {
            header.add(metadata.rarity());
        }
        if (!header.isEmpty()) {
            drawCenteredLines(graphics, List.of(String.join("  ", header)), 46, 9);
        }

        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
        int y = 66;
        if (metadata.series() != null) {
            y = drawLeftLines(graphics, limitLines(wrapText("Set: " + metadata.series(), 16), 1), y, 9, new Color(144, 203, 157));
            y += 3;
        }
        y = drawLeftLines(graphics, limitLines(wrapText(metadata.description(), 15), 3), y, 9, new Color(230, 230, 230));
        y += 4;
        y = drawLeftLines(graphics, limitLines(wrapText(metadata.flavorText(), 15), 2), y, 9, new Color(172, 172, 172));
        y = Math.max(y, 98);
        if (!metadata.tags().isEmpty()) {
            y = drawLeftLines(graphics, limitLines(wrapText("Tags: " + String.join(", ", metadata.tags()), 16), 1), y + 2, 8, new Color(144, 203, 157));
        }
        if (metadata.artist() != null) {
            y = drawLeftLines(graphics, limitLines(wrapText("Artist: " + metadata.artist(), 16), 1), y + 2, 8, new Color(194, 194, 194));
        }
        drawLeftLines(graphics, limitLines(wrapText("Motif: " + motif.id(), 16), 1), 120, 8, new Color(194, 194, 194));

        graphics.dispose();
        return MapPalette.resizeImage(card);
    }

    private String displayTitle(LoadedMotif motif, TradingCardMetadata metadata) {
        return metadata.title() != null ? metadata.title() : motif.displayName();
    }

    private void drawCenteredLines(Graphics2D graphics, List<String> lines, int startY, int lineHeight) {
        int y = startY;
        for (String line : lines) {
            int width = graphics.getFontMetrics().stringWidth(line);
            graphics.drawString(line, Math.max(12, (MAP_SIZE - width) / 2), y);
            y += lineHeight;
        }
    }

    private int drawLeftLines(Graphics2D graphics, List<String> lines, int startY, int lineHeight, Color color) {
        graphics.setColor(color);
        for (String line : lines) {
            graphics.drawString(line, 16, startY);
            startY += lineHeight;
        }
        return startY;
    }

    private List<String> wrapText(String text, int maxChars) {
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

    private List<String> limitLines(List<String> lines, int maxLines) {
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
