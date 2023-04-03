package bisq.core.xmr.knaccc.monero.address;

import bisq.core.xmr.knaccc.monero.crypto.CryptoUtil;
import bisq.core.xmr.knaccc.monero.crypto.Scalar;
import bisq.core.xmr.org.nem.core.crypto.ed25519.arithmetic.Ed25519EncodedFieldElement;
import bisq.core.xmr.org.nem.core.crypto.ed25519.arithmetic.Ed25519EncodedGroupElement;
import bisq.core.xmr.org.nem.core.crypto.ed25519.arithmetic.Ed25519Group;
import bisq.core.xmr.org.nem.core.crypto.ed25519.arithmetic.Ed25519GroupElement;

import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;

import static bisq.core.xmr.knaccc.monero.address.ByteUtil.concat;
import static bisq.core.xmr.knaccc.monero.address.ByteUtil.hexToBytes;
import static bisq.core.xmr.knaccc.monero.address.ByteUtil.longToLittleEndianUint32ByteArray;

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
        Scalar m = CryptoUtil.hashToScalar(data);
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

    public String getSubaddressBase58(String privateViewKeyHex, long accountId, long subaddressId) throws InvalidWalletAddressException {
        if (!checkPrivateViewKey(privateViewKeyHex)) {
            throw new InvalidWalletAddressException("Wrong private view key for main address");
        }
        return getSubaddressBase58(new Scalar(privateViewKeyHex), hexToBytes(getPublicSpendKeyHex()), accountId, subaddressId);
    }

    @VisibleForTesting
    boolean checkPrivateViewKey(String privateViewKey) {
        return isPrivateKeyReduced(privateViewKey) && doesPrivateKeyResolveToPublicKey(privateViewKey, this.publicViewKeyHex);
    }

    @VisibleForTesting
    static boolean isPrivateKeyReduced(String privateKey) {
        byte[] input = hexToBytes(privateKey);
        byte[] reduced = CryptoUtil.scReduce32(input);
        return Arrays.equals(input, reduced);
    }

    @VisibleForTesting
    static boolean doesPrivateKeyResolveToPublicKey(String privateKey, String publicKey) {
        Scalar m = new Scalar(privateKey);
        Ed25519GroupElement M = G.scalarMultiply(new Ed25519EncodedFieldElement(m.bytes));
        byte[] generatedPubKey = M.encode().getRaw();
        return Arrays.equals(generatedPubKey, hexToBytes(publicKey));
    }
}
