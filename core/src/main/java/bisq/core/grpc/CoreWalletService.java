package bisq.core.grpc;

import bisq.core.btc.Balances;
import bisq.core.btc.wallet.WalletsManager;

import bisq.common.util.Tuple2;

import org.bitcoinj.crypto.KeyCrypterScrypt;

import javax.inject.Inject;

import org.spongycastle.crypto.params.KeyParameter;

import java.util.Timer;
import java.util.TimerTask;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.grpc.ApiStatus.*;
import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
class CoreWalletService {

    private final Balances balances;
    private final WalletsManager walletsManager;

    @Nullable
    private String tempLockWalletPassword;

    @Inject
    public CoreWalletService(Balances balances, WalletsManager walletsManager) {
        this.balances = balances;
        this.walletsManager = walletsManager;
    }

    public long getAvailableBalance() {
        if (!walletsManager.areWalletsAvailable())
            throw new IllegalStateException("wallet is not yet available");

        if (walletsManager.areWalletsEncrypted())
            throw new IllegalStateException("wallet is locked");

        var balance = balances.getAvailableBalance().get();
        if (balance == null)
            throw new IllegalStateException("balance is not yet available");

        return balance.getValue();
    }

    public void setWalletPassword(String password, String newPassword) {
        try {
            if (!walletsManager.areWalletsAvailable())
                throw new IllegalStateException("wallet is not yet available");

            KeyCrypterScrypt keyCrypterScrypt = walletsManager.getKeyCrypterScrypt();
            if (keyCrypterScrypt == null)
                throw new IllegalStateException("wallet encrypter is not available");

            if (newPassword != null && !newPassword.isEmpty()) {
                // TODO Validate new password before replacing old password.
                if (!walletsManager.areWalletsEncrypted())
                    throw new IllegalStateException("wallet is not encrypted with a password");

                KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
                if (!walletsManager.checkAESKey(aesKey))
                    throw new IllegalStateException("incorrect old password");

                walletsManager.decryptWallets(aesKey);
                aesKey = keyCrypterScrypt.deriveKey(newPassword);
                walletsManager.encryptWallets(keyCrypterScrypt, aesKey);
            }

            if (walletsManager.areWalletsEncrypted())
                throw new IllegalStateException("wallet is encrypted with a password");

            // TODO Validate new password.
            KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
            walletsManager.encryptWallets(keyCrypterScrypt, aesKey);
        } catch (Throwable t) {
            t.printStackTrace();
            throw new IllegalStateException(t);
        }
    }

    public Tuple2<Boolean, ApiStatus> lockWallet() {
        if (tempLockWalletPassword != null) {
            setWalletPassword(tempLockWalletPassword, null);
            tempLockWalletPassword = null;
            return new Tuple2<>(true, OK);
        }
        return new Tuple2<>(false, WALLET_ALREADY_LOCKED);
    }

    public Tuple2<Boolean, ApiStatus> unlockWallet(String password, long timeout) {
        Tuple2<Boolean, ApiStatus> decrypted = removeWalletPassword(password);
        if (!decrypted.second.equals(OK))
            return decrypted;

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                log.info("Locking wallet");
                setWalletPassword(password, null);
                tempLockWalletPassword = null;
            }
        };
        Timer timer = new Timer("Lock Wallet Timer");
        timer.schedule(timerTask, SECONDS.toMillis(timeout));

        // Cache wallet password for timeout (secs), in case
        // user wants to lock the wallet for timeout expires.
        tempLockWalletPassword = password;
        return new Tuple2<>(true, OK);
    }

    // Provided for automated wallet protection method testing, despite the
    // security risks exposed by providing users the ability to decrypt their wallets.
    public Tuple2<Boolean, ApiStatus> removeWalletPassword(String password) {
        if (!walletsManager.areWalletsAvailable())
            return new Tuple2<>(false, WALLET_NOT_AVAILABLE);

        if (!walletsManager.areWalletsEncrypted())
            return new Tuple2<>(false, WALLET_NOT_ENCRYPTED);

        KeyCrypterScrypt keyCrypterScrypt = walletsManager.getKeyCrypterScrypt();
        if (keyCrypterScrypt == null)
            return new Tuple2<>(false, WALLET_ENCRYPTER_NOT_AVAILABLE);

        KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
        if (!walletsManager.checkAESKey(aesKey))
            return new Tuple2<>(false, INCORRECT_WALLET_PASSWORD);

        walletsManager.decryptWallets(aesKey);
        return new Tuple2<>(true, OK);
    }
}
