/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.api;

import bisq.core.api.model.AddressBalanceInfo;
import bisq.core.api.model.BalancesInfo;
import bisq.core.api.model.BsqBalanceInfo;
import bisq.core.api.model.BtcBalanceInfo;
import bisq.core.btc.Balances;
import bisq.core.btc.exceptions.BsqChangeBelowDustException;
import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.BsqTransferModel;
import bisq.core.btc.wallet.BsqTransferService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.Timer;
import bisq.common.UserThread;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.crypto.KeyCrypterScrypt;

import javax.inject.Inject;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.bouncycastle.crypto.params.KeyParameter;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.util.ParsingUtils.parseToCoin;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
class CoreWalletsService {

    private final Balances balances;
    private final WalletsManager walletsManager;
    private final BsqWalletService bsqWalletService;
    private final BsqTransferService bsqTransferService;
    private final BsqFormatter bsqFormatter;
    private final BtcWalletService btcWalletService;

    @Nullable
    private Timer lockTimer;

    @Nullable
    private KeyParameter tempAesKey;

    @Inject
    public CoreWalletsService(Balances balances,
                              WalletsManager walletsManager,
                              BsqWalletService bsqWalletService,
                              BsqTransferService bsqTransferService,
                              BsqFormatter bsqFormatter,
                              BtcWalletService btcWalletService) {
        this.balances = balances;
        this.walletsManager = walletsManager;
        this.bsqWalletService = bsqWalletService;
        this.bsqTransferService = bsqTransferService;
        this.bsqFormatter = bsqFormatter;
        this.btcWalletService = btcWalletService;
    }

    @Nullable
    KeyParameter getKey() {
        verifyEncryptedWalletIsUnlocked();
        return tempAesKey;
    }

    @Deprecated
    long getAvailableBalance() {
        verifyWalletsAreAvailable();
        verifyEncryptedWalletIsUnlocked();

        var balance = balances.getAvailableBalance().get();
        if (balance == null)
            throw new IllegalStateException("balance is not yet available");

        return balance.getValue();
    }

    BalancesInfo getBalances(String currencyCode) {
        verifyWalletCurrencyCodeIsValid(currencyCode);
        verifyWalletsAreAvailable();
        verifyEncryptedWalletIsUnlocked();
        if (balances.getAvailableBalance().get() == null)
            throw new IllegalStateException("balance is not yet available");

        switch (currencyCode.trim().toUpperCase()) {
            case "BSQ":
                return new BalancesInfo(getBsqBalances(), BtcBalanceInfo.EMPTY);
            case "BTC":
                return new BalancesInfo(BsqBalanceInfo.EMPTY, getBtcBalances());
            default:
                return new BalancesInfo(getBsqBalances(), getBtcBalances());
        }
    }

    long getAddressBalance(String addressString) {
        Address address = getAddressEntry(addressString).getAddress();
        return btcWalletService.getBalanceForAddress(address).value;
    }

    AddressBalanceInfo getAddressBalanceInfo(String addressString) {
        var satoshiBalance = getAddressBalance(addressString);
        var numConfirmations = getNumConfirmationsForMostRecentTransaction(addressString);
        return new AddressBalanceInfo(addressString, satoshiBalance, numConfirmations);
    }

    List<AddressBalanceInfo> getFundingAddresses() {
        verifyWalletsAreAvailable();
        verifyEncryptedWalletIsUnlocked();

        // Create a new funding address if none exists.
        if (btcWalletService.getAvailableAddressEntries().isEmpty())
            btcWalletService.getFreshAddressEntry();

        List<String> addressStrings = btcWalletService
                .getAvailableAddressEntries()
                .stream()
                .map(AddressEntry::getAddressString)
                .collect(Collectors.toList());

        // getAddressBalance is memoized, because we'll map it over addresses twice.
        // To get the balances, we'll be using .getUnchecked, because we know that
        // this::getAddressBalance cannot return null.
        var balances = memoize(this::getAddressBalance);

        boolean noAddressHasZeroBalance = addressStrings.stream()
                .allMatch(addressString -> balances.getUnchecked(addressString) != 0);

        if (noAddressHasZeroBalance) {
            var newZeroBalanceAddress = btcWalletService.getFreshAddressEntry();
            addressStrings.add(newZeroBalanceAddress.getAddressString());
        }

        return addressStrings.stream().map(address ->
                new AddressBalanceInfo(address,
                        balances.getUnchecked(address),
                        getNumConfirmationsForMostRecentTransaction(address)))
                .collect(Collectors.toList());
    }

    String getUnusedBsqAddress() {
        return bsqWalletService.getUnusedBsqAddressAsString();
    }

    void sendBsq(String address,
                 double amount,
                 TxBroadcaster.Callback callback) {
        try {
            LegacyAddress legacyAddress = getValidBsqLegacyAddress(address);
            Coin receiverAmount = getValidBsqTransferAmount(amount);
            BsqTransferModel model = bsqTransferService.getBsqTransferModel(legacyAddress, receiverAmount);
            bsqTransferService.sendFunds(model, callback);
        } catch (InsufficientMoneyException
                | BsqChangeBelowDustException
                | TransactionVerificationException
                | WalletException ex) {
            log.error("", ex);
            throw new IllegalStateException(ex);
        }
    }

    int getNumConfirmationsForMostRecentTransaction(String addressString) {
        Address address = getAddressEntry(addressString).getAddress();
        TransactionConfidence confidence = btcWalletService.getConfidenceForAddress(address);
        return confidence == null ? 0 : confidence.getDepthInBlocks();
    }

    void setWalletPassword(String password, String newPassword) {
        verifyWalletsAreAvailable();

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

    void lockWallet() {
        if (!walletsManager.areWalletsEncrypted())
            throw new IllegalStateException("wallet is not encrypted with a password");

        if (tempAesKey == null)
            throw new IllegalStateException("wallet is already locked");

        tempAesKey = null;
    }

    void unlockWallet(String password, long timeout) {
        verifyWalletIsAvailableAndEncrypted();

        KeyCrypterScrypt keyCrypterScrypt = getKeyCrypterScrypt();
        // The aesKey is also cached for timeout (secs) after being used to decrypt the
        // wallet, in case the user wants to manually lock the wallet before the timeout.
        tempAesKey = keyCrypterScrypt.deriveKey(password);

        if (!walletsManager.checkAESKey(tempAesKey))
            throw new IllegalStateException("incorrect password");

        if (lockTimer != null) {
            // The user has called unlockwallet again, before the prior unlockwallet
            // timeout has expired.  He's overriding it with a new timeout value.
            // Remove the existing lock timer to prevent it from calling lockwallet
            // before or after the new one does.
            lockTimer.stop();
            lockTimer = null;
        }

        lockTimer = UserThread.runAfter(() -> {
            if (tempAesKey != null) {
                // The unlockwallet timeout has expired;  re-lock the wallet.
                log.info("Locking wallet after {} second timeout expired.", timeout);
                tempAesKey = null;
            }
        }, timeout, SECONDS);
    }

    // Provided for automated wallet protection method testing, despite the
    // security risks exposed by providing users the ability to decrypt their wallets.
    void removeWalletPassword(String password) {
        verifyWalletIsAvailableAndEncrypted();
        KeyCrypterScrypt keyCrypterScrypt = getKeyCrypterScrypt();

        KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
        if (!walletsManager.checkAESKey(aesKey))
            throw new IllegalStateException("incorrect password");

        walletsManager.decryptWallets(aesKey);
        walletsManager.backupWallets();
    }

    // Throws a RuntimeException if wallets are not available (encrypted or not).
    private void verifyWalletsAreAvailable() {
        if (!walletsManager.areWalletsAvailable())
            throw new IllegalStateException("wallet is not yet available");
    }

    // Throws a RuntimeException if wallets are not available or not encrypted.
    private void verifyWalletIsAvailableAndEncrypted() {
        if (!walletsManager.areWalletsAvailable())
            throw new IllegalStateException("wallet is not yet available");

        if (!walletsManager.areWalletsEncrypted())
            throw new IllegalStateException("wallet is not encrypted with a password");
    }

    // Throws a RuntimeException if wallets are encrypted and locked.
    private void verifyEncryptedWalletIsUnlocked() {
        if (walletsManager.areWalletsEncrypted() && tempAesKey == null)
            throw new IllegalStateException("wallet is locked");
    }

    // Throws a RuntimeException if wallet currency code is not BSQ or BTC.
    private void verifyWalletCurrencyCodeIsValid(String currencyCode) {
        if (currencyCode == null || currencyCode.isEmpty())
            return;

        if (!currencyCode.equalsIgnoreCase("BSQ")
                && !currencyCode.equalsIgnoreCase("BTC"))
            throw new IllegalStateException(format("wallet does not support %s", currencyCode));
    }

    private BsqBalanceInfo getBsqBalances() {
        verifyWalletsAreAvailable();
        verifyEncryptedWalletIsUnlocked();

        var availableConfirmedBalance = bsqWalletService.getAvailableConfirmedBalance();
        var unverifiedBalance = bsqWalletService.getUnverifiedBalance();
        var unconfirmedChangeBalance = bsqWalletService.getUnconfirmedChangeBalance();
        var lockedForVotingBalance = bsqWalletService.getLockedForVotingBalance();
        var lockupBondsBalance = bsqWalletService.getLockupBondsBalance();
        var unlockingBondsBalance = bsqWalletService.getUnlockingBondsBalance();

        return new BsqBalanceInfo(availableConfirmedBalance.value,
                unverifiedBalance.value,
                unconfirmedChangeBalance.value,
                lockedForVotingBalance.value,
                lockupBondsBalance.value,
                unlockingBondsBalance.value);
    }

    private BtcBalanceInfo getBtcBalances() {
        verifyWalletsAreAvailable();
        verifyEncryptedWalletIsUnlocked();

        var availableBalance = balances.getAvailableBalance().get();
        if (availableBalance == null)
            throw new IllegalStateException("balance is not yet available");

        var reservedBalance = balances.getReservedBalance().get();
        if (reservedBalance == null)
            throw new IllegalStateException("reserved balance is not yet available");

        var lockedBalance = balances.getLockedBalance().get();
        if (lockedBalance == null)
            throw new IllegalStateException("locked balance is not yet available");

        return new BtcBalanceInfo(availableBalance.value,
                reservedBalance.value,
                availableBalance.add(reservedBalance).value,
                lockedBalance.value);
    }

    // Returns a LegacyAddress for the string, or a RuntimeException if invalid.
    private LegacyAddress getValidBsqLegacyAddress(String address) {
        try {
            return bsqFormatter.getAddressFromBsqAddress(address);
        } catch (Throwable t) {
            log.error("", t);
            throw new IllegalStateException(format("%s is not a valid bsq address", address));
        }
    }

    // Returns a Coin for the double amount, or a RuntimeException if invalid.
    private Coin getValidBsqTransferAmount(double amount) {
        Coin amountAsCoin = parseToCoin(Double.toString(amount), bsqFormatter);
        if (amountAsCoin.equals(Coin.ZERO))
            throw new IllegalStateException(format("%.2f bsq is an invalid send amount", amount));

        return amountAsCoin;
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

    /**
     * Memoization stores the results of expensive function calls and returns
     * the cached result when the same input occurs again.
     *
     * Resulting LoadingCache is used by calling `.get(input I)` or
     * `.getUnchecked(input I)`, depending on whether or not `f` can return null.
     * That's because CacheLoader throws an exception on null output from `f`.
     */
    private static <I, O> LoadingCache<I, O> memoize(Function<I, O> f) {
        // f::apply is used, because Guava 20.0 Function doesn't yet extend
        // Java Function.
        return CacheBuilder.newBuilder().build(CacheLoader.from(f::apply));
    }
}
