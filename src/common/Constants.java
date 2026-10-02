package common;

public class Constants {
    // Phiên bản và Magic Bytes cho gói tin tùy chỉnh
    public static final byte MAGIC_BYTE_1 = 0x53; // 'S'
    public static final byte MAGIC_BYTE_2 = 0x54; // 'T' (Sơn Tinh Thủy Tinh)
    public static final short PROTOCOL_VERSION = 1;

    // Cổng mạng mặc định
    public static final int MASTER_TCP_PORT = 8888;
    public static final int GAME_TCP_PORT = 9999;
    public static final int GAME_UDP_PORT = 9998;
    public static final String DEFAULT_HOST = "127.0.0.1";

    // Kích thước bản đồ thế giới (World Map 2D)
    public static final int MAP_WIDTH = 2600;
    public static final int MAP_HEIGHT = 2000;

    // Tọa độ các vị trí trọng yếu trên bản đồ
    // Cung điện Vua Hùng (Vị trí nộp sính lễ - Phía Bắc)
    public static final int SHRINE_X = 1300;
    public static final int SHRINE_Y = 260;
    public static final int SHRINE_RADIUS = 150;

    // Căn cứ Sơn Tinh (Tây Nam)
    public static final int SPAWN_SON_TINH_X = 350;
    public static final int SPAWN_SON_TINH_Y = 1750;

    // Căn cứ Thủy Tinh (Đông Nam)
    public static final int SPAWN_THUY_TINH_X = 2250;
    public static final int SPAWN_THUY_TINH_Y = 1750;

    // Vị trí 3 Boss giữ sính lễ trong Rừng Thiêng
    public static final int BOSS_VOI_X = 750;
    public static final int BOSS_VOI_Y = 950;

    public static final int BOSS_GA_X = 1300;
    public static final int BOSS_GA_Y = 1150;

    public static final int BOSS_NGUA_X = 1850;
    public static final int BOSS_NGUA_Y = 950;

    // Chỉ số người chơi
    public static final float BASE_PLAYER_SPEED = 210.0f; // pixel / giây
    public static final int BASE_PLAYER_HP = 500;
    public static final int BASE_PLAYER_MANA = 100;
    public static final int RESPAWN_TIME_SEC = 5;

    // Chỉ số sính lễ & Balo
    public static final float SPEED_DEBUFF_PER_TRIBUTE = 0.20f; // Mỗi món giảm 20% tốc độ
    public static final float MAX_SPEED_DEBUFF = 0.50f; // Tối đa giảm 50%
    public static final int PICKUP_RADIUS = 75;

    // Tần số cập nhật game loop (Ticks Per Second)
    public static final int GAME_TICK_RATE = 40; // 40 ticks/sec => 25ms/tick
    public static final int UDP_BROADCAST_INTERVAL_MS = 25; // 40Hz
}
