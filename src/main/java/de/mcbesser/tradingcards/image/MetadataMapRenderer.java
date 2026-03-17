package de.mcbesser.tradingcards.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
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

    public MetadataMapRenderer(LoadedMotif motif, de.mcbesser.tradingcards.CardStats stats) {
        super(true);
        this.image = createCardImage(motif, stats);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) {
            return;
        }
        canvas.drawImage(0, 0, image);
        rendered = true;
    }

    public static BufferedImage createCardImage(LoadedMotif motif, de.mcbesser.tradingcards.CardStats stats) {
        BufferedImage card = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = card.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        TradingCardMetadata metadata = motif.metadata();
        graphics.setColor(new Color(42, 42, 46));
        graphics.fillRect(0, 0, MAP_SIZE, MAP_SIZE);
        graphics.setColor(new Color(36, 36, 40));
        graphics.fillRect(0, 0, 14, MAP_SIZE);
        graphics.setColor(new Color(80, 80, 88));
        graphics.fillRect(15, 12, MAP_SIZE - 23, 1);
        graphics.fillRect(15, 31, MAP_SIZE - 23, 1);
        graphics.fillRect(15, 104, MAP_SIZE - 23, 1);

        drawVerticalMeta(graphics, metadata);

        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        drawCenteredLinesInArea(graphics, limitLines(wrapText(displayTitle(motif, metadata), 12), 2), 25, 18, 106, 12);

        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
        drawCenteredColoredLines(
            graphics,
            limitLines(wrapText(metadata.description(), 17), 3),
            47,
            9,
            new Color(235, 235, 235),
            18,
            106
        );

        drawStatGrid(graphics, stats, metadata);

        graphics.dispose();
        return MapPalette.resizeImage(card);
    }

    private static String displayTitle(LoadedMotif motif, TradingCardMetadata metadata) {
        return metadata.title() != null ? metadata.title() : motif.displayName();
    }

    private static void drawCenteredLines(Graphics2D graphics, List<String> lines, int startY, int lineHeight) {
        drawCenteredLinesInArea(graphics, lines, startY, 0, MAP_SIZE, lineHeight);
    }

    private static void drawCenteredLinesInArea(Graphics2D graphics, List<String> lines, int startY, int startX, int width, int lineHeight) {
        int y = startY;
        for (String line : lines) {
            int lineWidth = graphics.getFontMetrics().stringWidth(line);
            int x = startX + Math.max(2, (width - lineWidth) / 2);
            graphics.drawString(line, x, y);
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

    private static void drawCenteredColoredLines(Graphics2D graphics, List<String> lines, int startY, int lineHeight, Color color, int startX, int width) {
        graphics.setColor(color);
        drawCenteredLinesInArea(graphics, lines, startY, startX, width, lineHeight);
    }

    private static void drawStatGrid(Graphics2D graphics, de.mcbesser.tradingcards.CardStats stats, TradingCardMetadata metadata) {
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        drawStatCell(graphics, 14, 72, new Color(220, 70, 70), "Leben", "\u2665", stats.health(), 5, 9);
        drawStatCell(graphics, 74, 72, new Color(214, 153, 64), "Hunger", "\u25C6", stats.hunger(), 5, 8);
        drawStatCell(graphics, 14, 91, new Color(120, 170, 220), "R\u00FCstung", "\u25A0", stats.armor(), 5, 8);
        drawStatCell(graphics, 74, 91, new Color(186, 120, 220), "Kraft", "\u2736", stats.strength(), 5, 7);

        double multiplier = rarityMultiplier(metadata.rarity());
        int cardValue = cardValue(stats, multiplier);
        String rarityText = metadata.rarity() != null ? metadata.rarity() : "Common";

        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 9));
        graphics.setColor(rarityColor(metadata.rarity()));
        graphics.drawString(rarityText + " x " + formatMultiplier(multiplier), 18, 115);
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        graphics.setColor(new Color(235, 210, 120));
        String valueText = "= " + cardValue;
        graphics.drawString(valueText, 123 - graphics.getFontMetrics().stringWidth(valueText), 126);
    }

    private static void drawStatCell(Graphics2D graphics, int x, int y, Color color, String label, String icon, int value, int maxSymbols, int stepWidth) {
        graphics.setColor(color);
        graphics.drawString(label, x, y);
        graphics.drawString(String.valueOf(value), x, y + 9);
        int barX = x + 14;
        int barY = icon.equals("\u2665") ? y + 10 : y + 9;
        drawStatBar(graphics, barX, barY, color, icon, value, maxSymbols, stepWidth);
    }

    private static void drawStatBar(Graphics2D graphics, int x, int y, Color activeColor, String icon, int value, int maxSymbols, int stepWidth) {
        int currentX = x;
        for (int i = 0; i < maxSymbols; i++) {
            int remaining = value - (i * 2);
            graphics.setColor(new Color(95, 95, 95));
            graphics.drawString(icon, currentX, y);
            if (remaining >= 2) {
                graphics.setColor(activeColor);
                graphics.drawString(icon, currentX, y);
            } else if (remaining == 1) {
                drawPartialSymbol(graphics, currentX, y, activeColor, icon);
            }
            currentX += stepWidth;
        }
    }

    private static void drawPartialSymbol(Graphics2D graphics, int x, int y, Color activeColor, String icon) {
        Shape oldClip = graphics.getClip();
        int diagonalX = switch (icon) {
            case "\u2665", "\u2736" -> x + 5;
            default -> x + 7;
        };

        Polygon activeTriangle = new Polygon(
            new int[] {x, diagonalX, x},
            new int[] {y - 9, y - 9, y + 1},
            3
        );
        Polygon grayTriangle = new Polygon(
            new int[] {diagonalX, diagonalX, x},
            new int[] {y - 9, y + 1, y + 1},
            3
        );

        graphics.setClip(grayTriangle);
        graphics.setColor(new Color(95, 95, 95));
        graphics.drawString(icon, x, y);

        graphics.setClip(activeTriangle);
        graphics.setColor(activeColor);
        graphics.drawString(icon, x, y);

        graphics.setClip(oldClip);
    }

    private static void drawVerticalMeta(Graphics2D graphics, TradingCardMetadata metadata) {
        String number = metadata.number() != null ? "#" + metadata.number() : "#---";
        String series = metadata.series() != null ? metadata.series() : "";
        String vertical = series.isBlank() ? number : number + "  " + series;
        AffineTransform oldTransform = graphics.getTransform();
        graphics.setColor(new Color(185, 185, 190));
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 9));
        graphics.rotate(-Math.PI / 2);
        graphics.drawString(vertical, -119, 10);
        graphics.setTransform(oldTransform);
    }

    private static String formatMultiplier(double multiplier) {
        return String.format(java.util.Locale.US, "%.2f", multiplier);
    }

    private static int cardValue(de.mcbesser.tradingcards.CardStats stats, double multiplier) {
        int total = stats.health() + stats.hunger() + stats.armor() + stats.strength();
        return (int) Math.round(total * multiplier * 10);
    }

    private static Color rarityColor(String rarity) {
        if (rarity == null) {
            return new Color(205, 205, 205);
        }
        return switch (rarity.toLowerCase(java.util.Locale.ROOT)) {
            case "uncommon" -> new Color(120, 210, 120);
            case "rare" -> new Color(110, 180, 235);
            case "epic" -> new Color(190, 120, 235);
            case "legendary" -> new Color(240, 190, 90);
            default -> new Color(205, 205, 205);
        };
    }

    private static double rarityMultiplier(String rarity) {
        if (rarity == null) {
            return 1.00D;
        }
        return switch (rarity.toLowerCase(java.util.Locale.ROOT)) {
            case "uncommon" -> 1.15D;
            case "rare" -> 1.35D;
            case "epic" -> 1.65D;
            case "legendary" -> 2.00D;
            default -> 1.00D;
        };
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
