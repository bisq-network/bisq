package io.bitsquare.crypto;

import com.google.protobuf.ByteString;
import io.bitsquare.common.UserThread;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.wallet.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

//TODO
public class ScryptUtil {
    private static final Logger log = LoggerFactory.getLogger(ScryptUtil.class);

    public ScryptUtil() {
    }

    public static final Protos.ScryptParameters SCRYPT_PARAMETERS = Protos.ScryptParameters.newBuilder()
            .setP(6)
            .setR(8)
            .setN(32768)
            .setSalt(ByteString.copyFrom(KeyCrypterScrypt.randomSalt()))
            .build();

    public interface ScryptKeyDerivationResultHandler {
        void handleResult(KeyParameter aesKey);
    }

    public static void deriveKeyWithScrypt(KeyCrypterScrypt keyCrypterScrypt, String password, ScryptKeyDerivationResultHandler resultHandler) {
        new Thread(() -> {
            log.info("Doing key derivation");

            long start = System.currentTimeMillis();
            KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
            long duration = System.currentTimeMillis() - start;
            log.info("Key derivation took {} msec", duration);
            UserThread.execute(() -> resultHandler.handleResult(aesKey));
        }).start();
    }
}
