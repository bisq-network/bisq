package bisq.core.grpc;

import bisq.core.btc.Balances;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsManager;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.crypto.KeyCrypterScrypt;

import javax.inject.Inject;

import org.spongycastle.crypto.params.KeyParameter;

import java.text.DecimalFormat;

import java.math.BigDecimal;

import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
class CoreWalletsService {

    private final Balances balances;
    private final WalletsManager walletsManager;
    private final BtcWalletService btcWalletService;

    @Nullable
    private TimerTask lockTask;

    @Nullable
    private KeyParameter tempAesKey;

    private final BigDecimal satoshiDivisor = new BigDecimal(100000000);
    private final DecimalFormat btcFormat = new DecimalFormat("###,##0.00000000");
    @SuppressWarnings("BigDecimalMethodWithoutRoundingCalled")
    private final Function<Long, String> formatSatoshis = (sats) ->
            btcFormat.format(BigDecimal.valueOf(sats).divide(satoshiDivisor));

    @Inject
    public CoreWalletsService(Balances balances,
                              WalletsManager walletsManager,
                              BtcWalletService btcWalletService) {
        this.balances = balances;
        this.walletsManager = walletsManager;
        this.btcWalletService = btcWalletService;
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

    public long getAddressBalance(String addressString) {
        Address address = getAddressEntry(addressString).getAddress();
        return btcWalletService.getBalanceForAddress(address).value;
    }

    public String getFundingAddresses() {
        if (!walletsManager.areWalletsAvailable())
            throw new IllegalStateException("wallet is not yet available");

        if (walletsManager.areWalletsEncrypted() && tempAesKey == null)
            throw new IllegalStateException("wallet is locked");

        // TODO populate a List<Tuple3<String, Long, Integer>> to avoid repeated calls to
        //  fundingAddress.getAddressString() and getAddressBalance(addressString)
        List<AddressEntry> fundingAddresses = btcWalletService.getAvailableAddressEntries();

        // Create a new address with a zero balance if no addresses exist.
        if (fundingAddresses.size() == 0) {
            btcWalletService.getFreshAddressEntry();
            fundingAddresses = btcWalletService.getAvailableAddressEntries();
        }

        // Check to see if at least one of the existing addresses has a 0 balance.
        boolean hasZeroBalance = false;
        for (AddressEntry fundingAddress : fundingAddresses) {
            if (getAddressBalance(fundingAddress.getAddressString()) == 0) {
                hasZeroBalance = true;
                break;
            }
        }
        if (!hasZeroBalance) {
            // None of the existing addresses have a zero balance, create a new one.
            btcWalletService.getFreshAddressEntry();
            fundingAddresses = btcWalletService.getAvailableAddressEntries();
        }

        StringBuilder addressInfoBuilder = new StringBuilder();
        fundingAddresses.forEach(a -> {
            var addressString = a.getAddressString();
            var satoshiBalance = getAddressBalance(addressString);
            var btcBalance = formatSatoshis.apply(satoshiBalance);
            var numConfirmations = getNumConfirmationsForMostRecentTransaction(addressString);
            String addressInfo = "" + addressString
                    + " balance: " + btcBalance
                    + ((satoshiBalance > 0) ? (" confirmations: " + numConfirmations) : "")
                    + "\n";
            addressInfoBuilder.append(addressInfo);
        });

        return addressInfoBuilder.toString().trim();
    }

    public int getNumConfirmationsForMostRecentTransaction(String addressString) {
        Address address = getAddressEntry(addressString).getAddress();
        TransactionConfidence confidence = btcWalletService.getConfidenceForAddress(address);
        return confidence == null ? 0 : confidence.getDepthInBlocks();
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

    private AddressEntry getAddressEntry(String addressString) {
        Optional<AddressEntry> addressEntry =
                btcWalletService.getAddressEntryListAsImmutableList().stream()
                        .filter(e -> addressString.equals(e.getAddressString()))
                        .findFirst();

        if (!addressEntry.isPresent())
            throw new IllegalStateException(format("address %s not found in wallet", addressString));

        return addressEntry.get();
    }
}
