package master;

import common.Constants;
import common.CustomPacket;
import common.GameModels.Team;
import common.PacketType;
import common.SecurityUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class Matchmaker {
    public static class QueueRequest {
        public String username;
        public int preferredTeam; // 0: Bất kỳ, 1: Sơn Tinh, 2: Thủy Tinh
        public boolean allowBots;
        public MasterServer.ClientHandler handler;
        public long joinTime;

        public QueueRequest(String username, int preferredTeam, boolean allowBots, MasterServer.ClientHandler handler) {
            this.username = username;
            this.preferredTeam = preferredTeam;
            this.allowBots = allowBots;
            this.handler = handler;
            this.joinTime = System.currentTimeMillis();
        }
    }

    public static class MatchAssignment {
        public int playerId;
        public String username;
        public Team team;
        public String roleName;
        public boolean isBot;
    }

    private final List<QueueRequest> waitingQueue = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public Matchmaker() {
        // Quét hàng đợi mỗi 1 giây để kiểm tra ghép 4 người hoặc kích hoạt bot sau 3 giây chờ
        scheduler.scheduleAtFixedRate(this::checkAndCreateMatches, 1, 1, TimeUnit.SECONDS);
    }

    public synchronized void enqueue(QueueRequest req) {
        // Kiểm tra xem đã có trong queue chưa
        waitingQueue.removeIf(q -> q.username.equalsIgnoreCase(req.username));
        waitingQueue.add(req);
        System.out.println("[Matchmaker] Người chơi " + req.username + " tham gia hàng đợi ghép trận 2v2 (Bots: " + req.allowBots + ")");
        broadcastQueueStatus();
        checkAndCreateMatches();
    }

    public synchronized void dequeue(String username) {
        waitingQueue.removeIf(q -> q.username.equalsIgnoreCase(username));
        broadcastQueueStatus();
    }

    private synchronized void broadcastQueueStatus() {
        int count = waitingQueue.size();
        for (QueueRequest req : waitingQueue) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                dos.writeInt(count);
                dos.writeInt(4); // Cần 4 người cho 2v2
                dos.writeUTF("Đang tìm đối thủ trong cõi Phong Châu (" + count + "/4)...");
                req.handler.sendPacket(new CustomPacket(PacketType.MATCHMAKING_STATUS_RESP, baos.toByteArray()));
            } catch (Exception ignored) {}
        }
    }

    private synchronized void checkAndCreateMatches() {
        if (waitingQueue.isEmpty()) return;

        // Nếu có đủ 4 người chơi thực
        if (waitingQueue.size() >= 4) {
            List<QueueRequest> players = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                players.add(waitingQueue.remove(0));
            }
            createMatch(players, 0);
            broadcastQueueStatus();
            return;
        }

        // Nếu có người chơi bật cờ allowBots hoặc chờ quá 3 giây mà cho phép bot
        long now = System.currentTimeMillis();
        for (QueueRequest req : waitingQueue) {
            if (req.allowBots || (now - req.joinTime > 4000)) {
                // Tạo phòng đấu với bots cho người này và những người đang chờ cùng
                List<QueueRequest> players = new ArrayList<>();
                Iterator<QueueRequest> it = waitingQueue.iterator();
                while (it.hasNext() && players.size() < 4) {
                    QueueRequest q = it.next();
                    players.add(q);
                    waitingQueue.remove(q);
                }
                int botsNeeded = 4 - players.size();
                createMatch(players, botsNeeded);
                broadcastQueueStatus();
                return;
            }
        }
    }

    private void createMatch(List<QueueRequest> realPlayers, int botsNeeded) {
        String roomToken = SecurityUtils.generateSessionToken();
        System.out.println("[Matchmaker] === KHỞI TẠO TRẬN ĐẤU 2V2 MỚI (RoomToken: " + roomToken + ") ===");
        System.out.println("  -> Số người chơi thực: " + realPlayers.size() + ", Số Thần Binh Bot: " + botsNeeded);

        List<MatchAssignment> assignments = new ArrayList<>();
        int idCounter = 1;

        // Phân chia 2 bên: Slot 0, 1 là Sơn Tinh | Slot 2, 3 là Thủy Tinh
        int realIdx = 0;
        int botIdx = 1;

        for (int slot = 0; slot < 4; slot++) {
            MatchAssignment assign = new MatchAssignment();
            assign.playerId = idCounter++;
            if (slot < 2) {
                assign.team = Team.SON_TINH;
                assign.roleName = (slot == 0) ? "Sơn Tinh (Chúa Non Cao)" : "Hộ Vệ Thần Núi";
            } else {
                assign.team = Team.THUY_TINH;
                assign.roleName = (slot == 2) ? "Thủy Tinh (Chúa Sóng Lũ)" : "Hộ Vệ Thần Sóng";
            }

            if (realIdx < realPlayers.size()) {
                QueueRequest req = realPlayers.get(realIdx++);
                assign.username = req.username;
                assign.isBot = false;
            } else {
                assign.username = "ThanBinh_Bot_" + (botIdx++);
                assign.isBot = true;
            }
            assignments.add(assign);
        }

        // Gửi gói tin MATCH_FOUND_RESP cho tất cả người chơi thực
        for (int i = 0; i < realPlayers.size(); i++) {
            QueueRequest req = realPlayers.get(i);
            MatchAssignment myAssign = assignments.get(i);

            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);

                dos.writeUTF(Constants.DEFAULT_HOST); // Game Server Host
                dos.writeInt(Constants.GAME_TCP_PORT); // Game TCP Port
                dos.writeInt(Constants.GAME_UDP_PORT); // Game UDP Port
                dos.writeUTF(roomToken);
                dos.writeInt(myAssign.playerId);
                dos.writeByte(myAssign.team.getId());
                dos.writeUTF(myAssign.roleName);

                // Gửi danh sách toàn bộ 4 thành viên trận đấu
                dos.writeByte(4);
                for (MatchAssignment a : assignments) {
                    dos.writeInt(a.playerId);
                    dos.writeUTF(a.username);
                    dos.writeByte(a.team.getId());
                    dos.writeUTF(a.roleName);
                    dos.writeBoolean(a.isBot);
                }

                req.handler.sendPacket(new CustomPacket(PacketType.MATCH_FOUND_RESP, baos.toByteArray()));
                System.out.println("  -> Đã gửi thông tin phòng cho " + req.username + " (" + myAssign.roleName + ")");
            } catch (Exception e) {
                System.err.println("[Matchmaker] Lỗi gửi match info: " + e.getMessage());
            }
        }
    }
}
