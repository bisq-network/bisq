package knaccc.monero.address;

import static knaccc.monero.address.ByteUtil.concat;
import static knaccc.monero.address.ByteUtil.hexToBytes;
import static knaccc.monero.address.ByteUtil.longToLittleEndianUint32ByteArray;
import static knaccc.monero.crypto.CryptoUtil.hashToScalar;



import knaccc.monero.crypto.CryptoUtil;
import knaccc.monero.crypto.Scalar;
import org.nem.core.crypto.ed25519.arithmetic.Ed25519EncodedFieldElement;
import org.nem.core.crypto.ed25519.arithmetic.Ed25519EncodedGroupElement;
import org.nem.core.crypto.ed25519.arithmetic.Ed25519Group;
import org.nem.core.crypto.ed25519.arithmetic.Ed25519GroupElement;

public class WalletAddress {

    public static final int PUBLIC_ADDRESS_PREFIX = 18;
    public static final int PUBLIC_INTEGRATED_ADDRESS_PREFIX = 19;
    public static final int PUBLIC_SUBADDRESS_PREFIX = 42;

    private final String base58;
    private final String hex;
    private final String networkByte;
    private final String publicSpendKeyHex;
    private final String publicViewKeyHex;
    private final String checksum;
    private String integratedPaymentId = null;

    public static class InvalidWalletAddressException extends Exception {
        public InvalidWalletAddressException(String message) {
            super(message);
        }
    }

    public WalletAddress(String base58WalletAddress) throws InvalidWalletAddressException {

        base58 = base58WalletAddress;
        int pos = 0;
        hex = ByteUtil.bytesToHex(Base58.decode(base58WalletAddress));
        networkByte = hex.substring(0, pos += 2);
        if (!(
                networkByte.equals(ByteUtil.byteToHex(PUBLIC_ADDRESS_PREFIX)) ||
                        networkByte.equals(ByteUtil.byteToHex(PUBLIC_INTEGRATED_ADDRESS_PREFIX)) ||
                        networkByte.equals(ByteUtil.byteToHex(PUBLIC_SUBADDRESS_PREFIX))
        )) throw new InvalidWalletAddressException("Unrecognized address type: " + networkByte + " (hex)");
        publicSpendKeyHex = hex.substring(pos, pos += 64);
        publicViewKeyHex = hex.substring(pos, pos += 64);
        if (networkByte.equals(ByteUtil.byteToHex(PUBLIC_INTEGRATED_ADDRESS_PREFIX)))
            integratedPaymentId = hex.substring(pos, pos += 16);
        checksum = hex.substring(pos, pos += 8);

        String recalculatedChecksumHex = ByteUtil.bytesToHex(CryptoUtil.fastHash(hexToBytes(networkByte + publicSpendKeyHex + publicViewKeyHex + (integratedPaymentId == null ? "" : integratedPaymentId)))).substring(0, 8);
        if (!checksum.equals(recalculatedChecksumHex))
            throw new InvalidWalletAddressException("Checksum does not match");

    }

    public boolean isSubaddress() {
        return networkByte.equals(ByteUtil.byteToHex(PUBLIC_SUBADDRESS_PREFIX));
    }

    public String getPublicSpendKeyHex() {
        return publicSpendKeyHex;
    }

    public String getPublicViewKeyHex() {
        return publicViewKeyHex;
    }

    public String getNetworkByte() {
        return networkByte;
    }

    public String getHex() {
        return hex;
    }

    public String getBase58() {
        return base58;
    }

    public String getChecksum() {
        return checksum;
    }

    @Override
    public String toString() {
        return getBase58();
    }

    private static final Ed25519GroupElement G = Ed25519Group.BASE_POINT;


    public static byte[] getSubaddressPublicSpendKeyBytes(Scalar privateViewKey,
                                                          byte[] publicSpendKeyBytes,
                                                          long accountId,
                                                          long subaddressId) {
        if (accountId == 0 && subaddressId == 0)
            throw new RuntimeException("Not to be called for the base wallet address");
        byte[] data = concat("SubAddr\0".getBytes(), privateViewKey.bytes, longToLittleEndianUint32ByteArray(accountId), longToLittleEndianUint32ByteArray(subaddressId));
        Scalar m = hashToScalar(data);
        Ed25519GroupElement M = G.scalarMultiply(new Ed25519EncodedFieldElement(m.bytes));
        Ed25519GroupElement B = new Ed25519EncodedGroupElement(publicSpendKeyBytes).decode();
        Ed25519GroupElement D = B.add(M.toCached());
        return D.encode().getRaw();
    }

    public static String getSubaddressBase58(Scalar privateViewKey,
                                             byte[] publicSpendKeyBytes,
                                             long accountId,
                                             long subaddressId) {

        Ed25519GroupElement D = new Ed25519EncodedGroupElement(getSubaddressPublicSpendKeyBytes(privateViewKey, publicSpendKeyBytes, accountId, subaddressId)).decode();
        D.precomputeForScalarMultiplication();
        Ed25519GroupElement C = D.scalarMultiply(new Ed25519EncodedFieldElement(privateViewKey.bytes));

        byte[] subaddressBytes = concat((byte) PUBLIC_SUBADDRESS_PREFIX, concat(D.encode().getRaw(), C.encode().getRaw()));
        String hex = ByteUtil.bytesToHex(subaddressBytes);
        String calculatedChecksumHex = ByteUtil.bytesToHex(CryptoUtil.fastHash(hexToBytes(hex))).substring(0, 8);
        hex += calculatedChecksumHex;
        return Base58.encode(hexToBytes(hex));

    }

    public String getSubaddressBase58(String privateViewKeyHex, long accountId, long subaddressId) {
        return getSubaddressBase58(new Scalar(privateViewKeyHex), hexToBytes(getPublicSpendKeyHex()), accountId, subaddressId);
    }

    public static void assertEquals(Object a, Object b) {
        if (!a.equals(b)) throw new RuntimeException("Assertion failed");
    }

    public static void main(String[] args) throws Exception {

        String mainAddress = "43amHgM9cDHhJY8tAujYi4MisCx4dvNQB5xVYbRLqPYLbVmH5qHcUgsjgsdoSdLK3TgRaBd68bCLaRcK8VakCUAJLGjz42G";
        WalletAddress walletAddress = new WalletAddress(mainAddress);

        String privateViewKeyHex = "7b37d8922245a07244fd31855d1e705a590a9bd2881825f0542ad99cdaba090a";

        System.out.println("subaddress for account index 0, subaddress index 1: "
                + walletAddress.getSubaddressBase58(privateViewKeyHex, 0, 1));


        // tests
        String addr00 = "43amHgM9cDHhJY8tAujYi4MisCx4dvNQB5xVYbRLqPYLbVmH5qHcUgsjgsdoSdLK3TgRaBd68bCLaRcK8VakCUAJLGjz42G";
        String addr01 = "8B3QYUXKj8ySWiCaF79NyS6RJBkkRmNpQiCMKHkHhE7J67joNdt1Wf7gxFKw8EnXxofpVhdSsg61JQnR2jbeEyW2CM5sqvY";
        String addr10 = "83YULqcGNVzMA4ehBN8uwP4tiJYGBw3Zo8LAEod1rtvd4WfATg9LHZbd8tbnNrosb3Fri7HdXSPyF2hPBQend6A3LQWymPt";
        String addr11 = "8AZFX2Ledf8hhb5RTt9vsbGfc6CJW4SviWMgpFy9LCmKJzg6ZCyKR2nEBtiz8v8QXheoCPLFGi1HpEtyBju8aUA6Bkreqhr";

        assertEquals(walletAddress.getBase58(), mainAddress);
        assertEquals(mainAddress, addr00);
        assertEquals(walletAddress.getSubaddressBase58(privateViewKeyHex, 0, 1), addr01);
        assertEquals(walletAddress.getSubaddressBase58(privateViewKeyHex, 1, 0), addr10);
        assertEquals(walletAddress.getSubaddressBase58(privateViewKeyHex, 1, 1), addr11);

    }

}
