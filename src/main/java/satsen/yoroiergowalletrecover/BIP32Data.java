package satsen.yoroiergowalletrecover;

import java.nio.ByteBuffer;

/**
 * @author satsen
 */
public record BIP32Data(int version, byte depth, byte[] fingerprint, byte[] childNumber, byte[] chainCode, byte[] keyData) {
    /**
     * Versions
     */
    public static int
            MAINNET_PUBLIC = 0x0488B21E, MAINNET_PRIVATE = 0x0488ADE4,
            TESTNET_PUBLIC = 0x043587CF, TESTNET_PRIVATE = 0x04358394;

    public BIP32Data {
        boolean isPublic = version == MAINNET_PUBLIC || version == TESTNET_PUBLIC;
        if (fingerprint.length != 4 || childNumber.length != 4 || chainCode.length != 32
                || (isPublic && keyData.length != 33) || (!isPublic && keyData.length != 32))
            throw new IllegalArgumentException("wrong length");
    }
    public static BIP32Data parse(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        int version = buf.getInt();
        byte depth = buf.get();
        byte[] fingerprint = getBytes(buf, 4), childNumber = getBytes(buf, 4), chainCode = getBytes(buf, 32);
        byte[] keyData;
        // As public keys are 33 bytes but private keys are 32 bytes, there is a 0 at the beginning of the array to make the private 33 as well, so it must be ignored.
        if (version == MAINNET_PRIVATE || version == TESTNET_PRIVATE) {
            buf.get(); // skip 0
            keyData = getBytes(buf, 32);
        } else {
            keyData = getBytes(buf, 33);
        }
        if (buf.hasRemaining()) throw new IllegalArgumentException("found extra bytes");
        return new BIP32Data(version, depth, fingerprint, childNumber, chainCode, keyData);
    }
    private static byte[] getBytes(ByteBuffer buf, int size) {
        byte[] bytes = new byte[size];
        buf.get(bytes);
        return bytes;
    }
    public byte[] serialize() {
        ByteBuffer buf = ByteBuffer.allocate(78);
        buf.putInt(version);
        buf.put(depth);
        buf.put(fingerprint);
        buf.put(childNumber);
        buf.put(chainCode);
        if (version == MAINNET_PRIVATE || version == TESTNET_PRIVATE)
            buf.put((byte) 0);
        buf.put(keyData);
        return buf.array();
    }
}