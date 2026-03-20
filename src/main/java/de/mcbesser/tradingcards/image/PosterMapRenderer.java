package de.mcbesser.tradingcards.image;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public final class PosterMapRenderer extends MapRenderer {

    private static final int MAP_SIZE = 128;
    private static final int DISPLAY_HEIGHT = 384;

    private final BufferedImage segment;
    private boolean rendered;

    public PosterMapRenderer(LoadedMotif motif, int segmentIndex, de.mcbesser.tradingcards.CardStats stats, int panelCount) {
        super(false);
        int displayHeight = panelCount * MAP_SIZE;
        BufferedImage display = new BufferedImage(MAP_SIZE, displayHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = display.createGraphics();
        graphics.drawImage(motif.image(), 0, 0, null);
        if (panelCount == 3) {
            graphics.drawImage(MetadataMapRenderer.createCardImage(motif, stats, false, false), 0, MAP_SIZE * 2, null);
        }
        graphics.dispose();
        BufferedImage slice = display.getSubimage(0, segmentIndex * MAP_SIZE, MAP_SIZE, MAP_SIZE);
        this.segment = MapPalette.resizeImage(slice);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) {
            return;
        }
        canvas.drawImage(0, 0, segment);
        rendered = true;
    }
}
