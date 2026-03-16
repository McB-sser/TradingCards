package de.mcbesser.tradingcards.image;

import java.util.List;

public final class TradingCardMetadata {

    private String id;
    private String title;
    private String series;
    private String number;
    private String rarity;
    private String artist;
    private String description;
    private String flavorText;
    private List<String> tags;
    private MotifData motif;

    public String id() {
        return blankToNull(id);
    }

    public String title() {
        return blankToNull(title);
    }

    public String series() {
        return blankToNull(series);
    }

    public String number() {
        return blankToNull(number);
    }

    public String rarity() {
        return blankToNull(rarity);
    }

    public String artist() {
        return blankToNull(artist);
    }

    public String description() {
        return blankToNull(description);
    }

    public String flavorText() {
        return blankToNull(flavorText);
    }

    public List<String> tags() {
        return tags == null ? List.of() : tags.stream().map(this::blankToNull).filter(value -> value != null).toList();
    }

    public MotifData motif() {
        return motif;
    }

    public static TradingCardMetadata fallback(String id, String title) {
        TradingCardMetadata metadata = new TradingCardMetadata();
        metadata.id = id;
        metadata.title = title;
        return metadata;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class MotifData {

        private String file;
        private Integer width;
        private Integer height;
        private String fitMode;
        private Double zoom;
        private Integer offsetX;
        private Integer offsetY;
        private String mapPalette;
        private String background;
        private Boolean transparentBackground;
        private String accent;
        private String sourceFile;

        public String file() {
            return file;
        }

        public Integer width() {
            return width;
        }

        public Integer height() {
            return height;
        }

        public String fitMode() {
            return fitMode;
        }

        public Double zoom() {
            return zoom;
        }

        public Integer offsetX() {
            return offsetX;
        }

        public Integer offsetY() {
            return offsetY;
        }

        public String mapPalette() {
            return mapPalette;
        }

        public String background() {
            return background;
        }

        public Boolean transparentBackground() {
            return transparentBackground;
        }

        public String accent() {
            return accent;
        }

        public String sourceFile() {
            return sourceFile;
        }
    }
}
