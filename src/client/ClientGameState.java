package client;

import common.Constants;
import common.GameModels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientGameState {
    public int myPlayerId = -1;
    public String myUsername = "";
    public Team myTeam = Team.SON_TINH;
    public String myRoleName = "";
    public String roomToken = "";

    public final Map<Integer, PlayerState> players = new ConcurrentHashMap<>();
    public final List<MonsterState> monsters = new CopyOnWriteArrayList<>();
    public final List<ProjectileState> projectiles = new CopyOnWriteArrayList<>();
    public final List<TributeItem> tributes = new CopyOnWriteArrayList<>();
    public final FloodZone floodZone = new FloodZone();

    public int scoreSonTinh = 0;
    public int scoreThuyTinh = 0;
    public boolean isGameOver = false;
    public Team winningTeam = null;
    public String victoryMessage = "";

    public final List<String> combatLogs = new CopyOnWriteArrayList<>();
    public static final int MAX_LOGS = 6;

    public ClientGameState() {
        // Khởi tạo sẵn 3 sính lễ mặc định
        tributes.add(new TributeItem(1, TributeType.VOI_CHIN_NGA, -1000, -1000));
        tributes.add(new TributeItem(2, TributeType.GA_CHIN_CUA, -1000, -1000));
        tributes.add(new TributeItem(3, TributeType.NGUA_CHIN_HONG_MAO, -1000, -1000));
    }

    public PlayerState getMyPlayer() {
        return players.get(myPlayerId);
    }

    public void addCombatLog(String log) {
        combatLogs.add(log);
        while (combatLogs.size() > MAX_LOGS) {
            combatLogs.remove(0);
        }
    }
}
