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
    private KeyParameter tempAesKey;

    @Nullable
    KeyCrypterScrypt tempKeyCrypterScrypt;

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
            return;
        }

        if (walletsManager.areWalletsEncrypted())
            throw new IllegalStateException("wallet is encrypted with a password");

        // TODO Validate new password.
        KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
        walletsManager.encryptWallets(keyCrypterScrypt, aesKey);
    }

    public void lockWallet() {
        if (tempKeyCrypterScrypt == null && tempAesKey == null)
            throw new IllegalStateException("wallet is already locked");

        if (walletsManager.areWalletsEncrypted()) {
            // This should never happen.
            log.error("The lockwallet method found the wallet already encrypted, "
                    + "while the tempKeyCrypterScrypt and tempAesKey values that are "
                    + " supposed to be used on re-encrypt the wallet are not null.");
            tempKeyCrypterScrypt = null;
            tempAesKey = null;
            throw new IllegalStateException("wallet is already locked");
        }

        walletsManager.encryptWallets(tempKeyCrypterScrypt, tempAesKey);
        tempKeyCrypterScrypt = null;
        tempAesKey = null;
    }

    public void unlockWallet(String password, long timeout) {
        verifyWalletIsAvailableAndEncrypted();

        // We need to cache a temporary KeyCrypterScrypt for use in the manual lock method.
        // Using a different crypter instance there would invalidate the password used here
        // because it would have a different random salt value.
        tempKeyCrypterScrypt = getKeyCrypterScrypt();
        // The aesKey is also cached for timeout (secs) after being used to decrypt the
        // wallet, in case the user wants to manually lock the wallet before the timeout.
        tempAesKey = tempKeyCrypterScrypt.deriveKey(password);

        if (!walletsManager.checkAESKey(tempAesKey))
            throw new IllegalStateException("incorrect password");

        walletsManager.decryptWallets(tempAesKey);
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                if (tempKeyCrypterScrypt != null && tempAesKey != null) {
                    // Do not try to lock wallet after timeout if the user already has via 'lockwallet'.
                    log.info("Locking wallet after {} second timeout expired.", timeout);
                    lockWallet();
                }
            }
        };
        Timer timer = new Timer("Lock Wallet Timer");
        timer.schedule(timerTask, SECONDS.toMillis(timeout));
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
