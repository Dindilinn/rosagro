package agro;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public final class AgroHarvest {
    private static final int W = 1200;
    private static final int H = 760;
    private static final Color INK = rgb(24, 55, 41);
    private static final Color DARK = rgb(16, 57, 42);
    private static final Color MOSS = rgb(65, 108, 58);
    private static final Color LIME = rgb(205, 240, 99);
    private static final Color PAPER = rgb(247, 247, 240);
    private static final Color MUTED = rgb(100, 117, 103);
    private static final Color STROKE = rgb(222, 229, 211);
    private static final Color LIGHT = rgb(232, 241, 220);
    private static final Color ORANGE = rgb(239, 172, 83);
    private static final String[] CROPS = {"Клевер", "Подсолнух", "Яблоня"};
    private static final String[] CROP_DESCRIPTIONS = {"Четыре листа удачи", "Поймать тёплый луч", "Созреть для подарка"};

    private enum Stage { START, CHOOSE, GROW, REWARD }

    private static final class Zone {
        final String id;
        final RoundRectangle2D.Double shape;
        Zone(String id, double x, double y, double w, double h, double r) {
            this.id = id;
            this.shape = new RoundRectangle2D.Double(x, y, w, h, r, r);
        }
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "Урожай подарков");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        if (args.length > 0 && "--verify-ui".equals(args[0])) {
            try { verifyUi(); } catch (Exception e) { e.printStackTrace(); System.exit(1); }
            return;
        }
        if (args.length > 0 && "--screenshots".equals(args[0])) {
            try {
                Path target = args.length > 1 ? Path.of(args[1]) : Path.of("screenshots");
                saveScreenshots(target);
                System.out.println("Saved 4 demo screenshots to " + target.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("Screenshot generation failed: " + e.getMessage());
                System.exit(1);
            }
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("A graphical desktop is required. Use --screenshots for headless rendering.");
            System.exit(1);
        }
        SwingUtilities.invokeLater(() -> {
            try {
                GameConfig config = new GameConfig();
                JFrame frame = new JFrame("Я в Агро | Урожай подарков — прототип");
                GameCanvas canvas = new GameCanvas(config);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setContentPane(canvas);
                frame.setSize(W, H);
                frame.setMinimumSize(new java.awt.Dimension(940, 610));
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                canvas.requestFocusInWindow();
                canvas.startAnimationTimer();
                metric("app_opened");
            } catch (RuntimeException e) {
                javax.swing.JOptionPane.showMessageDialog(null, e.getMessage(), "Ошибка конфигурации",
                        javax.swing.JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        });
    }

    private static void verifyUi() {
        GameCanvas c = new GameCanvas(new GameConfig());
        c.setSize(W, H);
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        c.paint(graphics);
        c.clickAt(190, 534);
        if (c.stage != Stage.CHOOSE) throw new AssertionError("Start click did not open game");
        c.paint(graphics);
        c.clickAt(590, 425);
        if (c.selected != 1) throw new AssertionError("Field selection failed");
        c.paint(graphics);
        c.clickAt(200, 652);
        if (c.stage != Stage.GROW) throw new AssertionError("Harvest click did not start animation");
        c.paint(graphics);
        c.finish();
        if (c.stage != Stage.REWARD || c.reward == null || !c.reward.code().startsWith("DEMO-"))
            throw new AssertionError("Reward flow failed");
        c.paint(graphics);
        String partnerName = c.rewardEngine.draw().partner().name();
        if (!partnerName.equals("Своё Родное") && !partnerName.equals("Своё За Городом"))
            throw new AssertionError("Broken UTF-8 partner name: " + partnerName);
        c.clickAt(160, 627);
        if (c.stage != Stage.START) throw new AssertionError("Replay failed");
        graphics.dispose();
        System.out.println("PASS: start -> select plot -> grow -> guaranteed reward -> replay");
    }

    private static void saveScreenshots(Path dir) throws IOException {
        Files.createDirectories(dir);
        GameCanvas canvas = new GameCanvas(new GameConfig());
        canvas.setSize(W, H);
        canvas.writePng(dir.resolve("01-start.png"));
        canvas.stage = Stage.CHOOSE;
        canvas.selected = 1;
        canvas.startedNanos = System.nanoTime();
        canvas.writePng(dir.resolve("02-choose.png"));
        canvas.stage = Stage.GROW;
        canvas.growthProgress = 0.64;
        canvas.writePng(dir.resolve("03-grow.png"));
        canvas.stage = Stage.REWARD;
        canvas.reward = new RewardEngine.Reward(10,
                new RewardEngine.Partner("Своё Родное", "фермерские продукты", "https://svoe-rodnoe.ru/", "СР"),
                "DEMO-AGRO-7K9M2Q", "Скидка 10%");
        canvas.writePng(dir.resolve("04-reward.png"));
    }

    private static void metric(String event) {
        System.out.printf(Locale.ROOT, "[%s] event=%s%n", LocalTime.now().withNano(0), event);
    }

    private static Color rgb(int r, int g, int b) { return new Color(r, g, b); }

    private static final class GameCanvas extends JPanel {
        private final GameConfig config;
        private final RewardEngine rewardEngine;
        private final List<Zone> zones = new ArrayList<>();
        private Stage stage = Stage.START;
        private int selected = -1;
        private String hovered = "";
        private RewardEngine.Reward reward;
        private long startedNanos;
        private long growthStartedNanos;
        private double growthProgress;
        private boolean expired;
        private long flashUntilNanos;
        private String flashMessage = "";
        private Timer animationTimer;

        GameCanvas(GameConfig config) {
            this.config = config;
            this.rewardEngine = new RewardEngine(config.properties);
            setBackground(PAPER);
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { clickAt(e.getX(), e.getY()); }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    String next = findZone(e.getX(), e.getY());
                    if (!hovered.equals(next)) {
                        hovered = next;
                        setCursor(Cursor.getPredefinedCursor(next.isEmpty()
                                ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
                        repaint();
                    }
                }
            });
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> {
                            if (stage == Stage.START) start();
                            else if (stage == Stage.CHOOSE && selected >= 0) grow();
                        }
                        case KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3 -> {
                            if (stage == Stage.CHOOSE) choose(e.getKeyCode() - KeyEvent.VK_1);
                        }
                        case KeyEvent.VK_ESCAPE -> { if (stage == Stage.REWARD) reset(); }
                        default -> { }
                    }
                }
            });
        }

        void startAnimationTimer() {
            animationTimer = new Timer(33, e -> {
                if (stage == Stage.CHOOSE || stage == Stage.GROW) {
                    if (elapsedSeconds() >= config.sessionSeconds) {
                        expired = true;
                        finish();
                    } else if (stage == Stage.GROW) {
                        growthProgress = Math.min(1.0, (System.nanoTime() - growthStartedNanos)
                                / (config.growthMillis * 1_000_000.0));
                        if (growthProgress >= 1.0) finish();
                    }
                }
                repaint();
            });
            animationTimer.start();
        }

        void writePng(Path filename) throws IOException {
            BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            setSize(W, H);
            paint(g);
            g.dispose();
            if (!ImageIO.write(image, "png", filename.toFile())) throw new IOException("No PNG writer");
        }

        private void clickAt(int pixelX, int pixelY) {
            String zone = findZone(pixelX, pixelY);
            switch (zone) {
                case "start" -> start();
                case "crop0", "crop1", "crop2" -> choose(zone.charAt(4) - '0');
                case "grow" -> grow();
                case "copy" -> copyPromo();
                case "partner" -> openPartner();
                case "again" -> reset();
                case "about" -> openWeb("https://svoevagro.ru/");
                default -> { }
            }
            requestFocusInWindow();
            repaint();
        }

        private String findZone(int px, int py) {
            double scale = Math.min(getWidth() / (double) W, getHeight() / (double) H);
            if (scale <= 0) return "";
            double x = (px - (getWidth() - W * scale) / 2) / scale;
            double y = (py - (getHeight() - H * scale) / 2) / scale;
            for (Zone z : zones) if (z.shape.contains(x, y)) return z.id;
            return "";
        }

        private void start() {
            if (stage != Stage.START) return;
            stage = Stage.CHOOSE;
            selected = -1;
            expired = false;
            reward = null;
            startedNanos = System.nanoTime();
            metric("game_started");
            repaint();
        }
        private void choose(int crop) {
            if (stage != Stage.CHOOSE || crop < 0 || crop >= CROPS.length) return;
            selected = crop;
            metric("crop_selected_" + (crop + 1));
            repaint();
        }
        private void grow() {
            if (stage != Stage.CHOOSE || selected < 0) return;
            stage = Stage.GROW;
            growthStartedNanos = System.nanoTime();
            growthProgress = 0;
            metric("harvest_started");
            repaint();
        }
        private void finish() {
            if (stage == Stage.REWARD || stage == Stage.START) return;
            reward = rewardEngine.draw();
            stage = Stage.REWARD;
            metric("reward_granted_" + reward.percent());
            repaint();
        }
        private void reset() {
            stage = Stage.START;
            reward = null;
            selected = -1;
            growthProgress = 0;
            expired = false;
            hovered = "";
            metric("game_replayed");
            repaint();
        }
        private int elapsedSeconds() {
            return startedNanos == 0 ? 0 : (int)((System.nanoTime() - startedNanos) / 1_000_000_000L);
        }
        private String timeRemaining() {
            int remaining = Math.max(0, config.sessionSeconds - elapsedSeconds());
            return String.format(Locale.ROOT, "%02d:%02d", remaining / 60, remaining % 60);
        }
        private void flash(String message) {
            flashMessage = message;
            flashUntilNanos = System.nanoTime() + 2_600_000_000L;
        }
        private void copyPromo() {
            if (stage != Stage.REWARD || reward == null) return;
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(reward.code()), null);
                flash("Промокод скопирован!");
                metric("promo_copied");
            } catch (IllegalStateException | java.awt.HeadlessException e) {
                flash("Не удалось скопировать. Выдели код вручную.");
                metric("promo_copy_failed");
            }
        }
        private void openPartner() {
            if (stage != Stage.REWARD || reward == null) return;
            metric("partner_opened");
            openWeb(reward.partner().url());
        }
        private void openWeb(String target) {
            try {
                if (!target.startsWith("https://")) throw new IOException("Only HTTPS links allowed");
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new IOException("Browser integration is unavailable");
                }
                Desktop.getDesktop().browse(URI.create(target));
            } catch (Exception e) {
                try {
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(target), null);
                    flash("Ссылка скопирована — открой её в браузере");
                } catch (Exception ex) {
                    flash("Открой адрес вручную: " + target);
                }
            }
        }

        @Override protected void paintComponent(java.awt.Graphics raw) {
            super.paintComponent(raw);
            Graphics2D g = (Graphics2D) raw.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double scale = Math.min(getWidth() / (double) W, getHeight() / (double) H);
            double ox = (getWidth() - W * scale) / 2;
            double oy = (getHeight() - H * scale) / 2;
            g.translate(ox, oy);
            g.scale(scale, scale);
            g.setColor(PAPER);
            g.fillRect(0, 0, W, H);
            zones.clear();
            paintHeader(g);
            switch (stage) {
                case START -> paintStart(g);
                case CHOOSE -> paintChoose(g);
                case GROW -> paintGrow(g);
                case REWARD -> paintReward(g);
            }
            paintFooter(g);
            if (System.nanoTime() < flashUntilNanos) paintToast(g);
            g.dispose();
        }

        private void paintHeader(Graphics2D g) {
            g.setColor(DARK);
            g.fill(new RoundRectangle2D.Double(70, 28, 42, 42, 14, 14));
            paintSprout(g, 91, 53, 0.55, LIME);
            text(g, "я в агро", 123, 61, 29, Font.BOLD, INK);
            text(g, "/  УРОЖАЙ ПОДАРКОВ", 272, 58, 12, Font.BOLD, MUTED);
            g.setColor(STROKE);
            g.fillRoundRect(70, 96, 1060, 1, 1, 1);
            if (stage == Stage.CHOOSE || stage == Stage.GROW) {
                pill(g, 873, 32, 137, 38, "ЛИМИТ 1 МИН", LIGHT, INK, 11);
                pill(g, 1020, 32, 110, 38, timeRemaining(), DARK, Color.WHITE, 19);
            } else {
                pill(g, 970, 32, 160, 38, "DEMO  /  2026", LIGHT, INK, 13);
            }
        }

        private void paintFooter(Graphics2D g) {
            g.setColor(STROKE);
            g.fillRoundRect(70, 702, 1060, 1, 1, 1);
            text(g, "КОНЦЕПТ ДЛЯ «Я В АГРО»", 70, 730, 12, Font.BOLD, MUTED);
            text(g, "Демо-промокоды не действуют", 860, 730, 12, Font.PLAIN, MUTED);
        }

        private void paintStart(Graphics2D g) {
            pill(g, 70, 151, 189, 34, "МИНИ-ИГРА  ·  20 СЕК", LIGHT, MOSS, 12);
            text(g, "Урожай", 67, 275, 78, Font.BOLD, INK);
            text(g, "подарков.", 67, 359, 78, Font.BOLD, INK);
            text(g, "Посади удачу — собери свой бонус.", 72, 417, 22, Font.PLAIN, INK);
            text(g, "Всего два действия, и приз уже у тебя.", 72, 449, 17, Font.PLAIN, MUTED);
            button(g, "start", 70, 502, 255, 64, "Начать игру", LIME, INK, true, 20);
            arrow(g, 290, 534, INK, 1.2);
            paintTinyCheck(g, 79, 619);
            text(g, "ГАРАНТИРОВАННЫЙ ПОДАРОК", 106, 625, 13, Font.BOLD, MOSS);
            text(g, "Скидки 5%, 10% или 15% — без проигрыша", 72, 651, 14, Font.PLAIN, MUTED);

            g.setColor(rgb(224, 237, 208));
            g.fill(new RoundRectangle2D.Double(601, 145, 529, 527, 38, 38));
            Graphics2D art = (Graphics2D) g.create();
            art.setClip(new RoundRectangle2D.Double(601, 145, 529, 527, 38, 38));
            art.setPaint(new GradientPaint(610, 150, rgb(229, 243, 211), 1110, 515, rgb(198, 225, 174)));
            art.fillRect(602, 146, 527, 527);
            art.setColor(rgb(254, 233, 157));
            art.fill(new Ellipse2D.Double(952, 209, 112, 112));
            art.setColor(rgb(185, 217, 154));
            art.fill(new Ellipse2D.Double(552, 338, 670, 400));
            art.setColor(rgb(145, 191, 124));
            art.fill(new Ellipse2D.Double(724, 399, 550, 320));
            art.setColor(rgb(89, 142, 81));
            art.fill(new Ellipse2D.Double(555, 505, 795, 320));
            art.setColor(rgb(47, 102, 62));
            for (int i = 0; i < 7; i++) {
                int x = 545 + i * 95;
                art.fill(new Ellipse2D.Double(x, 633 + (i % 2) * 4, 200, 120));
            }
            paintStem(art, 726, 511, 1.15);
            paintFlower(art, 892, 435, 1.05);
            paintTree(art, 1040, 520, 0.85);
            pill(art, 641, 184, 136, 35, "ТВОЙ ПРИЗ ТУТ", Color.WHITE, MOSS, 11);
            art.dispose();
            smallPartnerTile(g, 680, 588, "Своё Родное");
            smallPartnerTile(g, 882, 588, "Своё За Городом");
        }

        private void paintChoose(Graphics2D g) {
            pill(g, 70, 130, 165, 32, "ШАГ 01 / 02", LIGHT, MOSS, 12);
            text(g, "Выбери свою грядку", 68, 225, 51, Font.BOLD, INK);
            text(g, "В каждой спрятан бонус. Выбор за тобой!", 71, 259, 17, Font.PLAIN, MUTED);
            for (int i = 0; i < 3; i++) {
                int x = 70 + i * 358;
                paintCropCard(g, x, 288, i);
            }
            boolean enabled = selected >= 0;
            button(g, enabled ? "grow" : "", 70, 627, 290, 55,
                    enabled ? "Вырастить бонус" : "Выбери одну грядку", enabled ? LIME : rgb(226, 230, 216),
                    enabled ? INK : MUTED, enabled, 17);
            if (enabled) arrow(g, 327, 654, INK, 1.0);
            text(g, "1. Выбери грядку   →   2. Собери урожай", 397, 661, 15, Font.PLAIN, MUTED);
        }

        private void paintCropCard(Graphics2D g, int x, int y, int i) {
            boolean active = i == selected;
            Color surface = active ? rgb(234, 246, 210) : Color.WHITE;
            g.setColor(surface);
            g.fill(new RoundRectangle2D.Double(x, y, 342, 312, 28, 28));
            g.setColor(active ? rgb(91, 142, 70) : STROKE);
            g.setStroke(new BasicStroke(active ? 3f : 1.3f));
            g.draw(new RoundRectangle2D.Double(x + 1, y + 1, 340, 310, 27, 27));
            g.setStroke(new BasicStroke(1));
            pill(g, x + 23, y + 19, 54, 27, String.format("0%d", i + 1), active ? DARK : LIGHT,
                    active ? Color.WHITE : MOSS, 12);
            if (active) {
                g.setColor(MOSS);
                g.setStroke(new BasicStroke(3));
                g.drawOval(x + 290, y + 22, 22, 22);
                g.fillOval(x + 296, y + 28, 10, 10);
                g.setStroke(new BasicStroke(1));
            } else {
                g.setColor(STROKE);
                g.setStroke(new BasicStroke(2));
                g.drawOval(x + 290, y + 22, 22, 22);
                g.setStroke(new BasicStroke(1));
            }
            Graphics2D crop = (Graphics2D) g.create();
            crop.translate(x + 171, y + (i == 1 ? 141 : 170));
            if (i == 0) paintStem(crop, 0, 0, 1.1);
            if (i == 1) paintFlower(crop, 0, -4, 0.86);
            if (i == 2) paintTree(crop, 0, 13, 0.86);
            crop.dispose();
            center(g, CROPS[i], x + 171, y + 255, 26, Font.BOLD, INK);
            center(g, CROP_DESCRIPTIONS[i], x + 171, y + 284, 14, Font.PLAIN, MUTED);
            zones.add(new Zone("crop" + i, x, y, 342, 312, 28));
        }

        private void paintGrow(Graphics2D g) {
            pill(g, 70, 134, 163, 32, "ШАГ 02 / 02", LIGHT, MOSS, 12);
            center(g, "Собираем твой урожай", 600, 235, 46, Font.BOLD, INK);
            center(g, "Чуть-чуть волшебства и подарок откроется", 600, 272, 17, Font.PLAIN, MUTED);
            g.setColor(LIGHT);
            g.fill(new RoundRectangle2D.Double(363, 296, 474, 296, 32, 32));
            Graphics2D icon = (Graphics2D) g.create();
            icon.translate(600, 439);
            icon.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f + 0.7f * (float) growthProgress));
            icon.scale(0.3 + 0.7 * growthProgress, 0.3 + 0.7 * growthProgress);
            if (selected == 1) paintFlower(icon, 0, 0, 1.0);
            else if (selected == 2) paintTree(icon, 0, 0, 0.9);
            else paintStem(icon, 0, 0, 1.2);
            icon.dispose();
            g.setColor(rgb(206, 219, 193));
            g.fill(new RoundRectangle2D.Double(363, 614, 474, 15, 15, 15));
            g.setColor(MOSS);
            g.fill(new RoundRectangle2D.Double(363, 614, 474 * Math.max(0.02, growthProgress), 15, 15, 15));
            center(g, "Урожай созревает...", 600, 664, 15, Font.PLAIN, MUTED);
        }

        private void paintReward(Graphics2D g) {
            confetti(g);
            pill(g, 70, 144, 181, 33, "ПОДАРОК НАЙДЕН!", LIGHT, MOSS, 12);
            text(g, expired ? "Время вышло." : "Ура! Урожай", 65, 248, 64, Font.BOLD, INK);
            text(g, expired ? "Приз всё равно твой." : "созрел.", 65, 316, expired ? 42 : 64, Font.BOLD, INK);
            text(g, "Тебе достаётся", 70, 380, 18, Font.PLAIN, MUTED);
            text(g, "−" + reward.percent() + "%", 64, 492, 104, Font.BOLD, DARK);
            text(g, "на " + reward.partner().subtitle(), 70, 529, 19, Font.PLAIN, INK);
            button(g, "again", 70, 601, 236, 57, "Играть ещё раз", LIGHT, INK, true, 16);

            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Double(615, 142, 515, 519, 34, 34));
            g.setColor(STROKE);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new RoundRectangle2D.Double(615, 142, 515, 519, 34, 34));
            g.setStroke(new BasicStroke(1));
            g.setColor(LIGHT);
            g.fill(new RoundRectangle2D.Double(648, 177, 450, 119, 23, 23));
            g.setColor(DARK);
            g.fill(new RoundRectangle2D.Double(673, 197, 69, 75, 18, 18));
            paintSprout(g, 707, 247, 1.0, LIME);
            text(g, "ТВОЙ ПАРТНЁР", 760, 216, 12, Font.BOLD, MUTED);
            text(g, reward.partner().name(), 758, 250, 25, Font.BOLD, INK);
            text(g, "Забирай свой бонус", 648, 337, 24, Font.BOLD, INK);
            text(g, "Твой промокод", 649, 372, 14, Font.PLAIN, MUTED);
            g.setColor(PAPER);
            g.fill(new RoundRectangle2D.Double(648, 390, 450, 72, 16, 16));
            text(g, reward.code(), 669, 435, 25, Font.BOLD, INK);
            button(g, "copy", 648, 482, 210, 57, "Копировать код", LIME, INK, true, 15);
            button(g, "partner", 868, 482, 230, 57, "К партнёру", DARK, Color.WHITE, true, 16);
            arrow(g, 1060, 510, Color.WHITE, 0.7);
            text(g, "ДЕМО-ПРОМОКОД  ·  НЕ ДЕЙСТВУЕТ", 651, 590, 12, Font.BOLD, MOSS);
            text(g, "Для запуска нужны реальные коды партнёров.", 650, 621, 13, Font.PLAIN, MUTED);
        }

        private void confetti(Graphics2D g) {
            Color[] cs = {rgb(145, 190, 101), rgb(255, 206, 108), rgb(170, 202, 132)};
            for (int i = 0; i < 33; i++) {
                int x = 40 + (i * 117 + 31) % 1100;
                int y = 130 + (i * 79 + 55) % 530;
                if (x > 575 && x < 1150 && y > 125 && y < 680) continue;
                g.setColor(cs[i % cs.length]);
                g.fillRoundRect(x, y, 8, 13, 3, 3);
            }
        }

        private void smallPartnerTile(Graphics2D g, int x, int y, String name) {
            g.setColor(new Color(255, 255, 255, 240));
            g.fill(new RoundRectangle2D.Double(x, y, 190, 52, 18, 18));
            g.setColor(MOSS);
            g.fillOval(x + 14, y + 17, 17, 17);
            text(g, name, x + 39, y + 32, 15, Font.BOLD, INK);
        }

        private void paintTinyCheck(Graphics2D g, int x, int y) {
            g.setColor(MOSS);
            g.fillOval(x - 5, y - 13, 22, 22);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D check = new Path2D.Double();
            check.moveTo(x, y - 2);
            check.lineTo(x + 4, y + 2);
            check.lineTo(x + 12, y - 6);
            g.draw(check);
            g.setStroke(new BasicStroke(1));
        }

        private void paintSprout(Graphics2D g, double x, double y, double scale, Color color) {
            Graphics2D s = (Graphics2D) g.create();
            s.translate(x, y); s.scale(scale, scale);
            s.setColor(color);
            s.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            s.drawLine(0, 8, 0, -18);
            s.fill(new Ellipse2D.Double(-16, -20, 18, 10));
            s.fill(new Ellipse2D.Double(0, -27, 17, 10));
            s.dispose();
        }

        private void paintStem(Graphics2D g, double x, double y, double scale) {
            Graphics2D s = (Graphics2D) g.create();
            s.translate(x, y); s.scale(scale, scale);
            s.setColor(rgb(72, 125, 72));
            s.fill(new RoundRectangle2D.Double(-5, -29, 10, 101, 8, 8));
            s.fillOval(-78, -47, 79, 33);
            s.fillOval(-7, -20, 73, 31);
            s.setColor(rgb(109, 176, 93));
            s.fillOval(-31, -116, 61, 67);
            s.fillOval(-75, -79, 67, 60);
            s.fillOval(9, -78, 67, 60);
            s.fillOval(-30, -43, 60, 57);
            s.setColor(rgb(52, 115, 63));
            s.fillOval(-8, -52, 16, 13);
            s.dispose();
        }

        private void paintFlower(Graphics2D g, double x, double y, double scale) {
            Graphics2D s = (Graphics2D) g.create();
            s.translate(x, y); s.scale(scale, scale);
            s.setColor(rgb(58, 121, 65));
            s.fill(new RoundRectangle2D.Double(-5, -7, 11, 95, 8, 8));
            s.fillOval(-60, 40, 62, 27);
            s.fillOval(5, 32, 57, 24);
            for (int i = 0; i < 12; i++) {
                AffineTransform old = s.getTransform();
                s.rotate(Math.PI * 2 * i / 12);
                s.setColor(i % 2 == 0 ? rgb(244, 180, 77) : rgb(254, 201, 101));
                s.fill(new Ellipse2D.Double(-15, -103, 30, 66));
                s.setTransform(old);
            }
            s.setColor(rgb(111, 81, 47));
            s.fillOval(-40, -40, 80, 80);
            s.setColor(rgb(179, 127, 54));
            for (int r = -25; r <= 25; r += 13) {
                for (int c = -25; c <= 25; c += 13) {
                    if (r*r + c*c < 950) s.fillOval(c + (r % 3), r, 5, 5);
                }
            }
            s.dispose();
        }

        private void paintTree(Graphics2D g, double x, double y, double scale) {
            Graphics2D s = (Graphics2D) g.create();
            s.translate(x, y); s.scale(scale, scale);
            s.setColor(rgb(119, 91, 63));
            s.fill(new RoundRectangle2D.Double(-13, -36, 27, 125, 9, 9));
            s.setColor(rgb(82, 147, 81));
            s.fillOval(-94, -114, 126, 109);
            s.fillOval(-38, -148, 112, 128);
            s.fillOval(-8, -103, 106, 103);
            s.setColor(rgb(225, 109, 71));
            s.fillOval(-58, -54, 25, 25);
            s.fillOval(40, -83, 24, 24);
            s.fillOval(12, -29, 25, 25);
            s.fillOval(-20, -112, 25, 25);
            s.dispose();
        }

        private void pill(Graphics2D g, int x, int y, int w, int h, String label, Color fill, Color color, int size) {
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
            center(g, label, x + w / 2, y + h / 2 + size * .36, size, Font.BOLD, color);
        }

        private void button(Graphics2D g, String id, int x, int y, int w, int h,
                String label, Color background, Color foreground, boolean enabled, int fontSize) {
            boolean isHover = enabled && !id.isEmpty() && id.equals(hovered);
            Color fill = background;
            if (isHover) {
                fill = new Color(Math.max(0, background.getRed() - 10),
                        Math.max(0, background.getGreen() - 10), Math.max(0, background.getBlue() - 10));
            }
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Double(x, y, w, h, 18, 18));
            center(g, label, x + w / 2 - (id.equals("partner") ? 10 : 0),
                    y + h / 2 + fontSize * 0.37, fontSize, Font.BOLD, foreground);
            if (enabled && !id.isEmpty()) zones.add(new Zone(id, x, y, w, h, 18));
        }

        private void arrow(Graphics2D g, int x, int y, Color color, double scale) {
            Graphics2D a = (Graphics2D) g.create();
            a.translate(x, y); a.scale(scale, scale);
            a.setColor(color);
            a.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            a.drawLine(-8, 0, 8, 0);
            a.drawLine(2, -6, 8, 0);
            a.drawLine(2, 6, 8, 0);
            a.dispose();
        }

        private static void text(Graphics2D g, String label, double x, double baseline, int size, int style, Color color) {
            g.setFont(new Font("Dialog", style, size));
            g.setColor(color);
            g.drawString(label, (float)x, (float)baseline);
        }
        private static void center(Graphics2D g, String label, double x, double baseline, int size, int style, Color color) {
            g.setFont(new Font("Dialog", style, size));
            double w = g.getFontMetrics().stringWidth(label);
            text(g, label, x - w / 2, baseline, size, style, color);
        }
        private void paintToast(Graphics2D g) {
            g.setFont(new Font("Dialog", Font.BOLD, 14));
            int width = Math.min(560, Math.max(265, g.getFontMetrics().stringWidth(flashMessage) + 46));
            g.setColor(DARK);
            g.fill(new RoundRectangle2D.Double((W - width) / 2., 655, width, 43, 19, 19));
            center(g, flashMessage, W / 2., 681, 14, Font.BOLD, Color.WHITE);
        }
    }
}
