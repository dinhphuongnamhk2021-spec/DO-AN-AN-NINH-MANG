package gameserver;

import common.Constants;
import common.CustomPacket;
import common.GameModels.*;
import common.PacketType;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameRoom {
    public static class ClientSession {
        public int playerId;
        public String username;
        public Team team;
        public String roleName;
        public boolean isBot;
        public Socket tcpSocket;
        public DataOutputStream tcpOut;
        public InetAddress udpAddress;
        public int udpPort = -1;

        public ClientSession(int playerId, String username, Team team, String roleName, boolean isBot) {
            this.playerId = playerId;
            this.username = username;
            this.team = team;
            this.roleName = roleName;
            this.isBot = isBot;
        }

        public synchronized void sendTCP(CustomPacket packet) {
            if (tcpOut != null && tcpSocket != null && !tcpSocket.isClosed()) {
                try {
                    tcpOut.write(packet.toBytes());
                    tcpOut.flush();
                } catch (IOException ignored) {}
            }
        }
    }

    private final String roomToken;
    private final Map<Integer, ClientSession> sessions = new ConcurrentHashMap<>();
    private final Map<Integer, PlayerState> players = new ConcurrentHashMap<>();
    private final List<MonsterState> monsters = new CopyOnWriteArrayList<>();
    private final List<TributeItem> tributes = new CopyOnWriteArrayList<>();
    private final List<ProjectileState> projectiles = new CopyOnWriteArrayList<>();
    private final FloodZone floodZone = new FloodZone();

    private int nextProjectileId = 1;
    private int scoreSonTinh = 0; // Số sính lễ phe Sơn Tinh đã nộp
    private int scoreThuyTinh = 0; // Số sính lễ phe Thủy Tinh đã nộp
    private volatile boolean isGameOver = false;
    private Team winningTeam = null;

    private ScheduledExecutorService gameLoopExecutor;
    private DatagramSocket udpSocket;

    public GameRoom(String roomToken, DatagramSocket udpSocket) {
        this.roomToken = roomToken;
        this.udpSocket = udpSocket;
        initWorldEntities();
    }

    private void initWorldEntities() {
        // Khởi tạo 3 Boss trấn giữ sính lễ
        monsters.add(new MonsterState(1, "Bạch Tượng Thần", TributeType.VOI_CHIN_NGA, 
            Constants.BOSS_VOI_X, Constants.BOSS_VOI_Y, 800));

        monsters.add(new MonsterState(2, "Hỏa Kê Thần", TributeType.GA_CHIN_CUA, 
            Constants.BOSS_GA_X, Constants.BOSS_GA_Y, 650));

        monsters.add(new MonsterState(3, "Xích Ký Thần", TributeType.NGUA_CHIN_HONG_MAO, 
            Constants.BOSS_NGUA_X, Constants.BOSS_NGUA_Y, 750));

        // Ban đầu sính lễ chưa rơi trên đất (được giữ bởi Boss)
        tributes.add(new TributeItem(1, TributeType.VOI_CHIN_NGA, -1000, -1000));
        tributes.add(new TributeItem(2, TributeType.GA_CHIN_CUA, -1000, -1000));
        tributes.add(new TributeItem(3, TributeType.NGUA_CHIN_HONG_MAO, -1000, -1000));
    }

    public synchronized void registerPlayer(int playerId, String username, Team team, String roleName, boolean isBot) {
        ClientSession session = new ClientSession(playerId, username, team, roleName, isBot);
        sessions.put(playerId, session);

        float spawnX = (team == Team.SON_TINH) ? Constants.SPAWN_SON_TINH_X + (playerId % 2) * 80 
                                               : Constants.SPAWN_THUY_TINH_X + (playerId % 2) * 80;
        float spawnY = (team == Team.SON_TINH) ? Constants.SPAWN_SON_TINH_Y + (playerId % 2) * 40 
                                               : Constants.SPAWN_THUY_TINH_Y + (playerId % 2) * 40;

        PlayerState ps = new PlayerState(playerId, username, team, roleName, spawnX, spawnY);
        players.put(playerId, ps);
    }

    public void bindPlayerTCP(int playerId, Socket socket, DataOutputStream out) {
        ClientSession session = sessions.get(playerId);
        if (session != null) {
            session.tcpSocket = socket;
            session.tcpOut = out;
        }
    }

    public void bindPlayerUDP(int playerId, InetAddress address, int port) {
        ClientSession session = sessions.get(playerId);
        if (session != null) {
            session.udpAddress = address;
            session.udpPort = port;
        }
    }

    public void startLoop() {
        if (gameLoopExecutor != null) return;
        gameLoopExecutor = Executors.newSingleThreadScheduledExecutor();
        gameLoopExecutor.scheduleAtFixedRate(this::tick, 0, 1000 / Constants.GAME_TICK_RATE, TimeUnit.MILLISECONDS);
        broadcastSystemChat("Trận chiến Cuộc Đua Sính Lễ bắt đầu! Hãy săn Boss hoặc cướp sính lễ dâng lên Vua Hùng!");
        broadcastInventorySync();
    }

    private void tick() {
        if (isGameOver) return;
        float dt = 1.0f / Constants.GAME_TICK_RATE;

        // 1. Cập nhật Player (Hồi sinh, hồi phục mana)
        for (PlayerState p : players.values()) {
            if (p.isDead) {
                p.respawnTimer -= dt;
                if (p.respawnTimer <= 0) {
                    p.isDead = false;
                    p.hp = p.maxHp;
                    p.x = (p.team == Team.SON_TINH) ? Constants.SPAWN_SON_TINH_X : Constants.SPAWN_THUY_TINH_X;
                    p.y = (p.team == Team.SON_TINH) ? Constants.SPAWN_SON_TINH_Y : Constants.SPAWN_THUY_TINH_Y;
                    broadcastSystemChat(p.username + " đã hồi sinh tại bản doanh!");
                }
            } else {
                // Hồi mana từ từ
                if (p.mana < p.maxMana) {
                    p.mana = Math.min(p.maxMana, p.mana + 1);
                }

                // Tính toán Debuff tốc độ do Gánh nặng sính lễ
                int tributeCount = p.backpack.size();
                float debuff = Math.min(Constants.MAX_SPEED_DEBUFF, tributeCount * Constants.SPEED_DEBUFF_PER_TRIBUTE);
                float speedMul = 1.0f - debuff;

                // Nếu đang trong vùng ngập lụt Thủy Tinh
                if (floodZone.isActive && isInFloodZone(p.x, p.y)) {
                    if (p.team == Team.SON_TINH) {
                        speedMul *= 0.65f; // Sơn Tinh bị chậm trong nước
                    } else {
                        speedMul *= 1.25f; // Thủy Tinh bơi nhanh hơn trong nước lũ
                    }
                }
                p.speedMultiplier = speedMul;

                // Cập nhật vị trí dựa vào vận tốc
                p.x += p.vx * dt;
                p.y += p.vy * dt;

                // Giới hạn trong bản đồ
                p.x = Math.max(50, Math.min(Constants.MAP_WIDTH - 50, p.x));
                p.y = Math.max(50, Math.min(Constants.MAP_HEIGHT - 50, p.y));
            }
        }

        // 2. AI Bot cập nhật hành vi
        updateBotAI(dt);

        // 3. Cập nhật AI Boss Quái Rừng
        updateMonstersAI(dt);

        // 4. Cập nhật Đạn bay & Va chạm (Projectiles)
        updateProjectiles(dt);

        // 5. Cập nhật Nước Lũ (Flood Zone)
        if (floodZone.isActive) {
            floodZone.waterLevel -= dt * 0.05f; // Kéo dài ~20s rồi rút bớt
            if (floodZone.waterLevel <= 0) {
                floodZone.isActive = false;
                floodZone.waterLevel = 0;
                broadcastSystemChat("Nước lũ đã rút dần!");
            }
        }

        // 6. Phát sóng UDP Snapshot vị trí thời gian thực cho tất cả Client
        broadcastUDPSnapshot();
    }

    private boolean isInFloodZone(float x, float y) {
        float dx = x - floodZone.centerX;
        float dy = y - floodZone.centerY;
        return (dx * dx + dy * dy) <= (floodZone.radius * floodZone.radius);
    }

    private void updateBotAI(float dt) {
        for (ClientSession session : sessions.values()) {
            if (!session.isBot) continue;
            PlayerState bot = players.get(session.playerId);
            if (bot == null || bot.isDead) continue;

            // Nếu bot đang giữ sính lễ -> Hướng thẳng về Cung Điện Vua Hùng để nộp!
            if (!bot.backpack.isEmpty()) {
                float dx = Constants.SHRINE_X - bot.x;
                float dy = Constants.SHRINE_Y - bot.y;
                float dist = (float) Math.hypot(dx, dy);

                if (dist > 80) {
                    bot.vx = (dx / dist) * Constants.BASE_PLAYER_SPEED * bot.speedMultiplier;
                    bot.vy = (dy / dist) * Constants.BASE_PLAYER_SPEED * bot.speedMultiplier;
                    bot.angle = (float) Math.atan2(dy, dx);
                } else {
                    bot.vx = 0;
                    bot.vy = 0;
                    handleInteract(bot.id); // Tự động nộp lễ
                }
                continue;
            }

            // Nếu có sính lễ rơi trên đất chưa ai nhặt -> Bot chạy đến nhặt
            TributeItem closestDrop = null;
            float minDist = 999999f;
            for (TributeItem item : tributes) {
                if (!item.isDelivered && item.carriedByPlayerId == -1 && item.x > 0) {
                    float d = (float) Math.hypot(item.x - bot.x, item.y - bot.y);
                    if (d < minDist) {
                        minDist = d;
                        closestDrop = item;
                    }
                }
            }

            if (closestDrop != null && minDist < 1500) {
                float dx = closestDrop.x - bot.x;
                float dy = closestDrop.y - bot.y;
                float dist = (float) Math.hypot(dx, dy);
                if (dist > 30) {
                    bot.vx = (dx / dist) * Constants.BASE_PLAYER_SPEED * bot.speedMultiplier;
                    bot.vy = (dy / dist) * Constants.BASE_PLAYER_SPEED * bot.speedMultiplier;
                    bot.angle = (float) Math.atan2(dy, dx);
                } else {
                    handleInteract(bot.id); // Nhặt sính lễ
                }
                continue;
            }

            // Nếu còn Boss sống -> Tìm Boss gần nhất để săn
            MonsterState targetBoss = null;
            float minBossDist = 999999f;
            for (MonsterState m : monsters) {
                if (m.isAlive) {
                    float d = (float) Math.hypot(m.x - bot.x, m.y - bot.y);
                    if (d < minBossDist) {
                        minBossDist = d;
                        targetBoss = m;
                    }
                }
            }

            if (targetBoss != null) {
                float dx = targetBoss.x - bot.x;
                float dy = targetBoss.y - bot.y;
                float dist = (float) Math.hypot(dx, dy);

                if (dist > 180) {
                    bot.vx = (dx / dist) * Constants.BASE_PLAYER_SPEED * bot.speedMultiplier;
                    bot.vy = (dy / dist) * Constants.BASE_PLAYER_SPEED * bot.speedMultiplier;
                } else {
                    bot.vx = 0;
                    bot.vy = 0;
                    // Tấn công Boss
                    bot.angle = (float) Math.atan2(dy, dx);
                    if (Math.random() < 0.05) {
                        spawnProjectile(bot.id, bot.team, bot.x, bot.y, bot.angle, 35, 1);
                    }
                }
                continue;
            }

            // Săn người chơi địch (đặc biệt kẻ mang sính lễ)
            PlayerState enemyTarget = null;
            for (PlayerState enemy : players.values()) {
                if (enemy.team != bot.team && !enemy.isDead) {
                    if (!enemy.backpack.isEmpty() || enemyTarget == null) {
                        enemyTarget = enemy;
                    }
                }
            }

            if (enemyTarget != null) {
                float dx = enemyTarget.x - bot.x;
                float dy = enemyTarget.y - bot.y;
                float dist = (float) Math.hypot(dx, dy);
                if (dist > 200) {
                    bot.vx = (dx / dist) * Constants.BASE_PLAYER_SPEED * bot.speedMultiplier;
                    bot.vy = (dy / dist) * Constants.BASE_PLAYER_SPEED * bot.speedMultiplier;
                } else {
                    bot.vx = 0;
                    bot.vy = 0;
                    bot.angle = (float) Math.atan2(dy, dx);
                    if (Math.random() < 0.06) {
                        spawnProjectile(bot.id, bot.team, bot.x, bot.y, bot.angle, 30, 1);
                    }
                }
            }
        }
    }

    private void updateMonstersAI(float dt) {
        for (MonsterState m : monsters) {
            if (!m.isAlive) continue;

            // Tìm dũng sĩ gần nhất
            PlayerState target = null;
            float minDist = 380; // Tầm nhìn / aggro

            for (PlayerState p : players.values()) {
                if (!p.isDead) {
                    float dist = (float) Math.hypot(p.x - m.x, p.y - m.y);
                    if (dist < minDist) {
                        minDist = dist;
                        target = p;
                    }
                }
            }

            if (target != null) {
                m.targetPlayerId = target.id;
                float dx = target.x - m.x;
                float dy = target.y - m.y;
                float dist = (float) Math.hypot(dx, dy);

                if (dist > 60) {
                    float speed = 120.0f;
                    m.vx = (dx / dist) * speed;
                    m.vy = (dy / dist) * speed;
                    m.x += m.vx * dt;
                    m.y += m.vy * dt;
                } else {
                    // Cận chiến cào xé dũng sĩ
                    if (Math.random() < 0.04) {
                        target.hp -= 20;
                        if (target.hp <= 0) {
                            handlePlayerKilled(target, null, "Quái thú " + m.name);
                        }
                    }
                }
            } else {
                m.targetPlayerId = -1;
                m.vx = 0;
                m.vy = 0;
            }
        }
    }

    private void updateProjectiles(float dt) {
        Iterator<ProjectileState> it = projectiles.iterator();
        while (it.hasNext()) {
            ProjectileState proj = it.next();
            proj.x += proj.vx * dt;
            proj.y += proj.vy * dt;

            // Nếu bay ra khỏi bản đồ
            if (proj.x < 0 || proj.x > Constants.MAP_WIDTH || proj.y < 0 || proj.y > Constants.MAP_HEIGHT) {
                projectiles.remove(proj);
                continue;
            }

            boolean hit = false;

            // 1. Kiểm tra va chạm với Boss
            for (MonsterState m : monsters) {
                if (m.isAlive && Math.hypot(proj.x - m.x, proj.y - m.y) < 55) {
                    m.hp -= proj.damage;
                    hit = true;
                    if (m.hp <= 0) {
                        m.isAlive = false;
                        handleBossSlain(m, proj.ownerId);
                    }
                    break;
                }
            }

            // 2. Kiểm tra va chạm với Người chơi địch
            if (!hit) {
                for (PlayerState p : players.values()) {
                    if (p.id != proj.ownerId && p.team != proj.team && !p.isDead) {
                        if (Math.hypot(proj.x - p.x, proj.y - p.y) < 35) {
                            p.hp -= proj.damage;
                            hit = true;
                            if (p.hp <= 0) {
                                PlayerState killer = players.get(proj.ownerId);
                                handlePlayerKilled(p, killer, killer != null ? killer.username : "Đối thủ");
                            }
                            break;
                        }
                    }
                }
            }

            if (hit) {
                projectiles.remove(proj);
            }
        }
    }

    private void handleBossSlain(MonsterState boss, int killerId) {
        PlayerState killer = players.get(killerId);
        String killerName = killer != null ? killer.username : "Dũng sĩ vô danh";
        broadcastSystemChat("HUNG THẦN GỤC NGÃ! " + killerName + " đã tiêu diệt " + boss.name + "!");

        // Rơi Sính Lễ tại vị trí Boss chết
        for (TributeItem item : tributes) {
            if (item.type == boss.guardedTribute) {
                item.x = boss.x;
                item.y = boss.y;
                item.carriedByPlayerId = -1;
                broadcastSystemChat(">>> SÍNH LỄ THẦN THOẠI [" + item.type.getDisplayName() + "] ĐÃ XUẤT HIỆN!");
                break;
            }
        }
        broadcastInventorySync();
    }

    /**
     * Xử lý khi 1 người chơi bị đánh bại:
     * - Văng toàn bộ Balo sính lễ ra đất!
     * - Chờ hồi sinh
     */
    private void handlePlayerKilled(PlayerState victim, PlayerState killer, String cause) {
        victim.isDead = true;
        victim.hp = 0;
        victim.respawnTimer = Constants.RESPAWN_TIME_SEC;

        if (killer != null) {
            killer.kills++;
        }

        // Văng toàn bộ sính lễ trong Balo ra đất
        if (!victim.backpack.isEmpty()) {
            broadcastSystemChat("CƯỚP SÍNH LỄ! " + victim.username + " bị hạ gục bởi " + cause + "! Balo sính lễ văng ra đất!");
            Random rand = new Random();
            for (TributeType t : victim.backpack) {
                for (TributeItem item : tributes) {
                    if (item.type == t) {
                        item.carriedByPlayerId = -1;
                        item.x = victim.x + (rand.nextFloat() * 120 - 60);
                        item.y = victim.y + (rand.nextFloat() * 120 - 60);
                        break;
                    }
                }
            }
            victim.backpack.clear();
        } else {
            broadcastSystemChat(victim.username + " đã bị tiêu diệt bởi " + cause + "!");
        }

        broadcastInventorySync();
    }

    /**
     * Tương tác nhặt hoặc nộp sính lễ
     */
    public synchronized void handleInteract(int playerId) {
        PlayerState p = players.get(playerId);
        if (p == null || p.isDead) return;

        // 1. Kiểm tra nộp lễ tại Cung Điện Vua Hùng
        float distToShrine = (float) Math.hypot(p.x - Constants.SHRINE_X, p.y - Constants.SHRINE_Y);
        if (distToShrine <= Constants.SHRINE_RADIUS && !p.backpack.isEmpty()) {
            Iterator<TributeType> it = p.backpack.iterator();
            while (it.hasNext()) {
                TributeType t = it.next();
                for (TributeItem item : tributes) {
                    if (item.type == t) {
                        item.isDelivered = true;
                        item.deliveredByTeam = p.team;
                        item.carriedByPlayerId = -1;
                        item.x = Constants.SHRINE_X;
                        item.y = Constants.SHRINE_Y;
                        break;
                    }
                }
                it.remove();

                if (p.team == Team.SON_TINH) {
                    scoreSonTinh++;
                } else {
                    scoreThuyTinh++;
                }
                p.tributesSubmitted++;

                broadcastSystemChat("DÂNG LỄ THÀNH CÔNG! " + p.username + " (" + p.team.getName() 
                    + ") đã dâng lên Vua Hùng: " + t.getDisplayName() + "!");
            }

            broadcastInventorySync();
            checkVictoryCondition();
            return;
        }

        // 2. Kiểm tra nhặt sính lễ trên đất
        for (TributeItem item : tributes) {
            if (!item.isDelivered && item.carriedByPlayerId == -1 && item.x > 0) {
                float d = (float) Math.hypot(p.x - item.x, p.y - item.y);
                if (d <= Constants.PICKUP_RADIUS) {
                    item.carriedByPlayerId = p.id;
                    item.x = -1000;
                    item.y = -1000;
                    p.backpack.add(item.type);
                    broadcastSystemChat("NHẶT SÍNH LỄ! " + p.username + " đã nhặt được [" + item.type.getDisplayName() 
                        + "] vào Balo! (Gánh nặng tăng lên, di chuyển chậm lại!)");
                    broadcastInventorySync();
                    break;
                }
            }
        }
    }

    private void checkVictoryCondition() {
        if (scoreSonTinh >= 3) {
            triggerVictory(Team.SON_TINH);
        } else if (scoreThuyTinh >= 3) {
            triggerVictory(Team.THUY_TINH);
        }
    }

    private void triggerVictory(Team team) {
        if (isGameOver) return;
        isGameOver = true;
        winningTeam = team;

        String victorName = team == Team.SON_TINH ? "SƠN TINH (CHÚA MIỀN NON CAO)" : "THỦY TINH (CHÚA VÙNG NƯỚC THẲM)";
        broadcastSystemChat("==================================================");
        broadcastSystemChat("   KẾT GIỚI KÍCH HOẠT! VUA HÙNG PHÁN:");
        broadcastSystemChat("   " + victorName + " ĐÃ DÂNG ĐỦ TAM SÍNH LỄ TRƯỚC!");
        broadcastSystemChat("   CHÍNH THỨC TRỞ THÀNH PHÒ MÃ NƯỚC VĂN LANG!");
        broadcastSystemChat("==================================================");

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeByte(team.getId());
            dos.writeUTF("Đội " + team.getName() + " đã chiến thắng tuyệt đối!");
            dos.writeInt(scoreSonTinh);
            dos.writeInt(scoreThuyTinh);

            CustomPacket pkt = new CustomPacket(PacketType.GAME_VICTORY_NOTIFY, baos.toByteArray());
            for (ClientSession session : sessions.values()) {
                session.sendTCP(pkt);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Bắn đạn / Kỹ năng thông thường của người chơi
     */
    public void handlePlayerAttack(int playerId, float angle) {
        PlayerState p = players.get(playerId);
        if (p == null || p.isDead) return;

        int skillType = (p.team == Team.SON_TINH) ? 1 : 2;
        int damage = 35;
        spawnProjectile(p.id, p.team, p.x, p.y, angle, damage, skillType);
    }

    public void spawnProjectile(int ownerId, Team team, float x, float y, float angle, int dmg, int type) {
        float speed = 550.0f;
        float vx = (float) Math.cos(angle) * speed;
        float vy = (float) Math.sin(angle) * speed;
        ProjectileState proj = new ProjectileState(nextProjectileId++, ownerId, team, x, y, vx, vy, dmg, type);
        projectiles.add(proj);
    }

    /**
     * Kỹ năng đặc biệt (Chiêu tối thượng Thủy Tinh / Sơn Tinh)
     */
    public void handleSpecialSkill(int playerId) {
        PlayerState p = players.get(playerId);
        if (p == null || p.isDead || p.mana < 40) return;

        p.mana -= 40;
        if (p.team == Team.THUY_TINH) {
            // Thủy Tinh: "Hô Mưa Gọi Gió / Thủy Triều Dâng"
            floodZone.isActive = true;
            floodZone.waterLevel = 1.0f;
            floodZone.centerX = p.x;
            floodZone.centerY = p.y;
            broadcastSystemChat("SÓNG CUỘN LŨ DÂNG! " + p.username + " hô mưa gọi gió, dâng nước cuồn cuộn làm ngập lụt vùng đất!");
        } else {
            // Sơn Tinh: "Bốc Đồi Dời Núi / Hộ Thể Thần Thạch"
            // Bắn 6 tảng đá bao quanh và tăng tốc
            broadcastSystemChat("DỜI NON LẤP BỂ! " + p.username + " dựng luỹ đá hộ thể, phóng cuồng thạch đánh lui quân giặc!");
            for (int i = 0; i < 8; i++) {
                double a = (Math.PI * 2 / 8) * i;
                spawnProjectile(p.id, p.team, p.x, p.y, (float) a, 40, 1);
            }
            p.hp = Math.min(p.maxHp, p.hp + 60);
        }
    }

    public void updatePlayerInput(int playerId, float targetX, float targetY, float vx, float vy, float angle) {
        PlayerState p = players.get(playerId);
        if (p != null && !p.isDead) {
            p.vx = vx;
            p.vy = vy;
            p.angle = angle;
        }
    }

    public void broadcastSystemChat(String text) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF("[HỆ THỐNG]");
            dos.writeUTF(text);
            CustomPacket pkt = new CustomPacket(PacketType.CHAT_MSG, baos.toByteArray());
            for (ClientSession session : sessions.values()) {
                session.sendTCP(pkt);
            }
        } catch (Exception ignored) {}
    }

    public void broadcastInventorySync() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeInt(scoreSonTinh);
            dos.writeInt(scoreThuyTinh);

            // Ghi danh sách 3 món sính lễ
            dos.writeByte(tributes.size());
            for (TributeItem item : tributes) {
                item.writeToStream(dos);
            }

            // Ghi balo của từng player
            dos.writeByte(players.size());
            for (PlayerState p : players.values()) {
                dos.writeInt(p.id);
                dos.writeByte(p.backpack.size());
                for (TributeType t : p.backpack) {
                    dos.writeByte(t.getId());
                }
            }

            CustomPacket pkt = new CustomPacket(PacketType.INVENTORY_SYNC_NOTIFY, baos.toByteArray());
            for (ClientSession session : sessions.values()) {
                session.sendTCP(pkt);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Broadcast Snapshot vị trí thực thể qua UDP
     */
    private void broadcastUDPSnapshot() {
        if (udpSocket == null || udpSocket.isClosed()) return;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // Players
            dos.writeByte(players.size());
            for (PlayerState p : players.values()) {
                p.writeToStream(dos);
            }

            // Quái / Boss
            dos.writeByte(monsters.size());
            for (MonsterState m : monsters) {
                m.writeToStream(dos);
            }

            // Đạn Projectiles
            dos.writeShort(projectiles.size());
            for (ProjectileState proj : projectiles) {
                proj.writeToStream(dos);
            }

            // Nước Lũ FloodZone
            dos.writeBoolean(floodZone.isActive);
            dos.writeFloat(floodZone.waterLevel);
            dos.writeFloat(floodZone.centerX);
            dos.writeFloat(floodZone.centerY);
            dos.writeFloat(floodZone.radius);

            byte[] payload = baos.toByteArray();
            CustomPacket pkt = new CustomPacket(PacketType.UDP_WORLD_SNAPSHOT, payload);
            byte[] packetBytes = pkt.toBytes();

            for (ClientSession session : sessions.values()) {
                if (!session.isBot && session.udpAddress != null && session.udpPort > 0) {
                    DatagramPacket dpacket = new DatagramPacket(packetBytes, packetBytes.length, session.udpAddress, session.udpPort);
                    udpSocket.send(dpacket);
                }
            }
        } catch (Exception ignored) {}
    }

    public void cleanup() {
        if (gameLoopExecutor != null) {
            gameLoopExecutor.shutdown();
        }
    }

    public String getRoomToken() {
        return roomToken;
    }
}
