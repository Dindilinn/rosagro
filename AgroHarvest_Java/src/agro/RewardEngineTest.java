package agro;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;


public final class RewardEngineTest {
    private RewardEngineTest() { }
    public static void main(String[] args) {
        RewardEngine engine = new RewardEngine(new Properties());
        int[] count = new int[3];
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 12000; i++) {
            RewardEngine.Reward reward = engine.draw();
            if (reward.percent() == 5) count[0]++;
            else if (reward.percent() == 10) count[1]++;
            else if (reward.percent() == 15) count[2]++;
            else throw new AssertionError("No reward");
            if (!reward.partner().url().startsWith("https://")) throw new AssertionError("Non-HTTPS partner");
            if (!reward.code().matches("DEMO-AGRO-[A-Z2-9]{6}")) throw new AssertionError("Bad demo code");
            seen.add(reward.code());
        }
        if (count[0] < 6000 || count[0] > 8400 || count[1] < 2600 || count[1] > 4700 ||
                count[2] < 600 || count[2] > 1900) throw new AssertionError("Distribution suspicious");
        if (seen.size() < 11900) throw new AssertionError("Many colliding codes");
        Properties bad = new Properties();
        bad.setProperty("weight.small", "60");
        bad.setProperty("weight.medium", "60");
        try {
            new RewardEngine(bad);
            throw new AssertionError("Invalid weights were accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("PASS: 12000 guaranteed rewards, HTTPS links, demo codes, distributions, invalid config");
        System.out.println("Reward counts: 5%=" + count[0] + " 10%=" + count[1] + " 15%=" + count[2]);
    }
}
