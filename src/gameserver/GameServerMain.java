package gameserver;

import common.Constants;
import common.CustomPacket;
import common.GameModels.Team;
import common.PacketType;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameServerMain {
    private final int tcpPort;
    private final int udpPort;
    private final Map<String, GameRoom> activeRooms = new ConcurrentHashMap<>();
    private DatagramSocket udpSocket;
    private ServerSocket tcpServerSocket;
    private volatile boolean isRunning = true;

    public GameServerMain(int tcpPort, int udpPort) {
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
    }

    public void start() {
        System.out.println("==================================================");
        System.out.println("   GAME SERVER: SƠN TINH THỦY TINH 2V2");
        System.out.println("   Cổng TCP: " + tcpPort + " (Đồng bộ Balo, Cướp/Nộp Lễ, Kỹ Năng)");
        System.out.println("   Cổng UDP: " + udpPort + " (Truyền nhận tọa độ Realtime 40Hz)");
        System.out.println("   Cơ chế: Inventory State Sync + Quái Boss AI");
        System.out.println("==================================================");

        try {
            udpSocket = new DatagramSocket(udpPort);
            new Thread(this::runUDPLoop, "GameServer-UDP").start();

            tcpServerSocket = new ServerSocket(tcpPort);
            new Thread(this::runTCPLoop, "GameServer-TCP").start();

            // Tạo sẵn 1 phòng chơi mặc định để có thể kết nối ngay lập tức nếu cần
            getOrCreateRoom("DEFAULT_ROOM");

        } catch (IOException e) {
            System.err.println("[GameServer] Lỗi khởi động: " + e.getMessage());
        }
    }

    public synchronized GameRoom getOrCreateRoom(String roomToken) {
        return activeRooms.computeIfAbsent(roomToken, token -> {
            System.out.println("[GameServer] Mở phòng thi đấu mới: " + token);
            GameRoom room = new GameRoom(token, udpSocket);
            room.startLoop();
            return room;
        });
    }

    private void runTCPLoop() {
        while (isRunning && !tcpServerSocket.isClosed()) {
            try {
                Socket clientSocket = tcpServerSocket.accept();
                clientSocket.setTcpNoDelay(true);
                new Thread(() -> handleTCPClient(clientSocket), "GameTCPClient-" + clientSocket.getRemoteSocketAddress()).start();
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("[GameServer TCP] Lỗi accept: " + e.getMessage());
                }
            }
        }
    }

    private void handleTCPClient(Socket socket) {
        int boundPlayerId = -1;
        GameRoom boundRoom = null;

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

            while (isRunning && !socket.isClosed()) {
                CustomPacket packet = CustomPacket.readFromTCP(in);
                switch (packet.getType()) {
                    case GAME_HANDSHAKE_REQ -> {
                        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet.getPayload()));
                        String roomToken = dis.readUTF();
                        int playerId = dis.readInt();
                        String username = dis.readUTF();
                        byte teamId = dis.readByte();
                        String roleName = dis.readUTF();

                        boundPlayerId = playerId;
                        boundRoom = getOrCreateRoom(roomToken);
                        boundRoom.registerPlayer(playerId, username, Team.fromId(teamId), roleName, false);
                        boundRoom.bindPlayerTCP(playerId, socket, out);

                        // Trả lời GAME_HANDSHAKE_RESP
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(baos);
                        dos.writeBoolean(true);
                        dos.writeUTF("Gia nhập phòng chơi thành công!");
                        dos.writeInt(playerId);

                        CustomPacket resp = new CustomPacket(PacketType.GAME_HANDSHAKE_RESP, baos.toByteArray());
                        out.write(resp.toBytes());
                        out.flush();

                        boundRoom.broadcastInventorySync();
                        boundRoom.broadcastSystemChat(username + " (" + roleName + ") đã vào trận địa!");
                        System.out.println("[GameServer TCP] Handshake thành công: " + username + " (Player " + playerId + ", Room " + roomToken + ")");
                    }

                    case ACTION_ATTACK_REQ -> {
                        if (boundRoom != null && boundPlayerId != -1) {
                            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet.getPayload()));
                            float angle = dis.readFloat();
                            boundRoom.handlePlayerAttack(boundPlayerId, angle);
                        }
                    }

                    case ACTION_SKILL_REQ -> {
                        if (boundRoom != null && boundPlayerId != -1) {
                            boundRoom.handleSpecialSkill(boundPlayerId);
                        }
                    }

                    case ACTION_INTERACT_REQ -> {
                        if (boundRoom != null && boundPlayerId != -1) {
                            boundRoom.handleInteract(boundPlayerId);
                        }
                    }

                    case CHAT_MSG -> {
                        if (boundRoom != null) {
                            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet.getPayload()));
                            String sender = dis.readUTF();
                            String msg = dis.readUTF();
                            boundRoom.broadcastSystemChat(sender + ": " + msg);
                        }
                    }

                    case PING -> {
                        CustomPacket pong = new CustomPacket(PacketType.PONG, new byte[0]);
                        out.write(pong.toBytes());
                        out.flush();
                    }
                    default -> {}
                }
            }
        } catch (EOFException | SocketException ignored) {
        } catch (Exception e) {
            System.err.println("[GameServer TCP] Ngoại lệ kết nối client: " + e.getMessage());
        }
    }

    private void runUDPLoop() {
        byte[] buffer = new byte[4096];
        while (isRunning && !udpSocket.isClosed()) {
            try {
                DatagramPacket dpacket = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(dpacket);

                CustomPacket packet = CustomPacket.fromBytes(dpacket.getData(), dpacket.getLength());
                if (packet.getType() == PacketType.UDP_CLIENT_INPUT) {
                    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet.getPayload()));
                    String roomToken = dis.readUTF();
                    int playerId = dis.readInt();
                    float x = dis.readFloat();
                    float y = dis.readFloat();
                    float vx = dis.readFloat();
                    float vy = dis.readFloat();
                    float angle = dis.readFloat();

                    GameRoom room = activeRooms.get(roomToken);
                    if (room != null) {
                        room.bindPlayerUDP(playerId, dpacket.getAddress(), dpacket.getPort());
                        room.updatePlayerInput(playerId, x, y, vx, vy, angle);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public static void main(String[] args) {
        int tcpPort = Constants.GAME_TCP_PORT;
        int udpPort = Constants.GAME_UDP_PORT;
        GameServerMain server = new GameServerMain(tcpPort, udpPort);
        server.start();
    }
}
