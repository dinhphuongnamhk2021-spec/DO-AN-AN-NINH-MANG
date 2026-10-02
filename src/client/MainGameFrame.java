package client;

import common.Constants;
import common.GameModels.Team;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class MainGameFrame extends JFrame implements ClientNetworkManager.NetworkListener {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rootPanel = new JPanel(cardLayout);

    private final ClientGameState gameState = new ClientGameState();
    private final ClientNetworkManager networkManager;

    // Login Panel Components
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JLabel lblStatus;

    // Lobby Panel Components
    private JLabel lblPlayerInfo;
    private JLabel lblPlayerStats;
    private JRadioButton rbAny;
    private JRadioButton rbSonTinh;
    private JRadioButton rbThuyTinh;
    private JButton btnFindMatch;
    private JButton btnPlayWithBots;
    private JLabel lblQueueStatus;

    // Game Panel
    private GamePanel gamePanel;

    public MainGameFrame() {
        super("SƠN TINH THỦY TINH - CUỘC ĐUA SÍNH LỄ 2V2");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setMinimumSize(new Dimension(950, 650));
        setLocationRelativeTo(null);

        networkManager = new ClientNetworkManager(gameState);
        networkManager.setListener(this);

        initUI();

        // Kết nối Master Server khi mở ứng dụng
        SwingUtilities.invokeLater(() -> {
            boolean connected = networkManager.connectMaster(Constants.DEFAULT_HOST, Constants.MASTER_TCP_PORT);
            if (!connected) {
                lblStatus.setText("⚠️ Chưa khởi động Master Server (Port " + Constants.MASTER_TCP_PORT + ")!");
                lblStatus.setForeground(new Color(231, 76, 60));
            } else {
                lblStatus.setText("Đã kết nối Master Server. Vui lòng đăng nhập.");
                lblStatus.setForeground(new Color(46, 204, 113));
            }
        });
    }

    private void initUI() {
        rootPanel.add(createLoginPanel(), "LOGIN");
        rootPanel.add(createLobbyPanel(), "LOBBY");

        gamePanel = new GamePanel(gameState, networkManager);
        rootPanel.add(gamePanel, "GAME");

        add(rootPanel);
        cardLayout.show(rootPanel, "LOGIN");
    }

    /**
     * Giao diện Đăng Nhập / Đăng Ký Thần Thoại Cổ Phong
     */
    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                // Nền gradient thần bí
                GradientPaint gp = new GradientPaint(0, 0, new Color(18, 22, 28), 
                                                    getWidth(), getHeight(), new Color(28, 40, 36));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Trang trí họa tiết văn hóa Đông Sơn
                g2.setColor(new Color(218, 165, 32, 25));
                g2.setStroke(new BasicStroke(6));
                g2.drawOval(getWidth() / 2 - 250, getHeight() / 2 - 250, 500, 500);
                g2.drawOval(getWidth() / 2 - 180, getHeight() / 2 - 180, 360, 360);
            }
        };

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(25, 30, 38, 240));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(218, 165, 32), 2, true),
            new EmptyBorder(30, 45, 35, 45)
        ));
        card.setPreferredSize(new Dimension(460, 520));

        JLabel lblTitle = new JLabel("SƠN TINH THỦY TINH");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 26));
        lblTitle.setForeground(new Color(255, 215, 0));
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSubtitle = new JLabel("CUỘC ĐUA SÍNH LỄ 2V2");
        lblSubtitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblSubtitle.setForeground(new Color(230, 126, 34));
        lblSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblDesc = new JLabel("Đồ án Hệ Thống Game Mạng TCP/UDP Đa Luồng");
        lblDesc.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        lblDesc.setForeground(new Color(180, 180, 180));
        lblDesc.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(lblTitle);
        card.add(Box.createRigidArea(new Dimension(0, 5)));
        card.add(lblSubtitle);
        card.add(Box.createRigidArea(new Dimension(0, 4)));
        card.add(lblDesc);
        card.add(Box.createRigidArea(new Dimension(0, 30)));

        // Input Username
        JLabel lblUser = new JLabel("Tên Dũng Sĩ:");
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblUser.setForeground(Color.WHITE);
        lblUser.setAlignmentX(Component.CENTER_ALIGNMENT);

        txtUsername = new JTextField("sontinh");
        txtUsername.setMaximumSize(new Dimension(320, 38));
        txtUsername.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtUsername.setBackground(new Color(36, 42, 52));
        txtUsername.setForeground(Color.WHITE);
        txtUsername.setCaretColor(Color.YELLOW);
        txtUsername.setBorder(BorderFactory.createLineBorder(new Color(70, 80, 95)));

        // Input Password
        JLabel lblPass = new JLabel("Mật Khẩu:");
        lblPass.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblPass.setForeground(Color.WHITE);
        lblPass.setAlignmentX(Component.CENTER_ALIGNMENT);

        txtPassword = new JPasswordField("123456");
        txtPassword.setMaximumSize(new Dimension(320, 38));
        txtPassword.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtPassword.setBackground(new Color(36, 42, 52));
        txtPassword.setForeground(Color.WHITE);
        txtPassword.setCaretColor(Color.YELLOW);
        txtPassword.setBorder(BorderFactory.createLineBorder(new Color(70, 80, 95)));

        card.add(lblUser);
        card.add(Box.createRigidArea(new Dimension(0, 5)));
        card.add(txtUsername);
        card.add(Box.createRigidArea(new Dimension(0, 15)));
        card.add(lblPass);
        card.add(Box.createRigidArea(new Dimension(0, 5)));
        card.add(txtPassword);
        card.add(Box.createRigidArea(new Dimension(0, 25)));

        // Buttons
        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        btnPanel.setOpaque(false);
        btnPanel.setMaximumSize(new Dimension(320, 42));

        JButton btnLogin = new JButton("Đăng Nhập");
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnLogin.setBackground(new Color(230, 126, 34));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setFocusPainted(false);
        btnLogin.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        btnLogin.addActionListener(e -> performLogin());

        JButton btnRegister = new JButton("Đăng Ký");
        btnRegister.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnRegister.setBackground(new Color(41, 128, 185));
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setFocusPainted(false);
        btnRegister.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        btnRegister.addActionListener(e -> performRegister());

        btnPanel.add(btnLogin);
        btnPanel.add(btnRegister);
        card.add(btnPanel);

        // Status Label
        lblStatus = new JLabel(" ");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(Color.YELLOW);
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(Box.createRigidArea(new Dimension(0, 18)));
        card.add(lblStatus);

        panel.add(card);
        return panel;
    }

    /**
     * Giao diện Sảnh Chờ & Ghép Trận (Lobby)
     */
    private JPanel createLobbyPanel() {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                GradientPaint gp = new GradientPaint(0, 0, new Color(16, 20, 28), getWidth(), getHeight(), new Color(24, 32, 40));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(25, 40, 20, 40));

        JPanel userInfoPanel = new JPanel(new GridLayout(2, 1));
        userInfoPanel.setOpaque(false);

        lblPlayerInfo = new JLabel("Dũng sĩ: ...");
        lblPlayerInfo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblPlayerInfo.setForeground(new Color(255, 215, 0));

        lblPlayerStats = new JLabel("Elo: 1200 | Thắng/Bại: 0/0 | Sính lễ dâng: 0");
        lblPlayerStats.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblPlayerStats.setForeground(new Color(200, 200, 200));

        userInfoPanel.add(lblPlayerInfo);
        userInfoPanel.add(lblPlayerStats);
        header.add(userInfoPanel, BorderLayout.WEST);

        JButton btnLeaderboard = new JButton("🏆 Bảng Xếp Hạng");
        btnLeaderboard.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLeaderboard.setBackground(new Color(52, 73, 94));
        btnLeaderboard.setForeground(Color.YELLOW);
        btnLeaderboard.setFocusPainted(false);
        btnLeaderboard.addActionListener(e -> networkManager.requestLeaderboard());
        header.add(btnLeaderboard, BorderLayout.EAST);

        panel.add(header, BorderLayout.NORTH);

        // Center: Chế độ ghép trận
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        JPanel matchCard = new JPanel();
        matchCard.setLayout(new BoxLayout(matchCard, BoxLayout.Y_AXIS));
        matchCard.setBackground(new Color(25, 32, 42, 220));
        matchCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(218, 165, 32), 2, true),
            new EmptyBorder(30, 40, 30, 40)
        ));
        matchCard.setPreferredSize(new Dimension(520, 400));

        JLabel lblLobbyTitle = new JLabel("CUỘC ĐUA SÍNH LỄ 2V2");
        lblLobbyTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblLobbyTitle.setForeground(new Color(255, 215, 0));
        lblLobbyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblChooseTeam = new JLabel("Lựa Chọn Phe Tín Ngưỡng:");
        lblChooseTeam.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblChooseTeam.setForeground(Color.WHITE);
        lblChooseTeam.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel teamGroupPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        teamGroupPanel.setOpaque(false);

        rbAny = new JRadioButton("Ngẫu Nhiên", true);
        rbSonTinh = new JRadioButton("Phe Sơn Tinh");
        rbThuyTinh = new JRadioButton("Phe Thủy Tinh");

        rbAny.setForeground(Color.WHITE);
        rbSonTinh.setForeground(new Color(230, 126, 34));
        rbThuyTinh.setForeground(new Color(52, 152, 219));
        rbAny.setOpaque(false);
        rbSonTinh.setOpaque(false);
        rbThuyTinh.setOpaque(false);

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbAny);
        bg.add(rbSonTinh);
        bg.add(rbThuyTinh);

        teamGroupPanel.add(rbAny);
        teamGroupPanel.add(rbSonTinh);
        teamGroupPanel.add(rbThuyTinh);

        matchCard.add(lblLobbyTitle);
        matchCard.add(Box.createRigidArea(new Dimension(0, 20)));
        matchCard.add(lblChooseTeam);
        matchCard.add(teamGroupPanel);
        matchCard.add(Box.createRigidArea(new Dimension(0, 20)));

        // 2 Nút Ghép Trận
        btnFindMatch = new JButton("⚔️ Ghép Trận 2v2 Thời Gian Thực");
        btnFindMatch.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnFindMatch.setBackground(new Color(230, 126, 34));
        btnFindMatch.setForeground(Color.WHITE);
        btnFindMatch.setFocusPainted(false);
        btnFindMatch.setMaximumSize(new Dimension(380, 45));
        btnFindMatch.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnFindMatch.addActionListener(e -> startMatchmaking(false));

        btnPlayWithBots = new JButton("⚡ Chơi Ngay Cùng Bot AI (Solo 2v2)");
        btnPlayWithBots.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnPlayWithBots.setBackground(new Color(39, 174, 96));
        btnPlayWithBots.setForeground(Color.WHITE);
        btnPlayWithBots.setFocusPainted(false);
        btnPlayWithBots.setMaximumSize(new Dimension(380, 45));
        btnPlayWithBots.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnPlayWithBots.addActionListener(e -> startMatchmaking(true));

        lblQueueStatus = new JLabel("Sẵn sàng xuất chiến.");
        lblQueueStatus.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        lblQueueStatus.setForeground(new Color(200, 200, 200));
        lblQueueStatus.setAlignmentX(Component.CENTER_ALIGNMENT);

        matchCard.add(btnFindMatch);
        matchCard.add(Box.createRigidArea(new Dimension(0, 12)));
        matchCard.add(btnPlayWithBots);
        matchCard.add(Box.createRigidArea(new Dimension(0, 25)));
        matchCard.add(lblQueueStatus);

        center.add(matchCard);
        panel.add(center, BorderLayout.CENTER);

        return panel;
    }

    private void performLogin() {
        String u = txtUsername.getText().trim();
        String p = new String(txtPassword.getPassword()).trim();
        if (u.isEmpty() || p.isEmpty()) {
            lblStatus.setText("Vui lòng điền đủ tên tài khoản và mật khẩu!");
            return;
        }
        lblStatus.setText("Đang xác thực thông tin đăng nhập...");
        networkManager.sendLogin(u, p);
    }

    private void performRegister() {
        String u = txtUsername.getText().trim();
        String p = new String(txtPassword.getPassword()).trim();
        if (u.length() < 3 || p.length() < 4) {
            lblStatus.setText("Tên cần ít nhất 3 ký tự, mật khẩu từ 4 ký tự!");
            return;
        }
        lblStatus.setText("Đang đăng ký tài khoản mới...");
        networkManager.sendRegister(u, p);
    }

    private void startMatchmaking(boolean allowBots) {
        int prefTeam = 0;
        if (rbSonTinh.isSelected()) prefTeam = 1;
        if (rbThuyTinh.isSelected()) prefTeam = 2;

        lblQueueStatus.setText("Đang kết nối hàng đợi ghép trận...");
        lblQueueStatus.setForeground(Color.YELLOW);
        networkManager.joinMatchmaking(prefTeam, allowBots);
    }

    // =========================================================================
    // XỬ LÝ SỰ KIỆN TỪ NETWORK CALLBACK
    // =========================================================================
    @Override
    public void onLoginResult(boolean success, String message, int elo, int won, int lost, int tributes) {
        SwingUtilities.invokeLater(() -> {
            if (success) {
                gameState.myUsername = txtUsername.getText().trim();
                lblPlayerInfo.setText("Dũng Sĩ: " + gameState.myUsername);
                lblPlayerStats.setText("Điểm Elo: " + elo + " | Trận Thắng/Bại: " + won + "/" + lost + " | Sính Lễ Đã Nộp: " + tributes);
                cardLayout.show(rootPanel, "LOBBY");
            } else {
                lblStatus.setText(message);
                lblStatus.setForeground(new Color(231, 76, 60));
            }
        });
    }

    @Override
    public void onRegisterResult(boolean success, String message) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(message);
            lblStatus.setForeground(success ? new Color(46, 204, 113) : new Color(231, 76, 60));
        });
    }

    @Override
    public void onLeaderboardReceived(List<String[]> list) {
        SwingUtilities.invokeLater(() -> {
            JDialog dlg = new JDialog(this, "BẢNG XẾP HẠNG THẦN BINH VĂN LANG", true);
            dlg.setSize(600, 420);
            dlg.setLocationRelativeTo(this);

            String[] columns = {"Hạng", "Dũng Sĩ", "Điểm Elo", "Thắng / Bại", "Sính Lễ Đã Nộp"};
            DefaultTableModel model = new DefaultTableModel(columns, 0);
            for (String[] row : list) {
                model.addRow(row);
            }

            JTable table = new JTable(model);
            table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            table.setRowHeight(28);
            table.setBackground(new Color(28, 34, 44));
            table.setForeground(Color.WHITE);
            table.getTableHeader().setBackground(new Color(40, 50, 65));
            table.getTableHeader().setForeground(new Color(255, 215, 0));
            table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));

            DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
            centerRenderer.setHorizontalAlignment(JLabel.CENTER);
            for (int i = 0; i < table.getColumnCount(); i++) {
                table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            }

            JScrollPane scroll = new JScrollPane(table);
            scroll.getViewport().setBackground(new Color(28, 34, 44));
            dlg.add(scroll);
            dlg.setVisible(true);
        });
    }

    @Override
    public void onQueueStatus(int inQueue, int needed, String statusText) {
        SwingUtilities.invokeLater(() -> {
            lblQueueStatus.setText(statusText);
            lblQueueStatus.setForeground(new Color(241, 196, 15));
        });
    }

    @Override
    public void onMatchFound(String roomToken, int myPlayerId, Team myTeam, String myRole, List<String> playerNames) {
        SwingUtilities.invokeLater(() -> {
            lblQueueStatus.setText("Trận đấu bắt đầu! Đang tiến vào chiến trường Phong Châu...");
            // Chuyển sang màn hình Game 2D
            cardLayout.show(rootPanel, "GAME");
            gamePanel.requestFocusInWindow();
        });
    }

    @Override
    public void onGameVictory(Team winningTeam, String message) {
        SwingUtilities.invokeLater(() -> {
            // Màn hình GamePanel sẽ vẽ Victory Overlay
        });
    }

    @Override
    public void onChatMessage(String sender, String message) {
        // Combat log đã được cập nhật vào GameState
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            MainGameFrame frame = new MainGameFrame();
            frame.setVisible(true);
        });
    }
}
