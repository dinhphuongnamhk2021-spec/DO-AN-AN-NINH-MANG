package master;

import common.Constants;
import common.CustomPacket;
import common.PacketType;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MasterServer {
    private final int port;
    private final AccountManager accountManager;
    private final Matchmaker matchmaker;
    private final List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
    private volatile boolean isRunning = true;

    public MasterServer(int port) {
        this.port = port;
        this.accountManager = new AccountManager("data/accounts.txt");
        this.matchmaker = new Matchmaker();
    }

    public void start() {
        System.out.println("==================================================");
        System.out.println("   MASTER SERVER: SƠN TINH THỦY TINH 2V2");
        System.out.println("   Cổng TCP: " + port);
        System.out.println("   Xác thực: SHA-256 + Salted Hashing");
        System.out.println("   Giao thức: Custom Binary Packet Protocol");
        System.out.println("==================================================");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);
                ClientHandler handler = new ClientHandler(clientSocket);
                connectedClients.add(handler);
                new Thread(handler, "MasterClient-" + clientSocket.getRemoteSocketAddress()).start();
            }
        } catch (IOException e) {
            if (isRunning) {
                System.err.println("[MasterServer] Lỗi ServerSocket: " + e.getMessage());
            }
        }
    }

    public AccountManager getAccountManager() {
        return accountManager;
    }

    public Matchmaker getMatchmaker() {
        return matchmaker;
    }

    public class ClientHandler implements Runnable {
        private final Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private String authenticatedUser = null;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                while (isRunning && !socket.isClosed()) {
                    CustomPacket packet = CustomPacket.readFromTCP(in);
                    handlePacket(packet);
                }
            } catch (EOFException | SocketException e) {
                // Client ngắt kết nối
            } catch (Exception e) {
                System.err.println("[MasterClient] Ngoại lệ xử lý gói tin: " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        private void handlePacket(CustomPacket packet) throws IOException {
            switch (packet.getType()) {
                case LOGIN_REQ -> handleLogin(packet);
                case REGISTER_REQ -> handleRegister(packet);
                case LEADERBOARD_REQ -> handleLeaderboard(packet);
                case MATCHMAKING_QUEUE_REQ -> handleMatchmakingQueue(packet);
                case PING -> sendPacket(new CustomPacket(PacketType.PONG, new byte[0]));
                default -> System.out.println("[MasterClient] Bỏ qua gói tin không xác định: " + packet.getType());
            }
        }

        private void handleLogin(CustomPacket packet) throws IOException {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet.getPayload()));
            String username = dis.readUTF();
            String password = dis.readUTF();

            AccountManager.Account acc = accountManager.authenticate(username, password);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            if (acc != null) {
                this.authenticatedUser = acc.username;
                dos.writeBoolean(true);
                dos.writeUTF("Đăng nhập thành công! Chào mừng dũng sĩ " + acc.username);
                dos.writeInt(acc.eloRating);
                dos.writeInt(acc.matchesWon);
                dos.writeInt(acc.matchesLost);
                dos.writeInt(acc.tributesDelivered);
                System.out.println("[Master] Đăng nhập thành công: " + acc.username + " (Elo: " + acc.eloRating + ")");
            } else {
                dos.writeBoolean(false);
                dos.writeUTF("Tên đăng nhập hoặc mật khẩu không chính xác!");
                System.out.println("[Master] Đăng nhập thất bại: " + username);
            }
            sendPacket(new CustomPacket(PacketType.LOGIN_RESP, baos.toByteArray()));
        }

        private void handleRegister(CustomPacket packet) throws IOException {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet.getPayload()));
            String username = dis.readUTF();
            String password = dis.readUTF();

            boolean ok = accountManager.registerAccount(username, password);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            if (ok) {
                dos.writeBoolean(true);
                dos.writeUTF("Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay.");
                System.out.println("[Master] Đăng ký mới thành công: " + username);
            } else {
                dos.writeBoolean(false);
                dos.writeUTF("Tên đăng nhập đã tồn tại hoặc mật khẩu quá ngắn!");
            }
            sendPacket(new CustomPacket(PacketType.REGISTER_RESP, baos.toByteArray()));
        }

        private void handleLeaderboard(CustomPacket packet) throws IOException {
            List<AccountManager.Account> topList = accountManager.getTopLeaderboard(10);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeInt(topList.size());
            for (AccountManager.Account acc : topList) {
                dos.writeUTF(acc.username);
                dos.writeInt(acc.eloRating);
                dos.writeInt(acc.matchesWon);
                dos.writeInt(acc.matchesLost);
                dos.writeInt(acc.tributesDelivered);
            }
            sendPacket(new CustomPacket(PacketType.LEADERBOARD_RESP, baos.toByteArray()));
        }

        private void handleMatchmakingQueue(CustomPacket packet) throws IOException {
            if (authenticatedUser == null) {
                return;
            }
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet.getPayload()));
            boolean isJoining = dis.readBoolean();
            int preferredTeam = dis.readByte(); // 0: Auto, 1: Son Tinh, 2: Thuy Tinh
            boolean allowBots = dis.readBoolean();

            if (isJoining) {
                matchmaker.enqueue(new Matchmaker.QueueRequest(authenticatedUser, preferredTeam, allowBots, this));
            } else {
                matchmaker.dequeue(authenticatedUser);
            }
        }

        public synchronized void sendPacket(CustomPacket packet) {
            try {
                if (out != null && !socket.isClosed()) {
                    out.write(packet.toBytes());
                    out.flush();
                }
            } catch (IOException e) {
                cleanup();
            }
        }

        private void cleanup() {
            connectedClients.remove(this);
            if (authenticatedUser != null) {
                matchmaker.dequeue(authenticatedUser);
            }
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
        }
    }

    public static void main(String[] args) {
        int port = Constants.MASTER_TCP_PORT;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (Exception ignored) {}
        }
        MasterServer server = new MasterServer(port);
        server.start();
    }
}
