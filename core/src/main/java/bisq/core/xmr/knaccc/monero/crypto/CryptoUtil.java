package bisq.core.xmr.knaccc.monero.crypto;

import bisq.core.xmr.com.joemelsha.crypto.hash.Keccak;
import bisq.core.xmr.org.nem.core.crypto.ed25519.arithmetic.Ed25519Group;
import bisq.core.xmr.org.nem.core.crypto.ed25519.arithmetic.Ed25519GroupElement;
import bisq.core.xmr.org.nem.core.utils.HexEncoder;

import java.math.BigInteger;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.xmr.knaccc.monero.address.ByteUtil.getBigIntegerFromUnsignedLittleEndianByteArray;
import static bisq.core.xmr.knaccc.monero.address.ByteUtil.reverseByteArrayInPlace;

@Slf4j
public class CryptoUtil {

    public static final Ed25519GroupElement G = Ed25519Group.BASE_POINT;

    public static Scalar hashToScalar(byte[] a) {
        return new Scalar(scReduce32(fastHash(a)));
    }

    public static Scalar hashToScalar(Ed25519GroupElement a) {
        return new Scalar(scReduce32(fastHash(a.encode().getRaw())));
    }

    private static Keccak keccak = new Keccak(256);

    public static byte[] fastHash(byte[] a) {
        try {
            keccak.reset();
            keccak.update(a);
            return keccak.digestArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static BigInteger l = BigInteger.valueOf(2).pow(252).add(new BigInteger("27742317777372353535851937790883648493"));

    public static String toCanonicalTxKey(String txKey) {
        byte[] bytes = HexEncoder.getBytes(txKey);
        byte[] asLittleEndianBytes = ensure32BytesAndConvertToLittleEndian(bytes);
        byte[] nonMalleable = new BigInteger(asLittleEndianBytes).mod(l).toByteArray();
        byte[] nonMalleableAsLittleEndian = ensure32BytesAndConvertToLittleEndian(nonMalleable);
        return HexEncoder.getString(nonMalleableAsLittleEndian);
    }

    public static byte[] scReduce32(byte[] a) {
        byte[] r = getBigIntegerFromUnsignedLittleEndianByteArray(a).mod(l).toByteArray();
        return ensure32BytesAndConvertToLittleEndian(r);
    }

    public static byte[] ensure32BytesAndConvertToLittleEndian(byte[] r) {
        byte[] s = new byte[32];
        if (r.length > 32) System.arraycopy(r, 1, s, 0, s.length);
        else System.arraycopy(r, 0, s, 32 - r.length, r.length);
        reverseByteArrayInPlace(s);
        return s;
    }

}
