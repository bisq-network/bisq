/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.payment;

import bisq.core.locale.CurrencyUtil;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.trade.Trade;
import bisq.core.user.User;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import bisq.common.UserThread;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Hash;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.util.MathUtils;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import java.security.PublicKey;

import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class AccountAgeWitnessService {
    private static final Date RELEASE = Utilities.getUTCDate(2017, GregorianCalendar.NOVEMBER, 11);
    public static final Date FULL_ACTIVATION = Utilities.getUTCDate(2018, GregorianCalendar.FEBRUARY, 15);

    public enum AccountAge {
        LESS_ONE_MONTH,
        ONE_TO_TWO_MONTHS,
        TWO_MONTHS_OR_MORE
    }

    private final KeyRing keyRing;
    private final P2PService p2PService;
    private final User user;

    private final Map<P2PDataStorage.ByteArray, AccountAgeWitness> accountAgeWitnessMap = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    public AccountAgeWitnessService(KeyRing keyRing, P2PService p2PService, User user,
                                    AccountAgeWitnessStorageService accountAgeWitnessStorageService,
                                    AppendOnlyDataStoreService appendOnlyDataStoreService) {
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.user = user;

        // We need to add that early (before onAllServicesInitialized) as it will be used at startup.
        appendOnlyDataStoreService.addService(accountAgeWitnessStorageService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(payload -> {
            if (payload instanceof AccountAgeWitness)
                addToMap((AccountAgeWitness) payload);
        });

        // At startup the P2PDataStorage initializes earlier, otherwise we ge the listener called.
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(e -> {
            if (e instanceof AccountAgeWitness)
                addToMap((AccountAgeWitness) e);
        });

        if (p2PService.isBootstrapped()) {
            republishAllFiatAccounts();
        } else {
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onUpdatedDataReceived() {
                    republishAllFiatAccounts();
                }
            });
        }
    }

    // At startup we re-publish the witness data of all fiat accounts to ensure we got our data well distributed.
    private void republishAllFiatAccounts() {
        if (user.getPaymentAccounts() != null)
            user.getPaymentAccounts().stream()
                    .filter(e -> !(e instanceof AssetAccount))
                    .forEach(e -> {
                        // We delay with a random interval of 20-60 sec to ensure to be better connected and don't stress the
                        // P2P network with publishing all at once at startup time.
                        final int delayInSec = 20 + new Random().nextInt(40);
                        UserThread.runAfter(() -> p2PService.addPersistableNetworkPayload(getMyWitness(e.getPaymentAccountPayload()), true), delayInSec);
                    });
    }

    private void addToMap(AccountAgeWitness accountAgeWitness) {
        accountAgeWitnessMap.putIfAbsent(accountAgeWitness.getHashAsByteArray(), accountAgeWitness);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Generic
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void publishMyAccountAgeWitness(PaymentAccountPayload paymentAccountPayload) {
        AccountAgeWitness accountAgeWitness = getMyWitness(paymentAccountPayload);
        if (!accountAgeWitnessMap.containsKey(accountAgeWitness.getHashAsByteArray()))
            p2PService.addPersistableNetworkPayload(accountAgeWitness, false);
    }

    private byte[] getAccountInputDataWithSalt(PaymentAccountPayload paymentAccountPayload) {
        return Utilities.concatenateByteArrays(paymentAccountPayload.getAgeWitnessInputData(), paymentAccountPayload.getSalt());
    }

    private AccountAgeWitness getNewWitness(PaymentAccountPayload paymentAccountPayload, PubKeyRing pubKeyRing) {
        byte[] accountInputDataWithSalt = getAccountInputDataWithSalt(paymentAccountPayload);
        byte[] hash = Hash.getSha256Ripemd160hash(Utilities.concatenateByteArrays(accountInputDataWithSalt,
                pubKeyRing.getSignaturePubKeyBytes()));
        return new AccountAgeWitness(hash, new Date().getTime());
    }

    private Optional<AccountAgeWitness> findWitness(PaymentAccountPayload paymentAccountPayload, PubKeyRing pubKeyRing) {
        byte[] accountInputDataWithSalt = getAccountInputDataWithSalt(paymentAccountPayload);
        byte[] hash = Hash.getSha256Ripemd160hash(Utilities.concatenateByteArrays(accountInputDataWithSalt,
                pubKeyRing.getSignaturePubKeyBytes()));

        return getWitnessByHash(hash);
    }

    private Optional<AccountAgeWitness> getWitnessByHash(byte[] hash) {
        P2PDataStorage.ByteArray hashAsByteArray = new P2PDataStorage.ByteArray(hash);

        final boolean containsKey = accountAgeWitnessMap.containsKey(hashAsByteArray);
        if (!containsKey)
            log.debug("hash not found in accountAgeWitnessMap");

        return accountAgeWitnessMap.containsKey(hashAsByteArray) ? Optional.of(accountAgeWitnessMap.get(hashAsByteArray)) : Optional.<AccountAgeWitness>empty();
    }

    private Optional<AccountAgeWitness> getWitnessByHashAsHex(String hashAsHex) {
        return getWitnessByHash(Utilities.decodeFromHex(hashAsHex));
    }

    public long getAccountAge(AccountAgeWitness accountAgeWitness, Date now) {
        log.debug("getAccountAge now={}, accountAgeWitness.getDate()={}", now.getTime(), accountAgeWitness.getDate());
        return now.getTime() - accountAgeWitness.getDate();
    }

    public AccountAge getAccountAgeCategory(long accountAge) {
        if (accountAge < TimeUnit.DAYS.toMillis(30)) {
            return AccountAge.LESS_ONE_MONTH;
        } else if (accountAge < TimeUnit.DAYS.toMillis(60)) {
            return AccountAge.ONE_TO_TWO_MONTHS;
        } else {
            return AccountAge.TWO_MONTHS_OR_MORE;
        }
    }

    private long getTradeLimit(Coin maxTradeLimit, String currencyCode, Optional<AccountAgeWitness> accountAgeWitnessOptional, Date now) {
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            double factor;

            final long accountAge = getAccountAge((accountAgeWitnessOptional.get()), now);
            AccountAge accountAgeCategory = accountAgeWitnessOptional
                    .map(accountAgeWitness1 -> getAccountAgeCategory(accountAge))
                    .orElse(AccountAge.LESS_ONE_MONTH);

            // TODO Fade in by date can be removed after feb 2018
            // We want to fade in the limit over 2 months to avoid that all users get limited to 25% of the limit when
            // we deploy that feature.

            switch (accountAgeCategory) {
                case TWO_MONTHS_OR_MORE:
                    factor = 1;
                    break;
                case ONE_TO_TWO_MONTHS:
                    factor = 0.5;
                    break;
                case LESS_ONE_MONTH:
                default:
                    factor = 0.25;
                    break;
            }

            final long limit = MathUtils.roundDoubleToLong((double) maxTradeLimit.value * factor);
            log.debug("accountAgeCategory={}, accountAge={}, limit={}, factor={}, accountAgeWitnessHash={}",
                    accountAgeCategory,
                    accountAge / TimeUnit.DAYS.toMillis(1) + " days",
                    Coin.valueOf(limit).toFriendlyString(),
                    factor,
                    accountAgeWitnessOptional.map(accountAgeWitness -> Utilities.bytesAsHexString(accountAgeWitness.getHash())).orElse("accountAgeWitnessOptional not present"));
            return limit;
        } else {
            return maxTradeLimit.value;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // My witness
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AccountAgeWitness getMyWitness(PaymentAccountPayload paymentAccountPayload) {
        final Optional<AccountAgeWitness> accountAgeWitnessOptional = findWitness(paymentAccountPayload, keyRing.getPubKeyRing());
        return accountAgeWitnessOptional.orElseGet(() -> getNewWitness(paymentAccountPayload, keyRing.getPubKeyRing()));
    }

    private byte[] getMyWitnessHash(PaymentAccountPayload paymentAccountPayload) {
        return getMyWitness(paymentAccountPayload).getHash();
    }

    public String getMyWitnessHashAsHex(PaymentAccountPayload paymentAccountPayload) {
        return Utilities.bytesAsHexString(getMyWitnessHash(paymentAccountPayload));
    }

    public long getMyAccountAge(PaymentAccountPayload paymentAccountPayload) {
        return getAccountAge(getMyWitness(paymentAccountPayload), new Date());
    }

    public long getMyTradeLimit(PaymentAccount paymentAccount, String currencyCode) {
        if (paymentAccount == null)
            return 0;

        Optional<AccountAgeWitness> witnessOptional = Optional.of(getMyWitness(paymentAccount.getPaymentAccountPayload()));
        return getTradeLimit(paymentAccount.getPaymentMethod().getMaxTradeLimitAsCoin(currencyCode),
                currencyCode,
                witnessOptional,
                new Date());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Peers witness
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Return -1 if witness data is not found (old versions)
    public long getMakersAccountAge(Offer offer, Date peersCurrentDate) {
        final Optional<String> accountAgeWitnessHash = offer.getAccountAgeWitnessHashAsHex();
        final Optional<AccountAgeWitness> witnessByHashAsHex = accountAgeWitnessHash.isPresent() ?
                getWitnessByHashAsHex(accountAgeWitnessHash.get()) :
                Optional.empty();
        return witnessByHashAsHex
                .map(accountAgeWitness -> getAccountAge(accountAgeWitness, peersCurrentDate))
                .orElse(-1L);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Verification
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean verifyAccountAgeWitness(Trade trade,
                                           PaymentAccountPayload peersPaymentAccountPayload,
                                           Date peersCurrentDate,
                                           PubKeyRing peersPubKeyRing,
                                           byte[] nonce,
                                           byte[] signature,
                                           ErrorMessageHandler errorMessageHandler) {
        final Optional<AccountAgeWitness> accountAgeWitnessOptional = findWitness(peersPaymentAccountPayload, peersPubKeyRing);
        // If we don't find a stored witness data we create a new dummy object which makes is easier to reuse the
        // below validation methods. This peersWitness object is not used beside for validation. Some of the
        // validation calls are pointless in the case we create a new Witness ourselves but the verifyPeersTradeLimit
        // need still be called, so we leave also the rest for sake of simplicity.
        AccountAgeWitness peersWitness;
        if (accountAgeWitnessOptional.isPresent()) {
            peersWitness = accountAgeWitnessOptional.get();
        } else {
            peersWitness = getNewWitness(peersPaymentAccountPayload, peersPubKeyRing);
            log.warn("We did not find the peers witness data. That is expected with peers using an older version.");
        }

        // Check if date in witness is not older than the release date of that feature (was added in v0.6)
        if (!isDateAfterReleaseDate(peersWitness.getDate(), RELEASE, errorMessageHandler))
            return false;

        // Check if peer current date is in tolerance range
        if (!verifyPeersCurrentDate(peersCurrentDate, errorMessageHandler))
            return false;

        final byte[] peersAccountInputDataWithSalt = Utilities.concatenateByteArrays(peersPaymentAccountPayload.getAgeWitnessInputData(), peersPaymentAccountPayload.getSalt());
        byte[] hash = Hash.getSha256Ripemd160hash(Utilities.concatenateByteArrays(peersAccountInputDataWithSalt, peersPubKeyRing.getSignaturePubKeyBytes()));

        // Check if the hash in the witness data matches the hash derived from the data provided by the peer
        final byte[] peersWitnessHash = peersWitness.getHash();
        if (!verifyWitnessHash(peersWitnessHash, hash, errorMessageHandler))
            return false;

        // Check if the peers trade limit is not less than the trade amount
        if (!verifyPeersTradeLimit(trade, peersWitness, peersCurrentDate, errorMessageHandler)) {
            log.error("verifyPeersTradeLimit failed: peersPaymentAccountPayload {}", peersPaymentAccountPayload);
            return false;
        }
        // Check if the signature is correct
        return verifySignature(peersPubKeyRing.getSignaturePubKey(), nonce, signature, errorMessageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope verification subroutines
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean isDateAfterReleaseDate(long witnessDateAsLong, Date ageWitnessReleaseDate, ErrorMessageHandler errorMessageHandler) {
        // Release date minus 1 day as tolerance for not synced clocks
        Date releaseDateWithTolerance = new Date(ageWitnessReleaseDate.getTime() - TimeUnit.DAYS.toMillis(1));
        final Date witnessDate = new Date(witnessDateAsLong);
        final boolean result = witnessDate.after(releaseDateWithTolerance);
        if (!result) {
            final String msg = "Witness date is set earlier than release date of ageWitness feature. " +
                    "ageWitnessReleaseDate=" + ageWitnessReleaseDate + ", witnessDate=" + witnessDate;
            log.warn(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
        return result;
    }

    private boolean verifyPeersCurrentDate(Date peersCurrentDate, ErrorMessageHandler errorMessageHandler) {
        final boolean result = Math.abs(peersCurrentDate.getTime() - new Date().getTime()) <= TimeUnit.DAYS.toMillis(1);
        if (!result) {
            final String msg = "Peers current date is further then 1 day off to our current date. " +
                    "PeersCurrentDate=" + peersCurrentDate + "; myCurrentDate=" + new Date();
            log.warn(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
        return result;
    }

    private boolean verifyWitnessHash(byte[] witnessHash,
                                      byte[] hash,
                                      ErrorMessageHandler errorMessageHandler) {
        final boolean result = Arrays.equals(witnessHash, hash);
        if (!result) {
            final String msg = "witnessHash is not matching peers hash. " +
                    "witnessHash=" + Utilities.bytesAsHexString(witnessHash) + ", hash=" + Utilities.bytesAsHexString(hash);
            log.warn(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
        return result;
    }

    private boolean verifyPeersTradeLimit(Trade trade,
                                          AccountAgeWitness peersWitness,
                                          Date peersCurrentDate,
                                          ErrorMessageHandler errorMessageHandler) {
        Offer offer = trade.getOffer();
        Coin tradeAmount = checkNotNull(trade.getTradeAmount());
        checkNotNull(offer);
        final String currencyCode = offer.getCurrencyCode();
        final Coin defaultMaxTradeLimit = PaymentMethod.getPaymentMethodById(offer.getOfferPayload().getPaymentMethodId()).getMaxTradeLimitAsCoin(currencyCode);
        long peersCurrentTradeLimit = getTradeLimit(defaultMaxTradeLimit, currencyCode, Optional.of(peersWitness), peersCurrentDate);
        // Makers current trade limit cannot be smaller than that in the offer
        boolean result = tradeAmount.value <= peersCurrentTradeLimit;
        if (!result) {
            String msg = "The peers trade limit is less than the traded amount.\n" +
                    "tradeAmount=" + tradeAmount.toFriendlyString() +
                    "\nPeers trade limit=" + Coin.valueOf(peersCurrentTradeLimit).toFriendlyString();
            log.warn(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
        return result;
    }

    boolean verifySignature(PublicKey peersPublicKey,
                            byte[] nonce,
                            byte[] signature,
                            ErrorMessageHandler errorMessageHandler) {
        boolean result;
        try {
            result = Sig.verify(peersPublicKey, nonce, signature);
        } catch (CryptoException e) {
            log.warn(e.toString());
            result = false;
        }
        if (!result) {
            final String msg = "Signature of nonce is not correct. " +
                    "peersPublicKey=" + peersPublicKey + ", nonce(hex)=" + Utilities.bytesAsHexString(nonce) +
                    ", signature=" + Utilities.bytesAsHexString(signature);
            log.warn(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
        return result;
    }
}
