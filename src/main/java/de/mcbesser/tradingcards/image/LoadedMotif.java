package de.mcbesser.tradingcards.image;

import java.awt.image.BufferedImage;

public record LoadedMotif(String id, String displayName, BufferedImage image, TradingCardMetadata metadata) {
}
