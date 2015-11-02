package io.bitsquare.crypto;

import com.google.protobuf.ByteString;
import io.bitsquare.common.UserThread;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.wallet.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

//TODO: Borrowed form BitcoinJ/Lighthouse. Remove Protos dependency, check complete code logic.
public class ScryptUtil {
    private static final Logger log = LoggerFactory.getLogger(ScryptUtil.class);

    public interface DeriveKeyResultHandler {
        void handleResult(KeyParameter aesKey);
    }

    public static KeyCrypterScrypt getKeyCrypterScrypt() {
        Protos.ScryptParameters scryptParameters = Protos.ScryptParameters.newBuilder()
                .setP(6)
                .setR(8)
                .setN(32768)
                .setSalt(ByteString.copyFrom(KeyCrypterScrypt.randomSalt()))
                .build();
        return new KeyCrypterScrypt(scryptParameters);
    }

    public static void deriveKeyWithScrypt(KeyCrypterScrypt keyCrypterScrypt, String password, DeriveKeyResultHandler resultHandler) {
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
