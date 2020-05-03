package bisq.core.grpc;

import bisq.core.btc.Balances;
import bisq.core.btc.wallet.WalletsManager;

import org.bitcoinj.crypto.KeyCrypterScrypt;

import javax.inject.Inject;

import org.spongycastle.crypto.params.KeyParameter;

import java.util.Timer;
import java.util.TimerTask;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

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
            return;
        }

        if (walletsManager.areWalletsEncrypted())
            throw new IllegalStateException("wallet is encrypted with a password");

        // TODO Validate new password.
        KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
        walletsManager.encryptWallets(keyCrypterScrypt, aesKey);
    }

    public void lockWallet() {
        if (tempLockWalletPassword == null)
            throw new IllegalStateException("wallet is already locked");

        setWalletPassword(tempLockWalletPassword, null);
        tempLockWalletPassword = null;
    }

    public void unlockWallet(String password, long timeout) {
        removeWalletPassword(password);

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
    }

    // Provided for automated wallet protection method testing, despite the
    // security risks exposed by providing users the ability to decrypt their wallets.
    public void removeWalletPassword(String password) {
        if (!walletsManager.areWalletsAvailable())
            throw new IllegalStateException("wallet is not yet available");

        if (!walletsManager.areWalletsEncrypted())
            throw new IllegalStateException("wallet is not encrypted with a password");

        KeyCrypterScrypt keyCrypterScrypt = walletsManager.getKeyCrypterScrypt();
        if (keyCrypterScrypt == null)
            throw new IllegalStateException("wallet encrypter is not available");

        KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
        if (!walletsManager.checkAESKey(aesKey))
            throw new IllegalStateException("incorrect password");

        walletsManager.decryptWallets(aesKey);
    }
}
