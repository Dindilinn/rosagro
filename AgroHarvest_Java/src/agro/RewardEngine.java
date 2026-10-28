package agro;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Properties;


public final class RewardEngine {
    private final SecureRandom random = new SecureRandom();
    private final int[] weights;
    private final Partner[] partners;

    public record Partner(String name, String subtitle, String url, String initials) {
        public Partner {
            Objects.requireNonNull(name);
            Objects.requireNonNull(subtitle);
            Objects.requireNonNull(url);
            Objects.requireNonNull(initials);
            if (!url.startsWith("https://")) throw new IllegalArgumentException("Partner URLs must use HTTPS");
        }
    }
    public record Reward(int percent, Partner partner, String code, String label) { }

    public RewardEngine(Properties properties) {
        weights = new int[]{percentage(properties, "weight.small", 60),
                percentage(properties, "weight.medium", 30),
                percentage(properties, "weight.large", 10)};
        int sum = weights[0] + weights[1] + weights[2];
        if (sum != 100) throw new IllegalArgumentException("Prize weights must total 100, got " + sum);
        partners = new Partner[]{
                new Partner(
                        properties.getProperty("partner.rodnoe.name", "Своё Родное"),
                        "фермерские продукты",
                        properties.getProperty("partner.rodnoe.url", "https://svoe-rodnoe.ru/"),
                        "СР"),
                new Partner(
                        properties.getProperty("partner.travel.name", "Своё За Городом"),
                        "поездки и впечатления",
                        properties.getProperty("partner.travel.url", "https://svoe-zagorodom.ru/"),
                        "СЗ")
        };
    }

    private static int percentage(Properties p, String key, int fallback) {
        int value;
        try {
            value = Integer.parseInt(p.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer in " + key, e);
        }
        if (value < 0 || value > 100) throw new IllegalArgumentException("Invalid weight for " + key);
        return value;
    }

    public Reward draw() {
        int ticket = random.nextInt(100);
        int tier = ticket < weights[0] ? 0 : ticket < weights[0] + weights[1] ? 1 : 2;
        int percent = switch (tier) { case 0 -> 5; case 1 -> 10; default -> 15; };
        Partner partner = partners[random.nextInt(partners.length)];
        return new Reward(percent, partner, "DEMO-AGRO-" + randomString(6), "Скидка " + percent + "%");
    }

    private String randomString(int count) {
        String symbols = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < count; i++) value.append(symbols.charAt(random.nextInt(symbols.length())));
        return value.toString();
    }
}
