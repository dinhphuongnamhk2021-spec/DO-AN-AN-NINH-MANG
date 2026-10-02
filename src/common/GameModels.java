package common;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GameModels {

    public enum Team {
        SON_TINH(1, "Phe Sơn Tinh", 0xE67E22), // Màu cam đất/núi
        THUY_TINH(2, "Phe Thủy Tinh", 0x2980B9); // Màu xanh dương/biển

        private final int id;
        private final String name;
        private final int colorRgb;

        Team(int id, String name, int colorRgb) {
            this.id = id;
            this.name = name;
            this.colorRgb = colorRgb;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public int getColorRgb() { return colorRgb; }

        public static Team fromId(int id) {
            return id == 1 ? SON_TINH : THUY_TINH;
        }
    }

    public enum TributeType {
        VOI_CHIN_NGA(1, "Voi Chín Ngà", 0xF39C12),     // Vàng kim
        GA_CHIN_CUA(2, "Gà Chín Cựa", 0xE74C3C),       // Đỏ son
        NGUA_CHIN_HONG_MAO(3, "Ngựa Chín Hồng Mao", 0x9B59B6); // Tím hồng

        private final int id;
        private final String displayName;
        private final int colorRgb;

        TributeType(int id, String displayName, int colorRgb) {
            this.id = id;
            this.displayName = displayName;
            this.colorRgb = colorRgb;
        }

        public int getId() { return id; }
        public String getDisplayName() { return displayName; }
        public int getColorRgb() { return colorRgb; }

        public static TributeType fromId(int id) {
            for (TributeType t : values()) {
                if (t.id == id) return t;
            }
            return VOI_CHIN_NGA;
        }
    }

    /**
     * Trạng thái 1 món Sính Lễ
     */
    public static class TributeItem {
        public int id;
        public TributeType type;
        public float x;
        public float y;
        public int carriedByPlayerId; // -1 nếu đang nằm trên mặt đất
        public boolean isDelivered;   // true nếu đã nộp thành công tại Cung điện Vua Hùng
        public Team deliveredByTeam;  // Đội đã nộp

        public TributeItem(int id, TributeType type, float x, float y) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.carriedByPlayerId = -1;
            this.isDelivered = false;
            this.deliveredByTeam = null;
        }

        public void writeToStream(DataOutputStream out) throws IOException {
            out.writeInt(id);
            out.writeByte(type.getId());
            out.writeFloat(x);
            out.writeFloat(y);
            out.writeInt(carriedByPlayerId);
            out.writeBoolean(isDelivered);
            out.writeInt(deliveredByTeam != null ? deliveredByTeam.getId() : 0);
        }

        public static TributeItem readFromStream(DataInputStream in) throws IOException {
            int id = in.readInt();
            TributeType type = TributeType.fromId(in.readByte());
            float x = in.readFloat();
            float y = in.readFloat();
            TributeItem item = new TributeItem(id, type, x, y);
            item.carriedByPlayerId = in.readInt();
            item.isDelivered = in.readBoolean();
            int teamId = in.readInt();
            item.deliveredByTeam = teamId > 0 ? Team.fromId(teamId) : null;
            return item;
        }
    }

    /**
     * Trạng thái người chơi
     */
    public static class PlayerState {
        public int id;
        public String username;
        public Team team;
        public String roleName; // Sơn Tinh / Thủy Tinh / Hộ Vệ
        public float x;
        public float y;
        public float vx;
        public float vy;
        public float angle;
        public int hp;
        public int maxHp;
        public int mana;
        public int maxMana;
        public float speedMultiplier; // Giảm khi mang sính lễ hoặc ngập lũ
        public List<TributeType> backpack = new ArrayList<>();
        public boolean isDead;
        public float respawnTimer;
        public int kills;
        public int tributesSubmitted;

        public PlayerState(int id, String username, Team team, String roleName, float x, float y) {
            this.id = id;
            this.username = username;
            this.team = team;
            this.roleName = roleName;
            this.x = x;
            this.y = y;
            this.hp = Constants.BASE_PLAYER_HP;
            this.maxHp = Constants.BASE_PLAYER_HP;
            this.mana = Constants.BASE_PLAYER_MANA;
            this.maxMana = Constants.BASE_PLAYER_MANA;
            this.speedMultiplier = 1.0f;
            this.isDead = false;
        }

        public void writeToStream(DataOutputStream out) throws IOException {
            out.writeInt(id);
            out.writeUTF(username);
            out.writeByte(team.getId());
            out.writeUTF(roleName);
            out.writeFloat(x);
            out.writeFloat(y);
            out.writeFloat(vx);
            out.writeFloat(vy);
            out.writeFloat(angle);
            out.writeInt(hp);
            out.writeInt(maxHp);
            out.writeInt(mana);
            out.writeInt(maxMana);
            out.writeFloat(speedMultiplier);
            out.writeBoolean(isDead);
            out.writeFloat(respawnTimer);
            out.writeInt(kills);
            out.writeInt(tributesSubmitted);
            
            // Balo sính lễ
            out.writeByte(backpack.size());
            for (TributeType t : backpack) {
                out.writeByte(t.getId());
            }
        }

        public static PlayerState readFromStream(DataInputStream in) throws IOException {
            int id = in.readInt();
            String username = in.readUTF();
            Team team = Team.fromId(in.readByte());
            String roleName = in.readUTF();
            float x = in.readFloat();
            float y = in.readFloat();
            PlayerState p = new PlayerState(id, username, team, roleName, x, y);
            p.vx = in.readFloat();
            p.vy = in.readFloat();
            p.angle = in.readFloat();
            p.hp = in.readInt();
            p.maxHp = in.readInt();
            p.mana = in.readInt();
            p.maxMana = in.readInt();
            p.speedMultiplier = in.readFloat();
            p.isDead = in.readBoolean();
            p.respawnTimer = in.readFloat();
            p.kills = in.readInt();
            p.tributesSubmitted = in.readInt();

            int backpackSize = in.readByte();
            p.backpack.clear();
            for (int i = 0; i < backpackSize; i++) {
                p.backpack.add(TributeType.fromId(in.readByte()));
            }
            return p;
        }
    }

    /**
     * Quái rừng / Boss canh giữ sính lễ
     */
    public static class MonsterState {
        public int id;
        public String name;
        public TributeType guardedTribute; // Sính lễ sẽ rơi ra khi bị tiêu diệt
        public float x;
        public float y;
        public float vx;
        public float vy;
        public int hp;
        public int maxHp;
        public boolean isAlive;
        public int targetPlayerId; // Đang đuổi theo ai (-1 nếu đang tuần tra)

        public MonsterState(int id, String name, TributeType tribute, float x, float y, int maxHp) {
            this.id = id;
            this.name = name;
            this.guardedTribute = tribute;
            this.x = x;
            this.y = y;
            this.hp = maxHp;
            this.maxHp = maxHp;
            this.isAlive = true;
            this.targetPlayerId = -1;
        }

        public void writeToStream(DataOutputStream out) throws IOException {
            out.writeInt(id);
            out.writeUTF(name);
            out.writeByte(guardedTribute != null ? guardedTribute.getId() : 0);
            out.writeFloat(x);
            out.writeFloat(y);
            out.writeInt(hp);
            out.writeInt(maxHp);
            out.writeBoolean(isAlive);
            out.writeInt(targetPlayerId);
        }

        public static MonsterState readFromStream(DataInputStream in) throws IOException {
            int id = in.readInt();
            String name = in.readUTF();
            byte tributeId = in.readByte();
            TributeType tribute = tributeId > 0 ? TributeType.fromId(tributeId) : null;
            float x = in.readFloat();
            float y = in.readFloat();
            int hp = in.readInt();
            int maxHp = in.readInt();
            MonsterState m = new MonsterState(id, name, tribute, x, y, maxHp);
            m.hp = hp;
            m.isAlive = in.readBoolean();
            m.targetPlayerId = in.readInt();
            return m;
        }
    }

    /**
     * Đạn / Kỹ năng bay trên không gian
     */
    public static class ProjectileState {
        public int id;
        public int ownerId;
        public Team team;
        public float x;
        public float y;
        public float vx;
        public float vy;
        public int damage;
        public int skillType; // 1: Đạn đá Sơn Tinh, 2: Thủy kích Thủy Tinh, 3: Cào xé Boss

        public ProjectileState(int id, int ownerId, Team team, float x, float y, float vx, float vy, int damage, int skillType) {
            this.id = id;
            this.ownerId = ownerId;
            this.team = team;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.damage = damage;
            this.skillType = skillType;
        }

        public void writeToStream(DataOutputStream out) throws IOException {
            out.writeInt(id);
            out.writeInt(ownerId);
            out.writeByte(team != null ? team.getId() : 0);
            out.writeFloat(x);
            out.writeFloat(y);
            out.writeFloat(vx);
            out.writeFloat(vy);
            out.writeInt(damage);
            out.writeByte(skillType);
        }

        public static ProjectileState readFromStream(DataInputStream in) throws IOException {
            int id = in.readInt();
            int ownerId = in.readInt();
            int teamId = in.readByte();
            Team team = teamId > 0 ? Team.fromId(teamId) : null;
            float x = in.readFloat();
            float y = in.readFloat();
            float vx = in.readFloat();
            float vy = in.readFloat();
            int dmg = in.readInt();
            int type = in.readByte();
            return new ProjectileState(id, ownerId, team, x, y, vx, vy, dmg, type);
        }
    }

    /**
     * Vùng ngập nước lũ do Thủy Tinh hô hoán
     */
    public static class FloodZone {
        public boolean isActive;
        public float waterLevel; // 0.0 -> 1.0
        public float centerX;
        public float centerY;
        public float radius;

        public FloodZone() {
            this.isActive = false;
            this.waterLevel = 0.0f;
            this.centerX = 1300;
            this.centerY = 1100;
            this.radius = 650;
        }
    }
}
