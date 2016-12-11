/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.btc.wallet;

import com.google.inject.Inject;
import io.bitsquare.common.handlers.ExceptionHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.crypto.ScryptUtil;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.wallet.DeterministicSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;

public class WalletsManager {
    private static final Logger log = LoggerFactory.getLogger(WalletsManager.class);

    private final BtcWalletService btcWalletService;
    private final TradeWalletService tradeWalletService;
    private final SquWalletService squWalletService;
    private final WalletsSetup walletsSetup;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public WalletsManager(BtcWalletService btcWalletService, TradeWalletService tradeWalletService, SquWalletService squWalletService, WalletsSetup walletsSetup) {
        this.btcWalletService = btcWalletService;
        this.tradeWalletService = tradeWalletService;
        this.squWalletService = squWalletService;
        this.walletsSetup = walletsSetup;
    }

    public void decryptWallets(KeyParameter aesKey) {
        btcWalletService.decryptWallet(aesKey);
        squWalletService.decryptWallet(aesKey);
        tradeWalletService.setAesKey(null);
    }

    public void encryptWallets(KeyCrypterScrypt keyCrypterScrypt, KeyParameter aesKey) {
        squWalletService.encryptWallet(keyCrypterScrypt, aesKey);
        btcWalletService.encryptWallet(keyCrypterScrypt, aesKey);

        // we save the key for the trade wallet as we don't require passwords here
        tradeWalletService.setAesKey(aesKey);
    }

    public String getWalletsAsString(boolean includePrivKeys) {
        return "BTC Wallet:\n" +
                btcWalletService.getWalletAsString(includePrivKeys) +
                "\n\nSQU Wallet:\n" +
                squWalletService.getWalletAsString(includePrivKeys);
    }

    public void restoreSeedWords(@Nullable DeterministicSeed btcSeed, @Nullable DeterministicSeed squSeed, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        walletsSetup.restoreSeedWords(btcSeed, squSeed, resultHandler, exceptionHandler);
    }

    public void backupWallets() {
        walletsSetup.backupWallets();
    }

    public void clearBackup() {
        walletsSetup.clearBackups();
    }

    public boolean areWalletsEncrypted() {
        return getBtcWallet() != null && getBtcWallet().isEncrypted();
    }

    public boolean areWalletsAvailable() {
        return getBtcWallet() != null;
    }

    private Wallet getBtcWallet() {
        return btcWalletService.getWallet();
    }

    public KeyCrypterScrypt getKeyCrypterScrypt() {
        if (areWalletsEncrypted())
            return (KeyCrypterScrypt) getBtcWallet().getKeyCrypter();
        else
            return ScryptUtil.getKeyCrypterScrypt();
    }

    public boolean checkAESKey(KeyParameter aesKey) {
        return getBtcWallet() != null && getBtcWallet().checkAESKey(aesKey);
    }

    public long getChainSeedCreationTimeSeconds() {
        return getBtcWallet() != null ? getBtcWallet().getKeyChainSeed().getCreationTimeSeconds() : 0;
    }

    public boolean hasPositiveBalance() {
        return getBtcWallet() != null &&
                (getBtcWallet().getBalance(Wallet.BalanceType.AVAILABLE).value > 0 ||
                        squWalletService.getWallet().getBalance(Wallet.BalanceType.AVAILABLE).value > 0);
    }

    public void setAesKey(KeyParameter aesKey) {
        btcWalletService.setAesKey(aesKey);
        squWalletService.setAesKey(aesKey);
        tradeWalletService.setAesKey(aesKey);
    }

    public DeterministicSeed getDecryptedSeed(KeyParameter aesKey, Wallet wallet) {
        KeyCrypter btcKeyCrypter = wallet.getKeyCrypter();
        if (btcKeyCrypter != null) {
            DeterministicSeed btcKeyChainSeed = wallet.getKeyChainSeed();
            return btcKeyChainSeed.decrypt(btcKeyCrypter, "", aesKey);
        } else {
            log.warn("keyCrypter is null");
            return null;
        }
    }
}
