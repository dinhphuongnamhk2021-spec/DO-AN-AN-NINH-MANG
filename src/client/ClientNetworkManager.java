package client;

import common.Constants;
import common.CustomPacket;
import common.GameModels.*;
import common.PacketType;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ClientNetworkManager {
    public interface NetworkListener {
        void onLoginResult(boolean success, String message, int elo, int won, int lost, int tributes);
        void onRegisterResult(boolean success, String message);
        void onLeaderboardReceived(List<String[]> leaderboard);
        void onQueueStatus(int inQueue, int needed, String statusText);
        void onMatchFound(String roomToken, int myPlayerId, Team myTeam, String myRole, List<String> playerNames);
        void onGameVictory(Team winningTeam, String message);
        void onChatMessage(String sender, String message);
    }

    private final ClientGameState gameState;
    private NetworkListener listener;

    // Master TCP Connection
    private Socket masterSocket;
    private DataInputStream masterIn;
    private DataOutputStream masterOut;
    private Thread masterListenerThread;

    // Game TCP Connection
    private Socket gameTcpSocket;
    private DataInputStream gameIn;
    private DataOutputStream gameOut;
    private Thread gameTcpListenerThread;

    // Game UDP Connection
    private DatagramSocket gameUdpSocket;
    private Thread gameUdpReceiverThread;
    private ScheduledExecutorService udpSendExecutor;
    private InetAddress gameServerAddress;
    private int gameServerUdpPort;

    private volatile boolean isRunning = true;

    public ClientNetworkManager(ClientGameState gameState) {
        this.gameState = gameState;
    }

    public void setListener(NetworkListener listener) {
        this.listener = listener;
    }

    // =========================================================================
    // 1. KẾT NỐI VÀ GIAO TIẾP VỚI MASTER SERVER (TCP)
    // =========================================================================
    public boolean connectMaster(String host, int port) {
        try {
            masterSocket = new Socket(host, port);
            masterSocket.setTcpNoDelay(true);
            masterIn = new DataInputStream(new BufferedInputStream(masterSocket.getInputStream()));
            masterOut = new DataOutputStream(new BufferedOutputStream(masterSocket.getOutputStream()));

            masterListenerThread = new Thread(this::listenMasterTCP, "Client-MasterListener");
            masterListenerThread.start();
            return true;
        } catch (IOException e) {
            System.err.println("[ClientNet] Không thể kết nối tới Master Server: " + e.getMessage());
            return false;
        }
    }

    private void listenMasterTCP() {
        try {
            while (isRunning && masterSocket != null && !masterSocket.isClosed()) {
                CustomPacket packet = CustomPacket.readFromTCP(masterIn);
                handleMasterPacket(packet);
            }
        } catch (IOException ignored) {}
    }

    private void handleMasterPacket(CustomPacket packet) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet.getPayload()));
        switch (packet.getType()) {
            case LOGIN_RESP -> {
                boolean ok = dis.readBoolean();
                String msg = dis.readUTF();
                int elo = ok ? dis.readInt() : 0;
                int won = ok ? dis.readInt() : 0;
                int lost = ok ? dis.readInt() : 0;
                int tributes = ok ? dis.readInt() : 0;
                if (listener != null) listener.onLoginResult(ok, msg, elo, won, lost, tributes);
            }
            case REGISTER_RESP -> {
                boolean ok = dis.readBoolean();
                String msg = dis.readUTF();
                if (listener != null) listener.onRegisterResult(ok, msg);
            }
            case LEADERBOARD_RESP -> {
                int count = dis.readInt();
                List<String[]> list = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    String user = dis.readUTF();
                    int elo = dis.readInt();
                    int won = dis.readInt();
                    int lost = dis.readInt();
                    int tributes = dis.readInt();
                    list.add(new String[]{String.valueOf(i + 1), user, String.valueOf(elo), 
                        won + "/" + lost, String.valueOf(tributes)});
                }
                if (listener != null) listener.onLeaderboardReceived(list);
            }
            case MATCHMAKING_STATUS_RESP -> {
                int current = dis.readInt();
                int total = dis.readInt();
                String status = dis.readUTF();
                if (listener != null) listener.onQueueStatus(current, total, status);
            }
            case MATCH_FOUND_RESP -> {
                String host = dis.readUTF();
                int tcpPort = dis.readInt();
                int udpPort = dis.readInt();
                String roomToken = dis.readUTF();
                int playerId = dis.readInt();
                Team team = Team.fromId(dis.readByte());
                String role = dis.readUTF();

                int totalPlayers = dis.readByte();
                List<String> playerDetails = new ArrayList<>();
                for (int i = 0; i < totalPlayers; i++) {
                    int pId = dis.readInt();
                    String pName = dis.readUTF();
                    byte pTeam = dis.readByte();
                    String pRole = dis.readUTF();
                    boolean isBot = dis.readBoolean();
                    playerDetails.add(pName + " (" + (pTeam == 1 ? "Sơn Tinh" : "Thủy Tinh") + " - " + pRole + (isBot ? " [BOT]" : "") + ")");
                }

                gameState.myPlayerId = playerId;
                gameState.myTeam = team;
                gameState.myRoleName = role;
                gameState.roomToken = roomToken;

                if (listener != null) listener.onMatchFound(roomToken, playerId, team, role, playerDetails);

                // Tự động kết nối Game Server
                connectGameServer(host, tcpPort, udpPort, roomToken);
            }
            default -> {}
        }
    }

    public void sendLogin(String username, String password) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(username);
            dos.writeUTF(password);
            masterOut.write(new CustomPacket(PacketType.LOGIN_REQ, baos.toByteArray()).toBytes());
            masterOut.flush();
        } catch (IOException ignored) {}
    }

    public void sendRegister(String username, String password) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(username);
            dos.writeUTF(password);
            masterOut.write(new CustomPacket(PacketType.REGISTER_REQ, baos.toByteArray()).toBytes());
            masterOut.flush();
        } catch (IOException ignored) {}
    }

    public void requestLeaderboard() {
        try {
            masterOut.write(new CustomPacket(PacketType.LEADERBOARD_REQ, new byte[0]).toBytes());
            masterOut.flush();
        } catch (IOException ignored) {}
    }

    public void joinMatchmaking(int preferredTeam, boolean allowBots) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeBoolean(true); // isJoining
            dos.writeByte(preferredTeam);
            dos.writeBoolean(allowBots);
            masterOut.write(new CustomPacket(PacketType.MATCHMAKING_QUEUE_REQ, baos.toByteArray()).toBytes());
            masterOut.flush();
        } catch (IOException ignored) {}
    }

    public void cancelMatchmaking() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeBoolean(false);
            dos.writeByte(0);
            dos.writeBoolean(false);
            masterOut.write(new CustomPacket(PacketType.MATCHMAKING_QUEUE_REQ, baos.toByteArray()).toBytes());
            masterOut.flush();
        } catch (IOException ignored) {}
    }

    // =========================================================================
    // 2. KẾT NỐI VÀ GIAO TIẾP VỚI GAME SERVER (TCP + UDP)
    // =========================================================================
    public void connectGameServer(String host, int tcpPort, int udpPort, String roomToken) {
        try {
            this.gameServerUdpPort = udpPort;
            this.gameServerAddress = InetAddress.getByName(host);

            // TCP Connection
            gameTcpSocket = new Socket(host, tcpPort);
            gameTcpSocket.setTcpNoDelay(true);
            gameIn = new DataInputStream(new BufferedInputStream(gameTcpSocket.getInputStream()));
            gameOut = new DataOutputStream(new BufferedOutputStream(gameTcpSocket.getOutputStream()));

            // Gửi Handshake
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(roomToken);
            dos.writeInt(gameState.myPlayerId);
            dos.writeUTF(gameState.myUsername);
            dos.writeByte(gameState.myTeam.getId());
            dos.writeUTF(gameState.myRoleName);

            gameOut.write(new CustomPacket(PacketType.GAME_HANDSHAKE_REQ, baos.toByteArray()).toBytes());
            gameOut.flush();

            gameTcpListenerThread = new Thread(this::listenGameTCP, "Client-GameTCPListener");
            gameTcpListenerThread.start();

            // UDP Connection
            gameUdpSocket = new DatagramSocket();
            gameUdpReceiverThread = new Thread(this::listenGameUDP, "Client-GameUDPReceiver");
            gameUdpReceiverThread.start();

            // Bắt đầu luồng gửi tọa độ UDP 40Hz
            udpSendExecutor = Executors.newSingleThreadScheduledExecutor();
            udpSendExecutor.scheduleAtFixedRate(this::sendUDPInput, 0, Constants.UDP_BROADCAST_INTERVAL_MS, TimeUnit.MILLISECONDS);

            System.out.println("[ClientNet] Đã kết nối TCP và UDP thành công tới Game Server!");
        } catch (IOException e) {
            System.err.println("[ClientNet] Lỗi kết nối Game Server: " + e.getMessage());
        }
    }

    private void listenGameTCP() {
        try {
            while (isRunning && gameTcpSocket != null && !gameTcpSocket.isClosed()) {
                CustomPacket packet = CustomPacket.readFromTCP(gameIn);
                handleGamePacket(packet);
            }
        } catch (IOException ignored) {}
    }

    private void handleGamePacket(CustomPacket packet) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet.getPayload()));
        switch (packet.getType()) {
            case INVENTORY_SYNC_NOTIFY -> {
                gameState.scoreSonTinh = dis.readInt();
                gameState.scoreThuyTinh = dis.readInt();

                // Đọc trạng thái 3 sính lễ
                int tributeCount = dis.readByte();
                gameState.tributes.clear();
                for (int i = 0; i < tributeCount; i++) {
                    gameState.tributes.add(TributeItem.readFromStream(dis));
                }

                // Cập nhật Balo người chơi
                int playerCount = dis.readByte();
                for (int i = 0; i < playerCount; i++) {
                    int pId = dis.readInt();
                    int bagSize = dis.readByte();
                    PlayerState p = gameState.players.get(pId);
                    if (p != null) {
                        p.backpack.clear();
                        for (int j = 0; j < bagSize; j++) {
                            p.backpack.add(TributeType.fromId(dis.readByte()));
                        }
                    } else {
                        // Bỏ qua byte nếu chưa tải player
                        for (int j = 0; j < bagSize; j++) dis.readByte();
                    }
                }
            }

            case GAME_VICTORY_NOTIFY -> {
                Team winner = Team.fromId(dis.readByte());
                String msg = dis.readUTF();
                gameState.scoreSonTinh = dis.readInt();
                gameState.scoreThuyTinh = dis.readInt();
                gameState.isGameOver = true;
                gameState.winningTeam = winner;
                gameState.victoryMessage = msg;
                if (listener != null) listener.onGameVictory(winner, msg);
            }

            case CHAT_MSG -> {
                String sender = dis.readUTF();
                String text = dis.readUTF();
                gameState.addCombatLog(sender + ": " + text);
                if (listener != null) listener.onChatMessage(sender, text);
            }
            default -> {}
        }
    }

    private void listenGameUDP() {
        byte[] buffer = new byte[8192];
        while (isRunning && gameUdpSocket != null && !gameUdpSocket.isClosed()) {
            try {
                DatagramPacket dpacket = new DatagramPacket(buffer, buffer.length);
                gameUdpSocket.receive(dpacket);
                CustomPacket packet = CustomPacket.fromBytes(dpacket.getData(), dpacket.getLength());

                if (packet.getType() == PacketType.UDP_WORLD_SNAPSHOT) {
                    unpackWorldSnapshot(packet.getPayload());
                }
            } catch (Exception ignored) {}
        }
    }

    private void unpackWorldSnapshot(byte[] payload) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload));

        // 1. Players
        int pCount = dis.readByte();
        for (int i = 0; i < pCount; i++) {
            PlayerState remoteP = PlayerState.readFromStream(dis);
            PlayerState localP = gameState.players.get(remoteP.id);
            if (localP == null) {
                gameState.players.put(remoteP.id, remoteP);
            } else {
                // Đồng bộ chỉ số
                localP.hp = remoteP.hp;
                localP.maxHp = remoteP.maxHp;
                localP.mana = remoteP.mana;
                localP.maxMana = remoteP.maxMana;
                localP.speedMultiplier = remoteP.speedMultiplier;
                localP.isDead = remoteP.isDead;
                localP.respawnTimer = remoteP.respawnTimer;
                localP.kills = remoteP.kills;
                localP.tributesSubmitted = remoteP.tributesSubmitted;
                localP.backpack = remoteP.backpack;

                // Nội suy tọa độ hoặc cập nhật trực tiếp
                if (remoteP.id != gameState.myPlayerId) {
                    localP.x = remoteP.x;
                    localP.y = remoteP.y;
                    localP.vx = remoteP.vx;
                    localP.vy = remoteP.vy;
                    localP.angle = remoteP.angle;
                }
            }
        }

        // 2. Monsters / Boss
        int mCount = dis.readByte();
        gameState.monsters.clear();
        for (int i = 0; i < mCount; i++) {
            gameState.monsters.add(MonsterState.readFromStream(dis));
        }

        // 3. Projectiles
        int projCount = dis.readShort();
        gameState.projectiles.clear();
        for (int i = 0; i < projCount; i++) {
            gameState.projectiles.add(ProjectileState.readFromStream(dis));
        }

        // 4. FloodZone
        gameState.floodZone.isActive = dis.readBoolean();
        gameState.floodZone.waterLevel = dis.readFloat();
        gameState.floodZone.centerX = dis.readFloat();
        gameState.floodZone.centerY = dis.readFloat();
        gameState.floodZone.radius = dis.readFloat();
    }

    private void sendUDPInput() {
        if (gameUdpSocket == null || gameUdpSocket.isClosed() || gameServerAddress == null) return;
        PlayerState myP = gameState.getMyPlayer();
        if (myP == null) return;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(gameState.roomToken);
            dos.writeInt(myP.id);
            dos.writeFloat(myP.x);
            dos.writeFloat(myP.y);
            dos.writeFloat(myP.vx);
            dos.writeFloat(myP.vy);
            dos.writeFloat(myP.angle);

            CustomPacket pkt = new CustomPacket(PacketType.UDP_CLIENT_INPUT, baos.toByteArray());
            byte[] bytes = pkt.toBytes();
            DatagramPacket dpacket = new DatagramPacket(bytes, bytes.length, gameServerAddress, gameServerUdpPort);
            gameUdpSocket.send(dpacket);
        } catch (Exception ignored) {}
    }

    public void sendAttack(float angle) {
        if (gameOut == null) return;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeFloat(angle);
            gameOut.write(new CustomPacket(PacketType.ACTION_ATTACK_REQ, baos.toByteArray()).toBytes());
            gameOut.flush();
        } catch (IOException ignored) {}
    }

    public void sendSpecialSkill() {
        if (gameOut == null) return;
        try {
            gameOut.write(new CustomPacket(PacketType.ACTION_SKILL_REQ, new byte[0]).toBytes());
            gameOut.flush();
        } catch (IOException ignored) {}
    }

    public void sendInteract() {
        if (gameOut == null) return;
        try {
            gameOut.write(new CustomPacket(PacketType.ACTION_INTERACT_REQ, new byte[0]).toBytes());
            gameOut.flush();
        } catch (IOException ignored) {}
    }

    public void sendChat(String msg) {
        if (gameOut == null) return;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeUTF(gameState.myUsername);
            dos.writeUTF(msg);
            gameOut.write(new CustomPacket(PacketType.CHAT_MSG, baos.toByteArray()).toBytes());
            gameOut.flush();
        } catch (IOException ignored) {}
    }

    public void disconnect() {
        isRunning = false;
        if (udpSendExecutor != null) udpSendExecutor.shutdown();
        try { if (gameUdpSocket != null) gameUdpSocket.close(); } catch (Exception ignored) {}
        try { if (gameTcpSocket != null) gameTcpSocket.close(); } catch (Exception ignored) {}
        try { if (masterSocket != null) masterSocket.close(); } catch (Exception ignored) {}
    }
}
