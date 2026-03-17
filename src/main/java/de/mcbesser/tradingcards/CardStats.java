package de.mcbesser.tradingcards;

import java.util.concurrent.ThreadLocalRandom;

public record CardStats(int health, int hunger, int armor, int strength) {

    public static CardStats random() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new CardStats(
            random.nextInt(2, 11),
            random.nextInt(2, 11),
            random.nextInt(1, 11),
            random.nextInt(1, 11)
        );
    }
}
