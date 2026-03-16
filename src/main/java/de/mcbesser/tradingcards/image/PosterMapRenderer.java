package de.mcbesser.tradingcards.image;

import java.awt.image.BufferedImage;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public final class PosterMapRenderer extends MapRenderer {

    private static final int MAP_SIZE = 128;

    private final BufferedImage segment;
    private boolean rendered;

    public PosterMapRenderer(LoadedMotif motif, int segmentIndex) {
        super(true);
        BufferedImage slice = motif.image().getSubimage(0, segmentIndex * MAP_SIZE, MAP_SIZE, MAP_SIZE);
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
