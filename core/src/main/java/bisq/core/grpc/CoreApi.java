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

package bisq.core.grpc;

import bisq.core.btc.Balances;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.monetary.Price;
import bisq.core.offer.CreateOfferService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.PaymentAccount;
import bisq.core.trade.handlers.TransactionResultHandler;
import bisq.core.trade.statistics.TradeStatistics2;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;

import bisq.common.app.Version;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.crypto.KeyCrypterScrypt;

import javax.inject.Inject;

import org.spongycastle.crypto.params.KeyParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.grpc.ApiStatus.*;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Provides high level interface to functionality of core Bisq features.
 * E.g. useful for different APIs to access data of different domains of Bisq.
 */
@Slf4j
public class CoreApi {
    private final Balances balances;
    private final OfferBookService offerBookService;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final CreateOfferService createOfferService;
    private final OpenOfferManager openOfferManager;
    private final WalletsManager walletsManager;
    private final User user;

    @Nullable
    private String tempLockWalletPassword;

    @Inject
    public CoreApi(Balances balances,
                   OfferBookService offerBookService,
                   TradeStatisticsManager tradeStatisticsManager,
                   CreateOfferService createOfferService,
                   OpenOfferManager openOfferManager,
                   WalletsManager walletsManager,
                   User user) {
        this.balances = balances;
        this.offerBookService = offerBookService;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.createOfferService = createOfferService;
        this.openOfferManager = openOfferManager;
        this.walletsManager = walletsManager;
        this.user = user;
    }

    public String getVersion() {
        return Version.VERSION;
    }

    public Tuple2<Long, ApiStatus> getAvailableBalance() {
        if (!walletsManager.areWalletsAvailable())
            return new Tuple2<>(-1L, WALLET_NOT_AVAILABLE);

        if (walletsManager.areWalletsEncrypted())
            return new Tuple2<>(-1L, WALLET_IS_ENCRYPTED_WITH_UNLOCK_INSTRUCTION);

        try {
            long balance = balances.getAvailableBalance().get().getValue();
            return new Tuple2<>(balance, OK);
        } catch (Throwable t) {
            // TODO Derive new ApiStatus codes from server stack traces.
            t.printStackTrace();
            // TODO Fix bug causing NPE thrown by getAvailableBalance().
            return new Tuple2<>(-1L, INTERNAL);
        }
    }

    public List<TradeStatistics2> getTradeStatistics() {
        return new ArrayList<>(tradeStatisticsManager.getObservableTradeStatisticsSet());
    }

    public List<Offer> getOffers() {
        return offerBookService.getOffers();
    }

    public Set<PaymentAccount> getPaymentAccounts() {
        return user.getPaymentAccounts();
    }

    public void placeOffer(String currencyCode,
                           String directionAsString,
                           long priceAsLong,
                           boolean useMarketBasedPrice,
                           double marketPriceMargin,
                           long amountAsLong,
                           long minAmountAsLong,
                           double buyerSecurityDeposit,
                           String paymentAccountId,
                           TransactionResultHandler resultHandler) {
        String offerId = createOfferService.getRandomOfferId();
        OfferPayload.Direction direction = OfferPayload.Direction.valueOf(directionAsString);
        Price price = Price.valueOf(currencyCode, priceAsLong);
        Coin amount = Coin.valueOf(amountAsLong);
        Coin minAmount = Coin.valueOf(minAmountAsLong);
        PaymentAccount paymentAccount = user.getPaymentAccount(paymentAccountId);
        // We don't support atm funding from external wallet to keep it simple
        boolean useSavingsWallet = true;

        placeOffer(offerId,
                currencyCode,
                direction,
                price,
                useMarketBasedPrice,
                marketPriceMargin,
                amount,
                minAmount,
                buyerSecurityDeposit,
                paymentAccount,
                useSavingsWallet,
                resultHandler);
    }

    public void placeOffer(String offerId,
                           String currencyCode,
                           OfferPayload.Direction direction,
                           Price price,
                           boolean useMarketBasedPrice,
                           double marketPriceMargin,
                           Coin amount,
                           Coin minAmount,
                           double buyerSecurityDeposit,
                           PaymentAccount paymentAccount,
                           boolean useSavingsWallet,
                           TransactionResultHandler resultHandler) {
        Offer offer = createOfferService.createAndGetOffer(offerId,
                direction,
                currencyCode,
                amount,
                minAmount,
                price,
                useMarketBasedPrice,
                marketPriceMargin,
                buyerSecurityDeposit,
                paymentAccount);

        openOfferManager.placeOffer(offer,
                buyerSecurityDeposit,
                useSavingsWallet,
                resultHandler,
                log::error);
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

    public Tuple2<Boolean, ApiStatus> setWalletPassword(String password, String newPassword) {
        try {
            if (!walletsManager.areWalletsAvailable())
                return new Tuple2<>(false, WALLET_NOT_AVAILABLE);

            KeyCrypterScrypt keyCrypterScrypt = walletsManager.getKeyCrypterScrypt();
            if (keyCrypterScrypt == null)
                return new Tuple2<>(false, WALLET_ENCRYPTER_NOT_AVAILABLE);

            if (newPassword != null && !newPassword.isEmpty()) {
                // TODO Validate new password before replacing old password.
                if (!walletsManager.areWalletsEncrypted())
                    return new Tuple2<>(false, WALLET_NOT_ENCRYPTED);

                KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
                if (!walletsManager.checkAESKey(aesKey))
                    return new Tuple2<>(false, INCORRECT_OLD_WALLET_PASSWORD);

                walletsManager.decryptWallets(aesKey);
                aesKey = keyCrypterScrypt.deriveKey(newPassword);
                walletsManager.encryptWallets(keyCrypterScrypt, aesKey);
                return new Tuple2<>(true, OK);
            }

            if (walletsManager.areWalletsEncrypted())
                return new Tuple2<>(false, WALLET_IS_ENCRYPTED);

            // TODO Validate new password.
            KeyParameter aesKey = keyCrypterScrypt.deriveKey(password);
            walletsManager.encryptWallets(keyCrypterScrypt, aesKey);
            return new Tuple2<>(true, OK);
        } catch (Throwable t) {
            // TODO Derive new ApiStatus codes from server stack traces.
            t.printStackTrace();
            return new Tuple2<>(false, INTERNAL);
        }
    }

    public Tuple2<Boolean, ApiStatus> lockWallet() {
        if (tempLockWalletPassword != null) {
            Tuple2<Boolean, ApiStatus> encrypted = setWalletPassword(tempLockWalletPassword, null);
            tempLockWalletPassword = null;
            if (!encrypted.second.equals(OK))
                return encrypted;

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
}
