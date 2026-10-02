package common;

public enum PacketType {
    // --- GIAO TIẾP MASTER SERVER (TCP) ---
    LOGIN_REQ((byte) 0x01),
    LOGIN_RESP((byte) 0x02),
    REGISTER_REQ((byte) 0x03),
    REGISTER_RESP((byte) 0x04),
    LEADERBOARD_REQ((byte) 0x05),
    LEADERBOARD_RESP((byte) 0x06),
    MATCHMAKING_QUEUE_REQ((byte) 0x07),
    MATCHMAKING_STATUS_RESP((byte) 0x08),
    MATCH_FOUND_RESP((byte) 0x09),

    // --- GIAO TIẾP GAME SERVER (TCP - RELIABLE) ---
    GAME_HANDSHAKE_REQ((byte) 0x10),
    GAME_HANDSHAKE_RESP((byte) 0x11),
    GAME_START_NOTIFY((byte) 0x12),
    
    // Thao tác chiến đấu và tương tác (TCP)
    ACTION_ATTACK_REQ((byte) 0x20),
    ACTION_SKILL_REQ((byte) 0x21),
    ACTION_INTERACT_REQ((byte) 0x22), // Nhặt sính lễ, Nộp lễ tại Vua Hùng
    
    // Đồng bộ trạng thái và sự kiện (TCP)
    INVENTORY_SYNC_NOTIFY((byte) 0x30), // Cập nhật sính lễ trong balo và trên đất
    FLOOD_EVENT_NOTIFY((byte) 0x31),    // Thủy tinh hô mưa gọi lũ
    COMBAT_EVENT_NOTIFY((byte) 0x32),   // Bị hạ gục, rơi balo, sính lễ bị cướp
    GAME_VICTORY_NOTIFY((byte) 0x33),   // Nộp đủ 3 món, chiến thắng
    CHAT_MSG((byte) 0x34),
    
    // --- GIAO TIẾP GAME SERVER (UDP - LOW LATENCY REALTIME) ---
    UDP_CLIENT_INPUT((byte) 0x50),      // Tọa độ người chơi, hướng di chuyển, góc quay
    UDP_WORLD_SNAPSHOT((byte) 0x51),    // Snapshot toàn bộ player, quái, đạn bay
    
    // Heartbeat / Ping Pong
    PING((byte) 0x7E),
    PONG((byte) 0x7F),
    ERROR_RESP((byte) 0xFF);

    private final byte code;

    PacketType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static PacketType fromCode(byte code) {
        for (PacketType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return ERROR_RESP;
    }
}
