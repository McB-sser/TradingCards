package de.mcbesser.tradingcards.image;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import de.mcbesser.tradingcards.TradingCardsPlugin;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

public final class MotifRegistry {

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".png", ".jpg", ".jpeg");

    private final TradingCardsPlugin plugin;
    private final File motifsFolder;
    private final Map<String, LoadedMotif> motifs = new LinkedHashMap<>();
    private final Gson gson = new Gson();

    public MotifRegistry(TradingCardsPlugin plugin, File motifsFolder) {
        this.plugin = plugin;
        this.motifsFolder = motifsFolder;
    }

    public void reload() throws IOException {
        ensureFolderExists();
        motifs.clear();

        try (Stream<java.nio.file.Path> paths = Files.list(motifsFolder.toPath())) {
            paths.filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .forEach(path -> {
                    String fileName = path.getFileName().toString();
                    if (!isSupported(fileName)) {
                        return;
                    }

                    try {
                        BufferedImage source = ImageIO.read(path.toFile());
                        if (source == null) {
                            plugin.getLogger().warning("Skipping unreadable image: " + fileName);
                            return;
                        }

                        String id = stripExtension(fileName).toLowerCase(Locale.ROOT);
                        TradingCardMetadata metadata = loadMetadata(path.toFile(), id);
                        String displayName = metadata.title() != null ? metadata.title() : toDisplayName(id);
                        motifs.put(id, new LoadedMotif(id, displayName, resizeToMap(source), metadata));
                    } catch (IOException exception) {
                        plugin.getLogger().warning("Failed to load motif " + fileName + ": " + exception.getMessage());
                    }
                });
        }
    }

    public LoadedMotif find(String id) {
        return motifs.get(id.toLowerCase(Locale.ROOT));
    }

    public List<String> getMotifIds() {
        return new ArrayList<>(motifs.keySet());
    }

    public int getMotifCount() {
        return motifs.size();
    }

    private void ensureFolderExists() throws IOException {
        if (!motifsFolder.exists()) {
            Files.createDirectories(motifsFolder.toPath());
        }
    }

    private boolean isSupported(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private String stripExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot == -1 ? fileName : fileName.substring(0, lastDot);
    }

    private TradingCardMetadata loadMetadata(File imageFile, String id) {
        File metadataFile = new File(imageFile.getParentFile(), stripExtension(imageFile.getName()) + ".json");
        if (!metadataFile.isFile()) {
            return TradingCardMetadata.fallback(id, toDisplayName(id));
        }

        try (Reader reader = Files.newBufferedReader(metadataFile.toPath())) {
            TradingCardMetadata metadata = gson.fromJson(reader, TradingCardMetadata.class);
            if (metadata == null) {
                plugin.getLogger().warning("Metadata file is empty: " + metadataFile.getName());
                return TradingCardMetadata.fallback(id, toDisplayName(id));
            }
            return metadata;
        } catch (IOException | JsonSyntaxException exception) {
            plugin.getLogger().warning("Failed to load metadata " + metadataFile.getName() + ": " + exception.getMessage());
            return TradingCardMetadata.fallback(id, toDisplayName(id));
        }
    }

    private String toDisplayName(String id) {
        String[] parts = id.split("[\\-_\\s]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private BufferedImage resizeToMap(BufferedImage source) {
        BufferedImage scaled = new BufferedImage(128, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, 128, 256, null);
        graphics.dispose();
        return scaled;
    }
}
