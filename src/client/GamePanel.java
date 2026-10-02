package client;

import common.Constants;
import common.GameModels.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GamePanel extends JPanel implements ActionListener, KeyListener, MouseListener, MouseMotionListener {
    private final ClientGameState gameState;
    private final ClientNetworkManager networkManager;
    private final Timer renderTimer;

    // Phím đang bấm
    private final Set<Integer> pressedKeys = new HashSet<>();
    private int mouseScreenX = 0;
    private int mouseScreenY = 0;

    // Tọa độ Camera
    private float camX = 0;
    private float camY = 0;

    // Hiệu ứng hoạt họa (Animation ticks)
    private float animTick = 0;

    public GamePanel(ClientGameState gameState, ClientNetworkManager networkManager) {
        this.gameState = gameState;
        this.networkManager = networkManager;

        setFocusable(true);
        setBackground(new Color(24, 30, 26));

        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        // Vòng lặp vẽ 60 FPS (~16ms)
        renderTimer = new Timer(16, this);
        renderTimer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        animTick += 0.05f;
        updateLocalPlayerMovement();
        repaint();
    }

    private void updateLocalPlayerMovement() {
        PlayerState p = gameState.getMyPlayer();
        if (p == null || p.isDead) return;

        float moveX = 0;
        float moveY = 0;

        if (pressedKeys.contains(KeyEvent.VK_W) || pressedKeys.contains(KeyEvent.VK_UP)) moveY -= 1;
        if (pressedKeys.contains(KeyEvent.VK_S) || pressedKeys.contains(KeyEvent.VK_DOWN)) moveY += 1;
        if (pressedKeys.contains(KeyEvent.VK_A) || pressedKeys.contains(KeyEvent.VK_LEFT)) moveX -= 1;
        if (pressedKeys.contains(KeyEvent.VK_D) || pressedKeys.contains(KeyEvent.VK_RIGHT)) moveX += 1;

        if (moveX != 0 && moveY != 0) {
            float len = (float) Math.sqrt(moveX * moveX + moveY * moveY);
            moveX /= len;
            moveY /= len;
        }

        float speed = Constants.BASE_PLAYER_SPEED * p.speedMultiplier;
        p.vx = moveX * speed;
        p.vy = moveY * speed;

        // Góc quay theo chuột
        float worldMouseX = mouseScreenX + camX;
        float worldMouseY = mouseScreenY + camY;
        p.angle = (float) Math.atan2(worldMouseY - p.y, worldMouseX - p.x);

        // Cập nhật vị trí dự đoán trên Client (Client-side prediction)
        float dt = 0.016f;
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.x = Math.max(50, Math.min(Constants.MAP_WIDTH - 50, p.x));
        p.y = Math.max(50, Math.min(Constants.MAP_HEIGHT - 50, p.y));

        // Cập nhật Camera theo mượt mà
        float targetCamX = p.x - getWidth() / 2f;
        float targetCamY = p.y - getHeight() / 2f;
        camX += (targetCamX - camX) * 0.12f;
        camY += (targetCamY - camY) * 0.12f;

        camX = Math.max(0, Math.min(Constants.MAP_WIDTH - getWidth(), camX));
        camY = Math.max(0, Math.min(Constants.MAP_HEIGHT - getHeight(), camY));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 1. Dời gốc tọa độ theo Camera
        AffineTransform oldTransform = g2.getTransform();
        g2.translate(-camX, -camY);

        // 2. Vẽ Thế Giới Game 2D
        drawWorldMap(g2);
        drawFloodZone(g2);
        drawShrineAndBases(g2);
        drawDroppedTributes(g2);
        drawMonsters(g2);
        drawPlayers(g2);
        drawProjectiles(g2);

        // Phục hồi transform để vẽ HUD cố định trên màn hình
        g2.setTransform(oldTransform);

        // 3. Vẽ In-Game HUD
        drawHUD(g2);
        drawMiniMap(g2);
        drawCombatFeed(g2);
        drawVictoryOverlay(g2);
    }

    /**
     * 1. Bản đồ Rừng Thiêng và Đất Cổ Văn Lang
     */
    private void drawWorldMap(Graphics2D g2) {
        // Nền cỏ rừng xanh sẫm
        g2.setColor(new Color(28, 48, 32));
        g2.fillRect(0, 0, Constants.MAP_WIDTH, Constants.MAP_HEIGHT);

        // Các mảng đá và đường mòn đất cổ
        g2.setColor(new Color(42, 58, 44));
        for (int x = 200; x < Constants.MAP_WIDTH; x += 400) {
            for (int y = 300; y < Constants.MAP_HEIGHT; y += 400) {
                g2.fillOval(x, y, 260, 200);
            }
        }

        // Lưới phong thủy / ranh giới đất trời mờ ảo
        g2.setColor(new Color(255, 255, 255, 8));
        for (int x = 0; x < Constants.MAP_WIDTH; x += 150) {
            g2.drawLine(x, 0, x, Constants.MAP_HEIGHT);
        }
        for (int y = 0; y < Constants.MAP_HEIGHT; y += 150) {
            g2.drawLine(0, y, Constants.MAP_WIDTH, y);
        }

        // Viền bản đồ cổ kính
        g2.setColor(new Color(192, 142, 60));
        g2.setStroke(new BasicStroke(16));
        g2.drawRect(8, 8, Constants.MAP_WIDTH - 16, Constants.MAP_HEIGHT - 16);
    }

    /**
     * 2. Vùng nước lũ dâng trào do Thủy Tinh hô hoán
     */
    private void drawFloodZone(Graphics2D g2) {
        if (!gameState.floodZone.isActive) return;

        float r = gameState.floodZone.radius;
        float cx = gameState.floodZone.centerX;
        float cy = gameState.floodZone.centerY;

        // Vùng nước ngập màu lam ngọc cuộn sóng
        RadialGradientPaint floodGrad = new RadialGradientPaint(
            new Point2D.Float(cx, cy), r,
            new float[]{0.0f, 0.7f, 1.0f},
            new Color[]{new Color(30, 144, 255, 160), new Color(0, 105, 148, 120), new Color(0, 70, 120, 0)}
        );
        g2.setPaint(floodGrad);
        g2.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));

        // Các gợn sóng lan tỏa
        g2.setColor(new Color(175, 238, 238, 180));
        g2.setStroke(new BasicStroke(3));
        float waveR = (r * 0.4f) + ((animTick * 40) % (r * 0.6f));
        g2.draw(new Ellipse2D.Float(cx - waveR, cy - waveR, waveR * 2, waveR * 2));

        // Chữ cảnh báo nước ngập
        g2.setColor(new Color(220, 245, 255));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
        g2.drawString("⚠️ THỦY TRIỀU BÃO LŨ DÂNG TRÀO!", cx - 140, cy - r + 30);
    }

    /**
     * 3. Cung Điện Vua Hùng & Căn cứ 2 phe
     */
    private void drawShrineAndBases(Graphics2D g2) {
        // --- A. CUNG ĐIỆN VUA HÙNG (NỘP SÍNH LỄ) ---
        int sx = Constants.SHRINE_X;
        int sy = Constants.SHRINE_Y;
        int sr = Constants.SHRINE_RADIUS;

        // Vòng hào quang Thánh Điện Vua Hùng
        float pulse = (float) Math.sin(animTick * 2) * 12;
        RadialGradientPaint shrineGlow = new RadialGradientPaint(
            new Point2D.Float(sx, sy), sr + pulse + 20,
            new float[]{0.0f, 0.6f, 1.0f},
            new Color[]{new Color(255, 215, 0, 140), new Color(218, 165, 32, 70), new Color(255, 255, 255, 0)}
        );
        g2.setPaint(shrineGlow);
        g2.fill(new Ellipse2D.Float(sx - (sr + pulse), sy - (sr + pulse), (sr + pulse) * 2, (sr + pulse) * 2));

        // Nền đá tế lễ hoa văn trống đồng
        g2.setColor(new Color(110, 80, 50));
        g2.fillOval(sx - sr, sy - sr, sr * 2, sr * 2);
        g2.setColor(new Color(241, 196, 15));
        g2.setStroke(new BasicStroke(5));
        g2.drawOval(sx - sr, sy - sr, sr * 2, sr * 2);
        g2.drawOval(sx - (sr - 30), sy - (sr - 30), (sr - 30) * 2, (sr - 30) * 2);

        // Ngai Vàng Vua Hùng
        g2.setColor(new Color(180, 130, 20));
        g2.fillRoundRect(sx - 45, sy - 50, 90, 70, 16, 16);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.drawString("👑 VUA HÙNG", sx - 48, sy - 15);
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2.setColor(new Color(255, 240, 180));
        g2.drawString("Nộp 3 Sính Lễ", sx - 38, sy + 6);

        // --- B. CĂN CỨ SƠN TINH (TÂY NAM) ---
        int bx1 = Constants.SPAWN_SON_TINH_X;
        int by1 = Constants.SPAWN_SON_TINH_Y;
        g2.setColor(new Color(211, 84, 0, 70));
        g2.fillOval(bx1 - 100, by1 - 100, 200, 200);
        g2.setColor(new Color(230, 126, 34));
        g2.setStroke(new BasicStroke(4));
        g2.drawOval(bx1 - 100, by1 - 100, 200, 200);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g2.drawString("⛰️ BẢN DOANH SƠN TINH", bx1 - 85, by1 - 110);

        // --- C. CĂN CỨ THỦY TINH (ĐÔNG NAM) ---
        int bx2 = Constants.SPAWN_THUY_TINH_X;
        int by2 = Constants.SPAWN_THUY_TINH_Y;
        g2.setColor(new Color(41, 128, 185, 70));
        g2.fillOval(bx2 - 100, by2 - 100, 200, 200);
        g2.setColor(new Color(52, 152, 219));
        g2.setStroke(new BasicStroke(4));
        g2.drawOval(bx2 - 100, by2 - 100, 200, 200);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g2.drawString("🌊 THỦY ĐỘNG THỦY TINH", bx2 - 90, by2 - 110);
    }

    /**
     * 4. Sính Lễ rơi trên mặt đất (Phát sáng rực rỡ)
     */
    private void drawDroppedTributes(Graphics2D g2) {
        for (TributeItem item : gameState.tributes) {
            if (item.isDelivered || item.carriedByPlayerId != -1 || item.x < 0) continue;

            float tx = item.x;
            float ty = item.y;
            float bob = (float) Math.sin(animTick * 4) * 6;

            // Vầng hào quang bảo vật
            Color itemColor = new Color(item.type.getColorRgb());
            g2.setColor(new Color(itemColor.getRed(), itemColor.getGreen(), itemColor.getBlue(), 90));
            g2.fillOval((int) (tx - 35), (int) (ty - 35 + bob), 70, 70);

            // Icon Hộp Bảo Vật
            g2.setColor(itemColor);
            g2.fillRoundRect((int) (tx - 18), (int) (ty - 18 + bob), 36, 36, 8, 8);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2));
            g2.drawRoundRect((int) (tx - 18), (int) (ty - 18 + bob), 36, 36, 8, 8);

            // Chữ tên sính lễ
            g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g2.setColor(Color.YELLOW);
            String label = "✨ " + item.type.getDisplayName();
            int strW = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, tx - strW / 2f, ty - 26 + bob);

            g2.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            g2.setColor(Color.WHITE);
            g2.drawString("[Phím E/Space để Nhặt]", tx - 56, ty + 34 + bob);
        }
    }

    /**
     * 5. Quái Thú / Boss Trấn Giữ Sính Lễ
     */
    private void drawMonsters(Graphics2D g2) {
        for (MonsterState m : gameState.monsters) {
            if (!m.isAlive) continue;

            float mx = m.x;
            float my = m.y;

            // Thân quái vật
            Color bossColor = switch (m.id) {
                case 1 -> new Color(230, 200, 160); // Bạch Tượng (Trắng ngà)
                case 2 -> new Color(220, 50, 40);   // Hỏa Kê (Đỏ lửa)
                default -> new Color(170, 70, 190); // Xích Ký (Tím hồng)
            };

            // Vòng nguy hiểm quanh Boss
            g2.setColor(new Color(bossColor.getRed(), bossColor.getGreen(), bossColor.getBlue(), 40));
            g2.fillOval((int) (mx - 60), (int) (my - 60), 120, 120);

            // Hình dáng Boss
            g2.setColor(bossColor);
            g2.fillOval((int) (mx - 32), (int) (my - 32), 64, 64);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(3));
            g2.drawOval((int) (mx - 32), (int) (my - 32), 64, 64);

            // Tên Boss
            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g2.setColor(Color.WHITE);
            String bName = "👹 " + m.name;
            int nameW = g2.getFontMetrics().stringWidth(bName);
            g2.drawString(bName, mx - nameW / 2f, my - 46);

            // Sính lễ canh giữ
            if (m.guardedTribute != null) {
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g2.setColor(new Color(255, 215, 0));
                g2.drawString("Giữ: " + m.guardedTribute.getDisplayName(), mx - 45, my - 34);
            }

            // Thanh Máu Boss (HP Bar)
            int barW = 70;
            int barH = 7;
            float hpRatio = Math.max(0, (float) m.hp / m.maxHp);
            g2.setColor(new Color(40, 40, 40));
            g2.fillRect((int) (mx - barW / 2), (int) (my + 36), barW, barH);
            g2.setColor(new Color(231, 76, 60));
            g2.fillRect((int) (mx - barW / 2), (int) (my + 36), (int) (barW * hpRatio), barH);
            g2.setColor(Color.WHITE);
            g2.drawRect((int) (mx - barW / 2), (int) (my + 36), barW, barH);
        }
    }

    /**
     * 6. Người chơi (Sơn Tinh, Thủy Tinh, Hộ Vệ)
     */
    private void drawPlayers(Graphics2D g2) {
        for (PlayerState p : gameState.players.values()) {
            float px = p.x;
            float py = p.y;

            if (p.isDead) {
                // Hiển thị hồn ma chờ hồi sinh
                g2.setColor(new Color(200, 200, 200, 90));
                g2.fillOval((int) (px - 20), (int) (py - 20), 40, 40);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                g2.drawString("Hồi sinh: " + Math.max(0, (int) p.respawnTimer + 1) + "s", px - 35, py - 28);
                continue;
            }

            // Vòng tròn hướng nhìn / ngắm bắn
            float barrelX = px + (float) Math.cos(p.angle) * 32;
            float barrelY = py + (float) Math.sin(p.angle) * 32;
            g2.setColor(p.team == Team.SON_TINH ? new Color(243, 156, 18) : new Color(52, 152, 219));
            g2.setStroke(new BasicStroke(4));
            g2.drawLine((int) px, (int) py, (int) barrelX, (int) barrelY);

            // Thân nhân vật
            Color teamColor = (p.team == Team.SON_TINH) ? new Color(211, 84, 0) : new Color(41, 128, 185);
            g2.setColor(teamColor);
            g2.fillOval((int) (px - 22), (int) (py - 22), 44, 44);

            // Viền nhân vật (Nếu là nhân vật của mình thì viền vàng sáng)
            if (p.id == gameState.myPlayerId) {
                g2.setColor(new Color(255, 235, 59));
                g2.setStroke(new BasicStroke(3));
            } else {
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.5f));
            }
            g2.drawOval((int) (px - 22), (int) (py - 22), 44, 44);

            // Icon Balo sính lễ bay trên đầu nếu đang mang sính lễ
            if (!p.backpack.isEmpty()) {
                float bagBob = (float) Math.sin(animTick * 6) * 4;
                g2.setColor(new Color(241, 196, 15));
                g2.fillRoundRect((int) (px - 14), (int) (py - 52 + bagBob), 28, 20, 6, 6);
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.drawString("🎒" + p.backpack.size(), px - 11, py - 38 + bagBob);
            }

            // Tên và vai trò
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.setColor(p.id == gameState.myPlayerId ? new Color(255, 235, 59) : Color.WHITE);
            String title = p.username + " (" + (p.team == Team.SON_TINH ? "Sơn" : "Thủy") + ")";
            int tw = g2.getFontMetrics().stringWidth(title);
            g2.drawString(title, px - tw / 2f, py - 26);

            // Thanh Máu HP Player
            int pBarW = 46;
            int pBarH = 5;
            float pRatio = Math.max(0, (float) p.hp / p.maxHp);
            g2.setColor(new Color(30, 30, 30));
            g2.fillRect((int) (px - pBarW / 2), (int) (py + 26), pBarW, pBarH);
            g2.setColor(new Color(46, 204, 113));
            g2.fillRect((int) (px - pBarW / 2), (int) (py + 26), (int) (pBarW * pRatio), pBarH);
        }
    }

    /**
     * 7. Đạn & Kỹ năng bay trên không gian
     */
    private void drawProjectiles(Graphics2D g2) {
        for (ProjectileState proj : gameState.projectiles) {
            float px = proj.x;
            float py = proj.y;

            if (proj.skillType == 1) {
                // Đạn Thần Thạch Sơn Tinh
                g2.setColor(new Color(230, 126, 34));
                g2.fillOval((int) (px - 8), (int) (py - 8), 16, 16);
                g2.setColor(Color.WHITE);
                g2.fillOval((int) (px - 3), (int) (py - 3), 6, 6);
            } else {
                // Thủy Kích Thủy Tinh
                g2.setColor(new Color(0, 191, 255));
                g2.fillOval((int) (px - 8), (int) (py - 8), 16, 16);
                g2.setColor(new Color(224, 255, 255));
                g2.fillOval((int) (px - 3), (int) (py - 3), 6, 6);
            }
        }
    }

    /**
     * 8. In-Game HUD: Avatar, HP/Mana, Tỉ số Nộp Lễ, Balo Sính Lễ
     */
    private void drawHUD(Graphics2D g2) {
        PlayerState me = gameState.getMyPlayer();

        // --- A. BẢNG TỈ SỐ NỘP SÍNH LỄ VUA HÙNG (TOP CENTER) ---
        int topW = 460;
        int topH = 64;
        int topX = (getWidth() - topW) / 2;
        int topY = 16;

        g2.setColor(new Color(15, 20, 25, 220));
        g2.fillRoundRect(topX, topY, topW, topH, 16, 16);
        g2.setColor(new Color(218, 165, 32));
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(topX, topY, topW, topH, 16, 16);

        // Bên Sơn Tinh
        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.setColor(new Color(230, 126, 34));
        g2.drawString("SƠN TINH: " + gameState.scoreSonTinh + "/3", topX + 24, topY + 28);

        // VS Icon
        g2.setColor(new Color(255, 215, 0));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
        g2.drawString("⚔️", topX + topW / 2 - 14, topY + 36);

        // Bên Thủy Tinh
        g2.setColor(new Color(52, 152, 219));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.drawString("THỦY TINH: " + gameState.scoreThuyTinh + "/3", topX + topW - 165, topY + 28);

        g2.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        g2.setColor(new Color(200, 200, 200));
        g2.drawString("Đội nào dâng đủ 3 Sính Lễ trước sẽ trở thành Phò mã!", topX + 68, topY + 52);

        // --- B. THÔNG TIN BẢN THÂN: HP & MANA (TOP LEFT) ---
        int leftW = 280;
        int leftH = 90;
        g2.setColor(new Color(15, 20, 25, 210));
        g2.fillRoundRect(16, 16, leftW, leftH, 14, 14);
        g2.setColor(new Color(100, 100, 100));
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(16, 16, leftW, leftH, 14, 14);

        if (me != null) {
            g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g2.setColor(me.team == Team.SON_TINH ? new Color(230, 126, 34) : new Color(52, 152, 219));
            g2.drawString(me.username + " (" + me.roleName + ")", 28, 38);

            // HP Bar
            g2.setColor(new Color(40, 40, 40));
            g2.fillRect(28, 48, 240, 14);
            float hpRatio = Math.max(0, (float) me.hp / me.maxHp);
            g2.setColor(new Color(46, 204, 113));
            g2.fillRect(28, 48, (int) (240 * hpRatio), 14);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.drawString("HP: " + me.hp + " / " + me.maxHp, 110, 59);

            // Mana Bar
            g2.setColor(new Color(40, 40, 40));
            g2.fillRect(28, 68, 240, 10);
            float manaRatio = Math.max(0, (float) me.mana / me.maxMana);
            g2.setColor(new Color(52, 152, 219));
            g2.fillRect(28, 68, (int) (240 * manaRatio), 10);
            g2.setColor(Color.WHITE);
            g2.drawString("NỘI LỰC: " + me.mana + " / " + me.maxMana, 100, 77);

            // Cảnh báo debuff tốc độ nếu mang sính lễ
            if (!me.backpack.isEmpty()) {
                g2.setColor(new Color(231, 76, 60));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                int debuffPct = (int) ((1.0f - me.speedMultiplier) * 100);
                g2.drawString("⚠️ GÁNH NẶNG SÍNH LỄ: -" + debuffPct + "% TỐC ĐỘ DI CHUYỂN", 16, 128);
            }
        }

        // --- C. BALO SÍNH LỄ & PHÍM KỸ NĂNG (BOTTOM CENTER) ---
        int botW = 540;
        int botH = 80;
        int botX = (getWidth() - botW) / 2;
        int botY = getHeight() - botH - 18;

        g2.setColor(new Color(15, 20, 25, 220));
        g2.fillRoundRect(botX, botY, botW, botH, 16, 16);
        g2.setColor(new Color(218, 165, 32));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(botX, botY, botW, botH, 16, 16);

        // 3 Ô Sính Lễ
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.setColor(Color.WHITE);
        g2.drawString("BALO SÍNH LỄ:", botX + 16, botY + 24);

        TributeType[] allTypes = {TributeType.VOI_CHIN_NGA, TributeType.GA_CHIN_CUA, TributeType.NGUA_CHIN_HONG_MAO};
        for (int i = 0; i < 3; i++) {
            TributeType t = allTypes[i];
            boolean hasIt = (me != null && me.backpack.contains(t));

            int slotX = botX + 16 + i * 85;
            int slotY = botY + 32;

            if (hasIt) {
                g2.setColor(new Color(t.getColorRgb()));
                g2.fillRoundRect(slotX, slotY, 78, 36, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(slotX, slotY, 78, 36, 8, 8);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                g2.drawString("✅ " + t.getDisplayName().substring(0, Math.min(8, t.getDisplayName().length())), slotX + 4, slotY + 22);
            } else {
                g2.setColor(new Color(40, 45, 50));
                g2.fillRoundRect(slotX, slotY, 78, 36, 8, 8);
                g2.setColor(new Color(80, 80, 80));
                g2.drawRoundRect(slotX, slotY, 78, 36, 8, 8);
                g2.setColor(new Color(130, 130, 130));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                g2.drawString(t.getDisplayName().substring(0, Math.min(8, t.getDisplayName().length())), slotX + 8, slotY + 22);
            }
        }

        // Các phím chức năng bên phải
        int skillX = botX + 290;
        drawSkillKey(g2, skillX, botY + 18, "L-Click", "Bắn Phép");
        drawSkillKey(g2, skillX + 75, botY + 18, "R-Click", (me != null && me.team == Team.SON_TINH) ? "Dời Non" : "Bão Lũ");
        drawSkillKey(g2, skillX + 160, botY + 18, "E/Space", "Nhặt/Nộp Lễ");
    }

    private void drawSkillKey(Graphics2D g2, int x, int y, String key, String action) {
        g2.setColor(new Color(35, 40, 48));
        g2.fillRoundRect(x, y, 68, 50, 8, 8);
        g2.setColor(new Color(218, 165, 32));
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(x, y, 68, 50, 8, 8);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.setColor(new Color(255, 215, 0));
        g2.drawString(key, x + 10, y + 20);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.setColor(Color.WHITE);
        g2.drawString(action, x + 6, y + 40);
    }

    /**
     * 9. Mini-Map ở góc phải trên
     */
    private void drawMiniMap(Graphics2D g2) {
        int mmW = 180;
        int mmH = 140;
        int mmX = getWidth() - mmW - 16;
        int mmY = 16;

        g2.setColor(new Color(15, 20, 25, 220));
        g2.fillRoundRect(mmX, mmY, mmW, mmH, 10, 10);
        g2.setColor(new Color(218, 165, 32));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(mmX, mmY, mmW, mmH, 10, 10);

        float scaleX = (float) mmW / Constants.MAP_WIDTH;
        float scaleY = (float) mmH / Constants.MAP_HEIGHT;

        // Vị trí Cung Điện Vua Hùng
        int sx = mmX + (int) (Constants.SHRINE_X * scaleX);
        int sy = mmY + (int) (Constants.SHRINE_Y * scaleY);
        g2.setColor(Color.YELLOW);
        g2.fillOval(sx - 4, sy - 4, 8, 8);

        // Vị trí Sính Lễ rơi
        for (TributeItem item : gameState.tributes) {
            if (!item.isDelivered && item.carriedByPlayerId == -1 && item.x > 0) {
                int ix = mmX + (int) (item.x * scaleX);
                int iy = mmY + (int) (item.y * scaleY);
                g2.setColor(Color.CYAN);
                g2.fillOval(ix - 3, iy - 3, 6, 6);
            }
        }

        // Vị trí Boss
        for (MonsterState m : gameState.monsters) {
            if (m.isAlive) {
                int mx = mmX + (int) (m.x * scaleX);
                int my = mmY + (int) (m.y * scaleY);
                g2.setColor(new Color(231, 76, 60));
                g2.fillRect(mx - 3, my - 3, 6, 6);
            }
        }

        // Vị trí Players
        for (PlayerState p : gameState.players.values()) {
            if (p.isDead) continue;
            int px = mmX + (int) (p.x * scaleX);
            int py = mmY + (int) (p.y * scaleY);

            if (p.id == gameState.myPlayerId) {
                g2.setColor(Color.GREEN);
                g2.fillOval(px - 4, py - 4, 8, 8);
            } else if (p.team == gameState.myTeam) {
                g2.setColor(Color.ORANGE);
                g2.fillOval(px - 3, py - 3, 6, 6);
            } else {
                g2.setColor(Color.RED);
                g2.fillOval(px - 3, py - 3, 6, 6);
            }
        }
    }

    /**
     * 10. Combat Feed: Lịch sử sự kiện chiến trường
     */
    private void drawCombatFeed(Graphics2D g2) {
        int feedX = 16;
        int feedY = getHeight() - 120;
        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));

        List<String> logs = gameState.combatLogs;
        for (int i = 0; i < logs.size(); i++) {
            String log = logs.get(i);
            g2.setColor(new Color(0, 0, 0, 160));
            int textW = g2.getFontMetrics().stringWidth(log);
            g2.fillRect(feedX, feedY - (logs.size() - i) * 22, textW + 16, 20);

            g2.setColor(new Color(255, 235, 150));
            g2.drawString(log, feedX + 8, feedY - (logs.size() - i) * 22 + 15);
        }
    }

    /**
     * 11. Màn hình Kết Thúc / Lời Phán Vua Hùng
     */
    private void drawVictoryOverlay(Graphics2D g2) {
        if (!gameState.isGameOver) return;

        g2.setColor(new Color(0, 0, 0, 210));
        g2.fillRect(0, 0, getWidth(), getHeight());

        int cw = 600;
        int ch = 340;
        int cx = (getWidth() - cw) / 2;
        int cy = (getHeight() - ch) / 2;

        g2.setColor(new Color(25, 30, 40));
        g2.fillRoundRect(cx, cy, cw, ch, 24, 24);
        g2.setColor(new Color(241, 196, 15));
        g2.setStroke(new BasicStroke(4));
        g2.drawRoundRect(cx, cy, cw, ch, 24, 24);

        boolean iWon = (gameState.winningTeam == gameState.myTeam);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 32));
        g2.setColor(iWon ? new Color(46, 204, 113) : new Color(231, 76, 60));
        String head = iWon ? "🏆 ĐẠI THẮNG QUANG VINH!" : "⚔️ CHIẾN TRẬN KẾT THÚC";
        int hw = g2.getFontMetrics().stringWidth(head);
        g2.drawString(head, cx + (cw - hw) / 2, cy + 60);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
        g2.setColor(Color.WHITE);
        String victorText = (gameState.winningTeam == Team.SON_TINH) ? "PHE SƠN TINH ĐÃ TRỞ THÀNH PHÒ MÃ!" : "PHE THỦY TINH ĐÃ TRỞ THÀNH PHÒ MÃ!";
        int vw = g2.getFontMetrics().stringWidth(victorText);
        g2.drawString(victorText, cx + (cw - vw) / 2, cy + 110);

        g2.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        g2.setColor(new Color(255, 230, 160));
        String decree1 = "Vua Hùng truyền lệnh: Tam sính lễ đã dâng đủ, kết giới thần bảo vệ ngàn năm.";
        String decree2 = "Dũng khí và sự đồng lòng của các dũng sĩ sẽ được lưu danh sử sách!";
        g2.drawString(decree1, cx + (cw - g2.getFontMetrics().stringWidth(decree1)) / 2, cy + 160);
        g2.drawString(decree2, cx + (cw - g2.getFontMetrics().stringWidth(decree2)) / 2, cy + 190);

        g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g2.setColor(new Color(230, 126, 34));
        g2.drawString("Sơn Tinh: " + gameState.scoreSonTinh + " Sính Lễ", cx + 120, cy + 245);
        g2.setColor(new Color(52, 152, 219));
        g2.drawString("Thủy Tinh: " + gameState.scoreThuyTinh + " Sính Lễ", cx + 330, cy + 245);
    }

    // =========================================================================
    // XỬ LÝ SỰ KIỆN BÀN PHÍM VÀ CHUỘT
    // =========================================================================
    @Override
    public void keyPressed(KeyEvent e) {
        pressedKeys.add(e.getKeyCode());

        if (e.getKeyCode() == KeyEvent.VK_E || e.getKeyCode() == KeyEvent.VK_SPACE) {
            networkManager.sendInteract();
        } else if (e.getKeyCode() == KeyEvent.VK_J) {
            PlayerState me = gameState.getMyPlayer();
            if (me != null) networkManager.sendAttack(me.angle);
        } else if (e.getKeyCode() == KeyEvent.VK_K) {
            networkManager.sendSpecialSkill();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        pressedKeys.remove(e.getKeyCode());
    }

    @Override
    public void mousePressed(MouseEvent e) {
        PlayerState me = gameState.getMyPlayer();
        if (me == null || me.isDead) return;

        if (SwingUtilities.isLeftMouseButton(e)) {
            // Tấn công thường
            networkManager.sendAttack(me.angle);
        } else if (SwingUtilities.isRightMouseButton(e)) {
            // Kỹ năng tối thượng
            networkManager.sendSpecialSkill();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseScreenX = e.getX();
        mouseScreenY = e.getY();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseScreenX = e.getX();
        mouseScreenY = e.getY();
    }

    @Override public void keyTyped(KeyEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
}
