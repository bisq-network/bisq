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
    private TimerTask lockTask;

    @Nullable
    private KeyParameter tempAesKey;

    @Inject
    public CoreWalletService(Balances balances, WalletsManager walletsManager) {
        this.balances = balances;
        this.walletsManager = walletsManager;
    }

    public long getAvailableBalance() {
        if (!walletsManager.areWalletsAvailable())
            throw new IllegalStateException("wallet is not yet available");

        if (walletsManager.areWalletsEncrypted() && tempAesKey == null)
            throw new IllegalStateException("wallet is locked");

        var balance = balances.getAvailableBalance().get();
        if (balance == null)
            throw new IllegalStateException("balance is not yet available");

        return balance.getValue();
    }

    public void setWalletPassword(String password, String newPassword) {
        if (!walletsManager.areWalletsAvailable())
            throw new IllegalStateException("wallet is not yet available");

        KeyCrypterScrypt keyCrypterScrypt = getKeyCrypterScrypt();

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
            walletsManager.backupWallets();
            return;
        }

        if (walletsManager.areWalletsEncrypted())
            throw new IllegalStateException("wallet is encrypted with a password");

        // TODO Validate new password.
        KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
        walletsManager.encryptWallets(keyCrypterScrypt, aesKey);
        walletsManager.backupWallets();
    }

    public void lockWallet() {
        if (!walletsManager.areWalletsEncrypted())
            throw new IllegalStateException("wallet is not encrypted with a password");

        if (tempAesKey == null)
            throw new IllegalStateException("wallet is already locked");

        tempAesKey = null;
    }

    public void unlockWallet(String password, long timeout) {
        verifyWalletIsAvailableAndEncrypted();

        KeyCrypterScrypt keyCrypterScrypt = getKeyCrypterScrypt();
        // The aesKey is also cached for timeout (secs) after being used to decrypt the
        // wallet, in case the user wants to manually lock the wallet before the timeout.
        tempAesKey = keyCrypterScrypt.deriveKey(password);

        if (!walletsManager.checkAESKey(tempAesKey))
            throw new IllegalStateException("incorrect password");

        if (lockTask != null) {
            // The user is overriding a prior unlock timeout.  Cancel the existing
            // lock TimerTask to prevent it from calling lockWallet() before or after the
            // new timer task does.
            lockTask.cancel();
            // Avoid the synchronized(lock) overhead of an unnecessary lockTask.cancel()
            // call the next time 'unlockwallet' is called.
            lockTask = null;
        }

        lockTask = new TimerTask() {
            @Override
            public void run() {
                if (tempAesKey != null) {
                    // Do not try to lock wallet after timeout if the user has already
                    // done so via 'lockwallet'
                    log.info("Locking wallet after {} second timeout expired.", timeout);
                    tempAesKey = null;
                }
            }
        };
        Timer timer = new Timer("Lock Wallet Timer");
        timer.schedule(lockTask, SECONDS.toMillis(timeout));
    }

    // Provided for automated wallet protection method testing, despite the
    // security risks exposed by providing users the ability to decrypt their wallets.
    public void removeWalletPassword(String password) {
        verifyWalletIsAvailableAndEncrypted();
        KeyCrypterScrypt keyCrypterScrypt = getKeyCrypterScrypt();

        KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
        if (!walletsManager.checkAESKey(aesKey))
            throw new IllegalStateException("incorrect password");

        walletsManager.decryptWallets(aesKey);
        walletsManager.backupWallets();
    }

    // Throws a RuntimeException if wallets are not available or not encrypted.
    private void verifyWalletIsAvailableAndEncrypted() {
        if (!walletsManager.areWalletsAvailable())
            throw new IllegalStateException("wallet is not yet available");

        if (!walletsManager.areWalletsEncrypted())
            throw new IllegalStateException("wallet is not encrypted with a password");
    }

    private KeyCrypterScrypt getKeyCrypterScrypt() {
        KeyCrypterScrypt keyCrypterScrypt = walletsManager.getKeyCrypterScrypt();
        if (keyCrypterScrypt == null)
            throw new IllegalStateException("wallet encrypter is not available");
        return keyCrypterScrypt;
    }
}
