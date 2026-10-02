package master;

import common.SecurityUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AccountManager {
    public static class Account {
        public String username;
        public String hashedPassword;
        public String salt;
        public int eloRating = 1200;
        public int matchesWon = 0;
        public int matchesLost = 0;
        public int tributesDelivered = 0;

        public Account(String username, String hashedPassword, String salt) {
            this.username = username;
            this.hashedPassword = hashedPassword;
            this.salt = salt;
        }

        public String toDataLine() {
            return String.format("%s|%s|%s|%d|%d|%d|%d", 
                username, hashedPassword, salt, eloRating, matchesWon, matchesLost, tributesDelivered);
        }

        public static Account fromDataLine(String line) {
            String[] parts = line.split("\\|");
            if (parts.length >= 3) {
                Account acc = new Account(parts[0], parts[1], parts[2]);
                if (parts.length >= 7) {
                    acc.eloRating = Integer.parseInt(parts[3]);
                    acc.matchesWon = Integer.parseInt(parts[4]);
                    acc.matchesLost = Integer.parseInt(parts[5]);
                    acc.tributesDelivered = Integer.parseInt(parts[6]);
                }
                return acc;
            }
            return null;
        }
    }

    private final Map<String, Account> accounts = new HashMap<>();
    private final File dataFile;

    public AccountManager(String filePath) {
        this.dataFile = new File(filePath);
        loadAccounts();
        initDefaultAccountsIfEmpty();
    }

    private synchronized void loadAccounts() {
        if (!dataFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                Account acc = Account.fromDataLine(line);
                if (acc != null) {
                    accounts.put(acc.username.toLowerCase(), acc);
                }
            }
        } catch (IOException e) {
            System.err.println("[AccountManager] Lỗi đọc file tài khoản: " + e.getMessage());
        }
    }

    public synchronized void saveAccounts() {
        File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dataFile), StandardCharsets.UTF_8))) {
            writer.write("# Database Tai Khoan Son Tinh Thuy Tinh\n");
            writer.write("# username|hashedPassword|salt|elo|won|lost|tributes\n");
            for (Account acc : accounts.values()) {
                writer.write(acc.toDataLine());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("[AccountManager] Lỗi lưu file tài khoản: " + e.getMessage());
        }
    }

    private void initDefaultAccountsIfEmpty() {
        if (accounts.isEmpty()) {
            // Tạo sẵn một số tài khoản mẫu phục vụ thi đấu & bảo vệ đồ án
            registerAccount("sontinh", "123456");
            registerAccount("thuytinh", "123456");
            registerAccount("hove1", "123456");
            registerAccount("hove2", "123456");
            registerAccount("admin", "admin123");

            // Mock điểm ban đầu cho bảng xếp hạng phong phú
            Account st = accounts.get("sontinh");
            if (st != null) { st.eloRating = 1650; st.matchesWon = 24; st.matchesLost = 5; st.tributesDelivered = 48; }
            Account tt = accounts.get("thuytinh");
            if (tt != null) { tt.eloRating = 1580; tt.matchesWon = 21; tt.matchesLost = 8; tt.tributesDelivered = 42; }
            saveAccounts();
        }
    }

    public synchronized boolean registerAccount(String username, String plainPassword) {
        if (username == null || plainPassword == null) return false;
        username = username.trim();
        if (username.length() < 3 || plainPassword.length() < 4) return false;

        String key = username.toLowerCase();
        if (accounts.containsKey(key)) return false;

        String salt = SecurityUtils.generateSalt();
        String hashed = SecurityUtils.hashPassword(plainPassword, salt);
        Account acc = new Account(username, hashed, salt);
        accounts.put(key, acc);
        saveAccounts();
        return true;
    }

    public synchronized Account authenticate(String username, String plainPassword) {
        if (username == null || plainPassword == null) return null;
        Account acc = accounts.get(username.trim().toLowerCase());
        if (acc == null) return null;

        if (SecurityUtils.verifyPassword(plainPassword, acc.salt, acc.hashedPassword)) {
            return acc;
        }
        return null;
    }

    public synchronized List<Account> getTopLeaderboard(int limit) {
        List<Account> list = new ArrayList<>(accounts.values());
        list.sort((a, b) -> Integer.compare(b.eloRating, a.eloRating));
        if (list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    public synchronized void updateMatchResult(String username, boolean won, int tributesCount) {
        Account acc = accounts.get(username.toLowerCase());
        if (acc != null) {
            if (won) {
                acc.matchesWon++;
                acc.eloRating += 25;
            } else {
                acc.matchesLost++;
                acc.eloRating = Math.max(800, acc.eloRating - 15);
            }
            acc.tributesDelivered += tributesCount;
            saveAccounts();
        }
    }
}
