# ĐỒ ÁN LẬP TRÌNH MẠNG & AN NINH MẠNG
# GAME: SƠN TINH THỦY TINH - CUỘC ĐUA SÍNH LỄ (2V2 HYBRID TCP/UDP)

---

## 📖 1. CỐT TRUYỆN & Ý NGHĨA TRÒ CHƠI

### 1.1. Lời Phán Của Vua Hùng (Khởi đầu trận đấu)
Năm Phong Châu thứ 18, Vua Hùng muốn kén chọn người hiền tài tác hợp cùng Mị Nương công chúa. Sơn Tinh (Chúa miền non cao) và Thủy Tinh (Chúa vùng nước thẳm) cùng mang thần lực biến hóa khôn lường đến dâng sớ xin rước dâu. Nhận thấy sức mạnh hai bên ngang ngửa, Vua Hùng truyền lệnh:  
> *"Rừng thiêng hung tợn, quái thú hoành hành. Đội nào tiến vào rừng sâu, tiêu diệt hung thần và thu thập đủ Bộ Ba Sính Lễ (**Voi 9 ngà**, **Gà 9 cựa**, **Ngựa 9 hồng mao**) mang về Cung điện nộp trước, kẻ đó sẽ trở thành Phò mã!"*

### 1.2. Dấn Thân Vào Rừng Thiêng (Giai đoạn Săn Quái - PvE)
- Hai phe Sơn Tinh và Thủy Tinh (mô hình 2v2) tiến sâu vào khu rừng nguyên sinh hoang dã.
- 3 Thủ Lĩnh Quái Rừng trấn giữ 3 món sính lễ:
  1. **Bạch Tượng Thần**: Quái thú khổng lồ canh giữ **Voi Chín Ngà**.
  2. **Hỏa Kê Thần**: Chúa tể rừng lửa canh giữ **Gà Chín Cựa**.
  3. **Xích Ký Thần**: Thần thú phi thiên canh giữ **Ngựa Chín Hồng Mao**.
- Khi thủ lĩnh quái bị hạ gục, sính lễ thần thoại sẽ rơi xuống đất và tỏa hào quang rực rỡ.

### 1.3. Balo Trọng Yếu & Bão Lũ Dâng Trào (Giai đoạn Giao Tranh - PvP)
- **Gánh nặng sính lễ**: Khi nhặt sính lễ vào Balo, linh khí thần thoại đè nặng khiến người mang bị **giảm tốc độ di chuyển** (20% mỗi món, tối đa 50%). Kẻ mang sính lễ trở thành mục tiêu săn đuổi hàng đầu.
- **Cướp sính lễ**: Khi một chiến binh mang sính lễ bị đánh gục (`HP = 0`), toàn bộ sính lễ trong Balo **văng tung tóe ra đất**, đối thủ hoặc đồng đội có thể nhặt cướp ngay giữa chiến trường!
- **Thủy Triều Dâng Lũ (Thủy Tinh)**: Thủy Tinh nổi giận hô mưa gọi gió, dâng nước ngập lụt một vùng rộng lớn làm chậm 35% tốc độ của đối phương.
- **Bốc Đồi Dời Núi (Sơn Tinh)**: Sơn Tinh dựng lũy đá thần thạch, phóng cự thạch đẩy lùi dòng nước lũ và bảo vệ đồng đội.

### 1.4. Định Mệnh Nộp Lễ (Kết cục trận đấu)
- Đội nào hộ tống người giữ Balo vượt qua mưa bão về đến **Cung Điện Vua Hùng** (Thánh điện phía Bắc) và nộp đủ **3 món sính lễ** sẽ giành chiến thắng tuyệt đối!
- Vua Hùng kích hoạt kết giới thần thánh, chính thức phong danh Phò mã nước Văn Lang!

### 🎯 Ý Nghĩa Lồng Ghép:
- **Tái hiện lịch sử chống thiên tai**: Mô phỏng sự khắc nghiệt của bão lũ miền Bắc và tinh thần dời non ngăn lũ của cha ông.
- **Đề cao tinh thần đồng đội (Co-op 2v2)**: Người mang sính lễ bị giảm tốc độ rõ rệt, không thể tự bảo vệ nếu không có đồng đội hộ tống, bọc lót.

---

## 🏛️ 2. KIẾN TRÚC HỆ THỐNG MẠNG (CLIENT-SERVER ĐA LUỒNG)

Hệ thống được thiết kế theo mô hình **Client-Server phân tán (Distributed Architecture)** gồm 3 thành phần chính:

```
                      +-----------------------------+
                      |       MASTER SERVER         |
                      |  (TCP Port: 8888)           |
                      |  - Auth (SHA-256 + Salt)    |
                      |  - Matchmaker 2v2 (Queue)   |
                      |  - Leaderboard Elo Rating   |
                      +--------------+--------------+
                                     |
                       (Match Found / Handshake)
                                     |
                      +--------------v--------------+
                      |        GAME SERVER          |
                      |  TCP Port: 9999 (Reliable)  |
                      |  UDP Port: 9998 (Realtime)  |
                      |  - Game Loop (40 TPS)       |
                      |  - AI Quái Boss & Va chạm   |
                      |  - Inventory State Sync     |
                      |  - Bão lũ & Nộp Lễ Vua Hùng |
                      +--------------+--------------+
                                     |
                       (TCP Control + UDP Streaming)
                                     |
          +--------------------------+--------------------------+
          |                                                     |
+---------v---------+                                 +---------v---------+
|   GAME CLIENT 1   |                                 |   GAME CLIENT 2   |
|   (Java Swing 2D) |   <=== Giao Tranh 2v2 ===>      |   (Java Swing 2D) |
+-------------------+                                 +-------------------+
```

### 2.1. Phân Định Giao Thức Lai Hybrid (TCP / UDP)
- **TCP (Transmission Control Protocol - Kết nối Đáng tin cậy)**:
  - Xác thực Đăng nhập, Đăng ký người dùng.
  - Hàng đợi Matchmaking 2v2.
  - Đồng bộ Balo Sính Lễ (`INVENTORY_SYNC_NOTIFY`): Nhặt đồ, Rơi đồ, Cướp đồ, Nộp lễ tại Vua Hùng (Đảm bảo tuyệt đối không bị mất gói tin hay duplicate sính lễ).
  - Kích hoạt Kỹ năng tối thượng (`ACTION_SKILL_REQ`) và Chat hệ thống.
- **UDP (User Datagram Protocol - Độ trễ siêu thấp 40Hz)**:
  - Truyền nhận trạng thái vị trí người chơi thời gian thực (`x, y, vx, vy, angle`).
  - Broadcast Snapshot thế giới (`UDP_WORLD_SNAPSHOT`) gồm tọa độ Players, Quái Boss, Đạn bay và Vùng ngập nước lũ với tần số 40 lần/giây (25ms/gói).

---

## 📦 3. THIẾT KẾ GIAO THỨC GÓI TIN TÙY CHỈNH (CUSTOM BINARY PACKET)

Để phục vụ đồ án An Ninh Mạng và Lập Trình Mạng, toàn bộ gói tin giao tiếp đều được thiết kế dưới dạng **Binary Stream tùy chỉnh** (không dùng JSON thuần để tối ưu băng thông và bảo mật):

### Cấu Trúc Header Gói Tin (11 Bytes):
```
+---------------+---------------+---------------+---------------+-------------------------------+---------------+--------------+
| Magic Byte 1  | Magic Byte 2  |    Version    |  Packet Type  |      Sequence / Timestamp     | Payload Length|   Checksum   |
|     (0x53)    |     (0x54)    |    (1 Byte)   |   (1 Byte)    |           (4 Bytes)           |   (2 Bytes)   |   (1 Byte)   |
+---------------+---------------+---------------+---------------+-------------------------------+---------------+--------------+
|<--------------------------------------- HEADER: 11 BYTES ---------------------------------------------------->|
```
- **Magic Bytes**: `0x53` ('S'), `0x54` ('T') – Nhận diện tính hợp lệ của giao thức Sơn Tinh Thủy Tinh.
- **Packet Type**: Mã định danh loại thao tác (LOGIN, MATCH, INTERACT, SYNC, UDP_SNAPSHOT...).
- **Checksum**: Tính toán bằng thuật toán XOR giữa Header và toàn bộ Payload. Giúp Server và Client phát hiện ngay lập tức nếu gói tin bị suy hao hoặc chỉnh sửa trái phép (Tampering Attack).

---

## 🔒 4. TÍNH NĂNG BẢO MẬT & AN NINH MẠNG

1. **Mật khẩu an toàn (SHA-256 + Random Salt)**: Mật khẩu người chơi không bao giờ lưu dưới dạng bản rõ (plaintext). Mỗi tài khoản có một chuỗi Salt ngẫu nhiên 16 bytes được băm bằng SHA-256 chống lại các cuộc tấn công Rainbow Table.
2. **Session Token động**: Khi ghép phòng thành công, Master Server phát sinh một mã `roomToken` bảo mật cao (24 bytes Base64). Game Server chỉ cho phép Client tham gia khi xuất trình đúng Token này, ngăn chặn tấn công chiếm quyền phòng (Room Hijacking).
3. **Chống gian lận vị trí (Server-Authoritative Game Loop)**: Vị trí người chơi, va chạm, sát thương đòn đánh và lượng máu quái vật hoàn toàn được tính toán và kiểm duyệt trên Game Server, chống hack Client tốc độ cao hoặc bất tử máu.

---

## 🚀 5. HƯỚNG DẪN KHỞI CHẠY HỆ THỐNG

### Cách 1: Khởi Chạy Tự Động Toàn Bộ Bằng 1 Click (Khuyên Dùng)
Nhấp đúp chuột vào file:
```
run_all.bat
```
*(Hệ thống sẽ tự động khởi động Master Server, Game Server và mở Game Client đầu tiên).*

### Cách 2: Khởi Chạy Từng Cửa Sổ (Phục vụ Demo / Kiểm Thử 2v2)
1. Mở Terminal hoặc nhấp file: **`run_master.bat`** (Chạy Master Server - Port 8888).
2. Mở Terminal hoặc nhấp file: **`run_gameserver.bat`** (Chạy Game Server - TCP 9999, UDP 9998).
3. Mở Client 1: Nhấp file **`run_client.bat`** (Đăng nhập `sontinh` / `123456`).
4. Mở Client 2: Nhấp file **`run_client.bat`** một lần nữa (Đăng nhập `thuytinh` / `123456`).

### 🎮 Điều Khiển Trong Game:
- **`W - A - S - D`** hoặc **Mũi tên**: Di chuyển dũng sĩ.
- **Di chuột**: Xoay hướng ngắm bắn thần khí.
- **Chuột Trái** hoặc **Phím `J`**: Bắn phép thường (Cự Thạch / Thủy Tiễn).
- **Chuột Phải** hoặc **Phím `K`**: Thi triển Tuyệt Kỹ Tối Thượng (Sơn Tinh: Hộ Thể Dời Non / Thủy Tinh: Thủy Triều Bão Lũ).
- **Phím `E`** hoặc **`Space`**: Nhặt Sính Lễ trên mặt đất / Dâng Nộp Sính Lễ trước ngai vàng Vua Hùng!

---

## 📊 6. TÀI KHOẢN MẪU CÓ SẴN
- Tài khoản 1: `sontinh` | Mật khẩu: `123456` (Elo: 1650)
- Tài khoản 2: `thuytinh` | Mật khẩu: `123456` (Elo: 1580)
- Tài khoản 3: `hove1` | Mật khẩu: `123456`
- Tài khoản 4: `hove2` | Mật khẩu: `123456`
*(Hoặc có thể bấm nút **Đăng Ký** trên giao diện Client để tạo tài khoản mới bất kỳ).*
