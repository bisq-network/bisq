package io.bitsquare.crypto;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.protobuf.ByteString;
import io.bitsquare.common.UserThread;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.wallet.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.concurrent.*;

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
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("Routing-%d")
                .setDaemon(true)
                .build();

        ExecutorService executorService = new ThreadPoolExecutor(5, 50, 10L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(50), threadFactory);
        executorService.submit(() -> {
            try {
                log.info("Doing key derivation");
                long start = System.currentTimeMillis();
                KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
                long duration = System.currentTimeMillis() - start;
                log.info("Key derivation took {} msec", duration);
                UserThread.execute(() -> {
                    try {
                        resultHandler.handleResult(aesKey);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        log.error("Executing task failed. " + t.getMessage());
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
                log.error("Executing task failed. " + t.getMessage());
            }
        });
    }
}
