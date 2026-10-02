package common;

import java.io.*;

/**
 * Cấu trúc gói tin nhị phân tùy chỉnh (Custom Binary Protocol)
 * Header (9 bytes):
 * [0..1] Magic Bytes: 0x53 ('S'), 0x54 ('T')
 * [2]    Protocol Version (byte)
 * [3]    Packet Type (byte)
 * [4..7] Sequence ID / Timestamp (int)
 * [8..9] Payload Length (short)
 * [10]   Checksum (byte: XOR of Header + Payload)
 * Payload: [Payload Length] bytes
 */
public class CustomPacket {
    public static final int HEADER_SIZE = 11;

    private PacketType type;
    private int sequenceId;
    private byte[] payload;

    public CustomPacket(PacketType type, int sequenceId, byte[] payload) {
        this.type = type;
        this.sequenceId = sequenceId;
        this.payload = (payload != null) ? payload : new byte[0];
    }

    public CustomPacket(PacketType type, byte[] payload) {
        this(type, (int) (System.currentTimeMillis() & 0x7FFFFFFF), payload);
    }

    public PacketType getType() {
        return type;
    }

    public int getSequenceId() {
        return sequenceId;
    }

    public byte[] getPayload() {
        return payload;
    }

    /**
     * Tính toán Checksum XOR đơn giản để bảo đảm tính toàn vẹn dữ liệu
     */
    private static byte computeChecksum(byte typeCode, int seq, byte[] payload) {
        byte chk = (byte) (Constants.MAGIC_BYTE_1 ^ Constants.MAGIC_BYTE_2 ^ Constants.PROTOCOL_VERSION ^ typeCode);
        chk ^= (byte) ((seq >> 24) & 0xFF);
        chk ^= (byte) ((seq >> 16) & 0xFF);
        chk ^= (byte) ((seq >> 8) & 0xFF);
        chk ^= (byte) (seq & 0xFF);
        int len = payload != null ? payload.length : 0;
        chk ^= (byte) ((len >> 8) & 0xFF);
        chk ^= (byte) (len & 0xFF);
        if (payload != null) {
            for (byte b : payload) {
                chk ^= b;
            }
        }
        return chk;
    }

    /**
     * Đóng gói packet thành mảng byte để gửi qua TCP / UDP socket
     */
    public byte[] toBytes() {
        int payloadLen = payload != null ? payload.length : 0;
        byte[] bytes = new byte[HEADER_SIZE + payloadLen];

        bytes[0] = Constants.MAGIC_BYTE_1;
        bytes[1] = Constants.MAGIC_BYTE_2;
        bytes[2] = (byte) Constants.PROTOCOL_VERSION;
        bytes[3] = type.getCode();
        bytes[4] = (byte) ((sequenceId >> 24) & 0xFF);
        bytes[5] = (byte) ((sequenceId >> 16) & 0xFF);
        bytes[6] = (byte) ((sequenceId >> 8) & 0xFF);
        bytes[7] = (byte) (sequenceId & 0xFF);
        bytes[8] = (byte) ((payloadLen >> 8) & 0xFF);
        bytes[9] = (byte) (payloadLen & 0xFF);
        bytes[10] = computeChecksum(type.getCode(), sequenceId, payload);

        if (payloadLen > 0) {
            System.arraycopy(payload, 0, bytes, HEADER_SIZE, payloadLen);
        }
        return bytes;
    }

    /**
     * Đọc 1 gói tin hoàn chỉnh từ TCP InputStream
     */
    public static CustomPacket readFromTCP(DataInputStream in) throws IOException {
        byte magic1 = in.readByte();
        byte magic2 = in.readByte();
        if (magic1 != Constants.MAGIC_BYTE_1 || magic2 != Constants.MAGIC_BYTE_2) {
            throw new IOException("Sai Magic Bytes! Gói tin không hợp lệ: " + magic1 + ", " + magic2);
        }

        byte version = in.readByte();
        byte typeCode = in.readByte();
        int seq = in.readInt();
        int payloadLen = in.readUnsignedShort();
        byte checksum = in.readByte();

        byte[] payload = new byte[payloadLen];
        if (payloadLen > 0) {
            in.readFully(payload);
        }

        byte expectedChk = computeChecksum(typeCode, seq, payload);
        if (checksum != expectedChk) {
            throw new IOException("Checksum không khớp! Gói tin bị giả mạo hoặc hư hỏng.");
        }

        PacketType type = PacketType.fromCode(typeCode);
        return new CustomPacket(type, seq, payload);
    }

    /**
     * Giải mã 1 gói tin nhận được từ UDP DatagramPacket
     */
    public static CustomPacket fromBytes(byte[] data, int length) throws IOException {
        if (length < HEADER_SIZE) {
            throw new IOException("Gói tin UDP quá ngắn: " + length);
        }

        if (data[0] != Constants.MAGIC_BYTE_1 || data[1] != Constants.MAGIC_BYTE_2) {
            throw new IOException("Sai Magic Bytes trong UDP!");
        }

        byte typeCode = data[3];
        int seq = ((data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16) | ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        int payloadLen = ((data[8] & 0xFF) << 8) | (data[9] & 0xFF);
        byte checksum = data[10];

        if (HEADER_SIZE + payloadLen > length) {
            throw new IOException("Chiều dài payload UDP không hợp lệ!");
        }

        byte[] payload = new byte[payloadLen];
        if (payloadLen > 0) {
            System.arraycopy(data, HEADER_SIZE, payload, 0, payloadLen);
        }

        byte expectedChk = computeChecksum(typeCode, seq, payload);
        if (checksum != expectedChk) {
            throw new IOException("UDP Checksum không khớp!");
        }

        return new CustomPacket(PacketType.fromCode(typeCode), seq, payload);
    }
}
