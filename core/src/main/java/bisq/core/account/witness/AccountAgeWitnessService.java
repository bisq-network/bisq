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

package bisq.core.account.witness;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.filter.FilterManager;
import bisq.core.filter.PaymentAccountFilter;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferRestrictions;
import bisq.core.payment.AssetAccount;
import bisq.core.payment.ChargeBackRisk;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.arbitration.TraderDataItem;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.TradingPeer;
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
import org.bitcoinj.core.ECKey;

import javax.inject.Inject;

import java.security.PublicKey;

import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class AccountAgeWitnessService {
    private static final Date RELEASE = Utilities.getUTCDate(2017, GregorianCalendar.NOVEMBER, 11);
    public static final Date FULL_ACTIVATION = Utilities.getUTCDate(2018, GregorianCalendar.FEBRUARY, 15);
    private static final long SAFE_ACCOUNT_AGE_DATE = Utilities.getUTCDate(2019, GregorianCalendar.MARCH, 1).getTime();

    public enum AccountAge {
        UNVERIFIED,
        LESS_ONE_MONTH,
        ONE_TO_TWO_MONTHS,
        TWO_MONTHS_OR_MORE
    }

    public enum SignState {
        UNSIGNED(Res.get("offerbook.timeSinceSigning.notSigned")),
        ARBITRATOR(Res.get("offerbook.timeSinceSigning.info.arbitrator")),
        PEER_INITIAL(Res.get("offerbook.timeSinceSigning.info.peer")),
        PEER_LIMIT_LIFTED(Res.get("offerbook.timeSinceSigning.info.peerLimitLifted")),
        PEER_SIGNER(Res.get("offerbook.timeSinceSigning.info.signer"));

        private String presentation;

        SignState(String presentation) {
            this.presentation = presentation;
        }

        public String getPresentation() {
            return presentation;
        }

    }

    private final KeyRing keyRing;
    private final P2PService p2PService;
    private final User user;
    private final SignedWitnessService signedWitnessService;
    private final ChargeBackRisk chargeBackRisk;
    private final FilterManager filterManager;

    private final Map<P2PDataStorage.ByteArray, AccountAgeWitness> accountAgeWitnessMap = new HashMap<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Inject
    public AccountAgeWitnessService(KeyRing keyRing,
                                    P2PService p2PService,
                                    User user,
                                    SignedWitnessService signedWitnessService,
                                    ChargeBackRisk chargeBackRisk,
                                    AccountAgeWitnessStorageService accountAgeWitnessStorageService,
                                    AppendOnlyDataStoreService appendOnlyDataStoreService,
                                    FilterManager filterManager) {
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.user = user;
        this.signedWitnessService = signedWitnessService;
        this.chargeBackRisk = chargeBackRisk;
        this.filterManager = filterManager;

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

        // At startup the P2PDataStorage initializes earlier, otherwise we get the listener called.
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

    private Optional<AccountAgeWitness> findWitness(PaymentAccountPayload paymentAccountPayload,
                                                    PubKeyRing pubKeyRing) {
        byte[] accountInputDataWithSalt = getAccountInputDataWithSalt(paymentAccountPayload);
        byte[] hash = Hash.getSha256Ripemd160hash(Utilities.concatenateByteArrays(accountInputDataWithSalt,
                pubKeyRing.getSignaturePubKeyBytes()));

        return getWitnessByHash(hash);
    }

    private Optional<AccountAgeWitness> findWitness(Offer offer) {
        final Optional<String> accountAgeWitnessHash = offer.getAccountAgeWitnessHashAsHex();
        return accountAgeWitnessHash.isPresent() ?
                getWitnessByHashAsHex(accountAgeWitnessHash.get()) :
                Optional.empty();
    }

    private Optional<AccountAgeWitness> findTradePeerWitness(Trade trade) {
        TradingPeer tradingPeer = trade.getProcessModel().getTradingPeer();
        return (tradingPeer.getPaymentAccountPayload() == null || tradingPeer.getPubKeyRing() == null) ?
                Optional.empty() : findWitness(tradingPeer.getPaymentAccountPayload(), tradingPeer.getPubKeyRing());
    }

    private Optional<AccountAgeWitness> getWitnessByHash(byte[] hash) {
        P2PDataStorage.ByteArray hashAsByteArray = new P2PDataStorage.ByteArray(hash);

        final boolean containsKey = accountAgeWitnessMap.containsKey(hashAsByteArray);
        if (!containsKey)
            log.debug("hash not found in accountAgeWitnessMap");

        return accountAgeWitnessMap.containsKey(hashAsByteArray) ? Optional.of(accountAgeWitnessMap.get(hashAsByteArray)) : Optional.empty();
    }

    private Optional<AccountAgeWitness> getWitnessByHashAsHex(String hashAsHex) {
        return getWitnessByHash(Utilities.decodeFromHex(hashAsHex));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Witness age
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getAccountAge(AccountAgeWitness accountAgeWitness, Date now) {
        log.debug("getAccountAge now={}, accountAgeWitness.getDate()={}", now.getTime(), accountAgeWitness.getDate());
        return now.getTime() - accountAgeWitness.getDate();
    }

    // Return -1 if no witness found
    public long getAccountAge(PaymentAccountPayload paymentAccountPayload, PubKeyRing pubKeyRing) {
        return findWitness(paymentAccountPayload, pubKeyRing)
                .map(accountAgeWitness -> getAccountAge(accountAgeWitness, new Date()))
                .orElse(-1L);
    }

    public long getAccountAge(Offer offer) {
        return findWitness(offer)
                .map(accountAgeWitness -> getAccountAge(accountAgeWitness, new Date()))
                .orElse(-1L);
    }

    public long getAccountAge(Trade trade) {
        return findTradePeerWitness(trade)
                .map(accountAgeWitness -> getAccountAge(accountAgeWitness, new Date()))
                .orElse(-1L);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Signed age
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Return -1 if not signed
    public long getWitnessSignAge(AccountAgeWitness accountAgeWitness, Date now) {
        List<Long> dates = signedWitnessService.getVerifiedWitnessDateList(accountAgeWitness);
        if (dates.isEmpty()) {
            return -1L;
        } else {
            return now.getTime() - dates.get(0);
        }
    }

    // Return -1 if not signed
    public long getWitnessSignAge(Offer offer, Date now) {
        return findWitness(offer)
                .map(witness -> getWitnessSignAge(witness, now))
                .orElse(-1L);
    }

    public long getWitnessSignAge(Trade trade, Date now) {
        return findTradePeerWitness(trade)
                .map(witness -> getWitnessSignAge(witness, now))
                .orElse(-1L);
    }

    public AccountAge getPeersAccountAgeCategory(long peersAccountAge) {
        return getAccountAgeCategory(peersAccountAge);
    }

    private AccountAge getAccountAgeCategory(long accountAge) {
        if (accountAge < 0) {
            return AccountAge.UNVERIFIED;
        } else if (accountAge < TimeUnit.DAYS.toMillis(30)) {
            return AccountAge.LESS_ONE_MONTH;
        } else if (accountAge < TimeUnit.DAYS.toMillis(60)) {
            return AccountAge.ONE_TO_TWO_MONTHS;
        } else {
            return AccountAge.TWO_MONTHS_OR_MORE;
        }
    }

    // Checks trade limit based on time since signing of AccountAgeWitness
    private long getTradeLimit(Coin maxTradeLimit,
                               String currencyCode,
                               AccountAgeWitness accountAgeWitness,
                               AccountAge accountAgeCategory,
                               OfferPayload.Direction direction,
                               PaymentMethod paymentMethod) {
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            double factor;
            boolean isRisky = PaymentMethod.hasChargebackRisk(paymentMethod, currencyCode);
            if (!isRisky || direction == OfferPayload.Direction.SELL) {
                // Get age of witness rather than time since signing for non risky payment methods and for selling
                accountAgeCategory = getAccountAgeCategory(getAccountAge(accountAgeWitness, new Date()));
            }
            long limit = OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT.value;
            if (direction == OfferPayload.Direction.BUY && isRisky) {
                // Used only for bying of BTC with risky payment methods
                switch (accountAgeCategory) {
                    case TWO_MONTHS_OR_MORE:
                        factor = 1;
                        break;
                    case ONE_TO_TWO_MONTHS:
                        factor = 0.5;
                        break;
                    case LESS_ONE_MONTH:
                    case UNVERIFIED:
                    default:
                        factor = 0;
                }
            } else {
                // Used by non risky payment methods and for selling BTC with risky methods
                switch (accountAgeCategory) {
                    case TWO_MONTHS_OR_MORE:
                        factor = 1;
                        break;
                    case ONE_TO_TWO_MONTHS:
                        factor = 0.5;
                        break;
                    case LESS_ONE_MONTH:
                    case UNVERIFIED:
                        factor = 0.25;
                        break;
                    default:
                        factor = 0;
                }
            }
            if (factor > 0) {
                limit = MathUtils.roundDoubleToLong((double) maxTradeLimit.value * factor);
            }

            log.debug("accountAgeCategory={}, limit={}, factor={}, accountAgeWitnessHash={}",
                    accountAgeCategory,
                    Coin.valueOf(limit).toFriendlyString(),
                    factor,
                    Utilities.bytesAsHexString(accountAgeWitness.getHash()));
            return limit;
        } else {
            return maxTradeLimit.value;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade limit exceptions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean isImmature(AccountAgeWitness accountAgeWitness) {
        return accountAgeWitness.getDate() > SAFE_ACCOUNT_AGE_DATE;
    }

    public boolean myHasTradeLimitException(PaymentAccount myPaymentAccount) {
        return hasTradeLimitException(getMyWitness(myPaymentAccount.getPaymentAccountPayload()));
    }

    // There are no trade limits on accounts that
    // - are mature
    // - were signed by an arbitrator
    private boolean hasTradeLimitException(AccountAgeWitness accountAgeWitness) {
        return !isImmature(accountAgeWitness) || signedWitnessService.isSignedByArbitrator(accountAgeWitness);
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

    public long getMyTradeLimit(PaymentAccount paymentAccount, String currencyCode, OfferPayload.Direction
            direction) {
        if (paymentAccount == null)
            return 0;

        AccountAgeWitness accountAgeWitness = getMyWitness(paymentAccount.getPaymentAccountPayload());
        Coin maxTradeLimit = paymentAccount.getPaymentMethod().getMaxTradeLimitAsCoin(currencyCode);
        if (hasTradeLimitException(accountAgeWitness)) {
            return maxTradeLimit.value;
        }
        final long accountSignAge = getWitnessSignAge(accountAgeWitness, new Date());
        AccountAge accountAgeCategory = getAccountAgeCategory(accountSignAge);

        return getTradeLimit(maxTradeLimit,
                currencyCode,
                accountAgeWitness,
                accountAgeCategory,
                direction,
                paymentAccount.getPaymentMethod());
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
        if (!verifyPeersTradeLimit(trade.getOffer(), trade.getTradeAmount(), peersWitness, peersCurrentDate,
                errorMessageHandler)) {
            log.error("verifyPeersTradeLimit failed: peersPaymentAccountPayload {}", peersPaymentAccountPayload);
            return false;
        }
        // Check if the signature is correct
        return verifySignature(peersPubKeyRing.getSignaturePubKey(), nonce, signature, errorMessageHandler);
    }

    public boolean verifyPeersTradeAmount(Offer offer,
                                          Coin tradeAmount,
                                          ErrorMessageHandler errorMessageHandler) {
        checkNotNull(offer);
        return findWitness(offer)
                .map(witness -> verifyPeersTradeLimit(offer, tradeAmount, witness, new Date(), errorMessageHandler))
                .orElse(false);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope verification subroutines
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean isDateAfterReleaseDate(long witnessDateAsLong,
                                   Date ageWitnessReleaseDate,
                                   ErrorMessageHandler errorMessageHandler) {
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
            final String msg = "Peers current date is further than 1 day off to our current date. " +
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

    private boolean verifyPeersTradeLimit(Offer offer,
                                          Coin tradeAmount,
                                          AccountAgeWitness peersWitness,
                                          Date peersCurrentDate,
                                          ErrorMessageHandler errorMessageHandler) {
        checkNotNull(offer);
        final String currencyCode = offer.getCurrencyCode();
        final Coin defaultMaxTradeLimit = PaymentMethod.getPaymentMethodById(offer.getOfferPayload().getPaymentMethodId()).getMaxTradeLimitAsCoin(currencyCode);
        long peersCurrentTradeLimit = defaultMaxTradeLimit.value;
        if (!hasTradeLimitException(peersWitness)) {
            final long accountSignAge = getWitnessSignAge(peersWitness, peersCurrentDate);
            AccountAge accountAgeCategory = getPeersAccountAgeCategory(accountSignAge);
            OfferPayload.Direction direction = offer.isMyOffer(keyRing) ?
                    offer.getMirroredDirection() : offer.getDirection();
            peersCurrentTradeLimit = getTradeLimit(defaultMaxTradeLimit, currencyCode, peersWitness,
                    accountAgeCategory, direction, offer.getPaymentMethod());
        }
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Witness signing
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void arbitratorSignAccountAgeWitness(Coin tradeAmount,
                                                AccountAgeWitness accountAgeWitness,
                                                ECKey key,
                                                PublicKey peersPubKey) {
        signedWitnessService.signAccountAgeWitness(tradeAmount, accountAgeWitness, key, peersPubKey);
    }

    public void traderSignPeersAccountAgeWitness(Trade trade) {
        AccountAgeWitness peersWitness = findTradePeerWitness(trade).orElse(null);
        Coin tradeAmount = trade.getTradeAmount();
        checkNotNull(trade.getProcessModel().getTradingPeer().getPubKeyRing(), "Peer must have a keyring");
        PublicKey peersPubKey = trade.getProcessModel().getTradingPeer().getPubKeyRing().getSignaturePubKey();
        checkNotNull(peersWitness, "Not able to find peers witness, unable to sign for trade {}", trade.toString());
        checkNotNull(tradeAmount, "Trade amount must not be null");
        checkNotNull(peersPubKey, "Peers pub key must not be null");

        try {
            signedWitnessService.signAccountAgeWitness(tradeAmount, peersWitness, peersPubKey);
        } catch (CryptoException e) {
            log.warn("Trader failed to sign witness, exception {}", e.toString());
        }
    }

    // Arbitrator signing
    public List<TraderDataItem> getTraderPaymentAccounts(long safeDate, PaymentMethod paymentMethod,
                                                         List<Dispute> disputes) {
        return disputes.stream()
                .filter(dispute -> dispute.getContract().getPaymentMethodId().equals(paymentMethod.getId()))
                .filter(this::isNotFiltered)
                .filter(this::hasChargebackRisk)
                .filter(this::isBuyerWinner)
                .flatMap(this::getTraderData)
                .filter(Objects::nonNull)
                .filter(traderDataItem ->
                        !signedWitnessService.isSignedAccountAgeWitness(traderDataItem.getAccountAgeWitness()))
                .filter(traderDataItem -> traderDataItem.getAccountAgeWitness().getDate() < safeDate)
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean isNotFiltered(Dispute dispute) {
        boolean isFiltered = filterManager.isNodeAddressBanned(dispute.getContract().getBuyerNodeAddress()) ||
                filterManager.isNodeAddressBanned(dispute.getContract().getSellerNodeAddress()) ||
                filterManager.isCurrencyBanned(dispute.getContract().getOfferPayload().getCurrencyCode()) ||
                filterManager.isPaymentMethodBanned(
                        PaymentMethod.getPaymentMethodById(dispute.getContract().getPaymentMethodId())) ||
                filterManager.isPeersPaymentAccountDataAreBanned(dispute.getContract().getBuyerPaymentAccountPayload(),
                        new PaymentAccountFilter[1]) ||
                filterManager.isPeersPaymentAccountDataAreBanned(dispute.getContract().getSellerPaymentAccountPayload(),
                        new PaymentAccountFilter[1]);
        return !isFiltered;
    }

    private boolean hasChargebackRisk(Dispute dispute) {
        return chargeBackRisk.hasChargebackRisk(dispute.getContract().getPaymentMethodId(),
                dispute.getContract().getOfferPayload().getCurrencyCode());
    }

    private boolean isBuyerWinner(Dispute dispute) {
        if (!dispute.isClosed() || dispute.getDisputeResultProperty() == null)
            return false;
        return dispute.getDisputeResultProperty().get().getWinner() == DisputeResult.Winner.BUYER;
    }

    private Stream<TraderDataItem> getTraderData(Dispute dispute) {
        Coin tradeAmount = dispute.getContract().getTradeAmount();

        PubKeyRing buyerPubKeyRing = dispute.getContract().getBuyerPubKeyRing();
        PubKeyRing sellerPubKeyRing = dispute.getContract().getSellerPubKeyRing();

        PaymentAccountPayload buyerPaymentAccountPaload = dispute.getContract().getBuyerPaymentAccountPayload();
        PaymentAccountPayload sellerPaymentAccountPaload = dispute.getContract().getSellerPaymentAccountPayload();

        TraderDataItem buyerData = findWitness(buyerPaymentAccountPaload, buyerPubKeyRing)
                .map(witness -> new TraderDataItem(
                        buyerPaymentAccountPaload,
                        witness,
                        tradeAmount,
                        sellerPubKeyRing.getSignaturePubKey()))
                .orElse(null);
        TraderDataItem sellerData = findWitness(sellerPaymentAccountPaload, sellerPubKeyRing)
                .map(witness -> new TraderDataItem(
                        sellerPaymentAccountPaload,
                        witness,
                        tradeAmount,
                        buyerPubKeyRing.getSignaturePubKey()))
                .orElse(null);
        return Stream.of(buyerData, sellerData);
    }

    public boolean hasSignedWitness(Offer offer) {
        return findWitness(offer)
                .map(signedWitnessService::isSignedAccountAgeWitness)
                .orElse(false);
    }

    public boolean peerHasSignedWitness(Trade trade) {
        return findTradePeerWitness(trade)
                .map(signedWitnessService::isSignedAccountAgeWitness)
                .orElse(false);
    }

    public boolean accountIsSigner(AccountAgeWitness accountAgeWitness) {
        return signedWitnessService.isSignerAccountAgeWitness(accountAgeWitness);
    }

    public boolean tradeAmountIsSufficient(Coin tradeAmount) {
        return signedWitnessService.isSufficientTradeAmountForSigning(tradeAmount);
    }

    public SignState getSignState(Offer offer) {
        return findWitness(offer)
                .map(this::getSignState)
                .orElse(SignState.UNSIGNED);
    }

    public SignState getSignState(Trade trade) {
        return findTradePeerWitness(trade)
                .map(this::getSignState)
                .orElse(SignState.UNSIGNED);
    }

    public SignState getSignState(AccountAgeWitness accountAgeWitness) {
        if (signedWitnessService.isSignedByArbitrator(accountAgeWitness)) {
            return SignState.ARBITRATOR;
        } else {
            final long accountSignAge = getWitnessSignAge(accountAgeWitness, new Date());
            switch (getAccountAgeCategory(accountSignAge)) {
                case TWO_MONTHS_OR_MORE:
                    return SignState.PEER_SIGNER;
                case ONE_TO_TWO_MONTHS:
                    return SignState.PEER_LIMIT_LIFTED;
                case LESS_ONE_MONTH:
                    return SignState.PEER_INITIAL;
                case UNVERIFIED:
                default:
                    return SignState.UNSIGNED;
            }
        }
    }
}
