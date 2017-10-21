/*
 * This file is part of bisq.
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

package io.bisq.core.payment;

import io.bisq.common.UserThread;
import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.crypto.Sig;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.util.MathUtils;
import io.bisq.common.util.Utilities;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.core.user.User;
import io.bisq.network.p2p.BootstrapListener;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.storage.P2PDataStorage;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AccountAgeWitnessService {

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
    public AccountAgeWitnessService(KeyRing keyRing, P2PService p2PService, User user) {
        this.keyRing = keyRing;
        this.p2PService = p2PService;
        this.user = user;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        p2PService.getP2PDataStorage().addPersistableNetworkPayloadMapListener(payload -> {
            if (payload instanceof AccountAgeWitness)
                addToMap((AccountAgeWitness) payload);
        });

        // At startup the P2PDataStorage initializes earlier, otherwise we ge the listener called.
        p2PService.getP2PDataStorage().getPersistableNetworkPayloadCollection().getMap().entrySet().forEach(e -> {
            if (e.getValue() instanceof AccountAgeWitness)
                addToMap((AccountAgeWitness) e.getValue());
        });

        if (p2PService.isBootstrapped()) {
            republishAllFiatAccounts();
        } else {
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onBootstrapComplete() {
                    republishAllFiatAccounts();
                }
            });
        }
    }

    // At startup we re-publish the witness data of all fiat accounts to ensure we got our data well distributed.
    private void republishAllFiatAccounts() {
        if (user.getPaymentAccounts() != null)
            user.getPaymentAccounts().stream()
                .filter(e -> !(e instanceof CryptoCurrencyAccount))
                .forEach(e -> {
                    // We delay with a random interval of 20-60 sec to ensure to be better connected and don't stress the
                    // P2P network with publishing all at once at startup time.
                    final int delayInSec = 20 + new Random().nextInt(40);
                    UserThread.runAfter(() -> p2PService.addPersistableNetworkPayload(getMyWitness(e.getPaymentAccountPayload()), true), delayInSec);
                });
    }

    private void addToMap(AccountAgeWitness accountAgeWitness) {
        log.debug("addToMap hash=" + Utilities.bytesAsHexString(accountAgeWitness.getHash()));
        if (!accountAgeWitnessMap.containsKey(accountAgeWitness.getHashAsByteArray()))
            accountAgeWitnessMap.put(accountAgeWitness.getHashAsByteArray(), accountAgeWitness);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Generic
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void publishMyAccountAgeWitness(PaymentAccountPayload paymentAccountPayload) {
        AccountAgeWitness accountAgeWitness = getMyWitness(paymentAccountPayload);
        if (!accountAgeWitnessMap.containsKey(accountAgeWitness.getHashAsByteArray()))
            p2PService.addPersistableNetworkPayload(accountAgeWitness, false);
    }

    public byte[] getAccountInputDataWithSalt(PaymentAccountPayload paymentAccountPayload) {
        return Utilities.concatenateByteArrays(paymentAccountPayload.getAgeWitnessInputData(), paymentAccountPayload.getSalt());
    }

    public long getAccountAge(AccountAgeWitness accountAgeWitness) {
        return new Date().getTime() - accountAgeWitness.getDate();
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

    private long getTradeLimit(PaymentMethod paymentMethod, String currencyCode, Optional<AccountAgeWitness> accountAgeWitnessOptional) {
        final long maxTradeLimit = paymentMethod.getMaxTradeLimitAsCoin(currencyCode).value;
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            double factor;

            AccountAge accountAgeCategory = accountAgeWitnessOptional.isPresent() ?
                getAccountAgeCategory(getAccountAge((accountAgeWitnessOptional.get()))) :
                AccountAgeWitnessService.AccountAge.LESS_ONE_MONTH;

            // TODO Fade in by date can be removed after feb 2018
            // We want to fade in the limit over 2 months to avoid that all users get limited to 25% of the limit when
            // we deploy that feature.
            final Date now = new Date();
          /*  final Date dez = new GregorianCalendar(2017, GregorianCalendar.DECEMBER, 1).getTime();
            final Date jan = new GregorianCalendar(2017, GregorianCalendar.JANUARY, 1).getTime();
            final Date feb = new GregorianCalendar(2017, GregorianCalendar.FEBRUARY, 1).getTime();
*/
            // testing

            final Date dez = new GregorianCalendar(2016, GregorianCalendar.DECEMBER, 1).getTime();
            final Date jan = new GregorianCalendar(2016, GregorianCalendar.JANUARY, 1).getTime();
            final Date feb = new GregorianCalendar(2016, GregorianCalendar.FEBRUARY, 1).getTime();

            switch (accountAgeCategory) {
                case TWO_MONTHS_OR_MORE:
                    factor = 1;
                    break;
                case ONE_TO_TWO_MONTHS:
                    if (now.before(dez)) {
                        factor = 1;
                    } else if (now.before(jan)) {
                        factor = 0.9;
                    } else if (now.before(feb)) {
                        factor = 0.75;
                    } else {
                        factor = 0.5;
                    }
                    break;
                case LESS_ONE_MONTH:
                default:
                    if (now.before(dez)) {
                        factor = 1;
                    } else if (now.before(jan)) {
                        factor = 0.75;
                    } else if (now.before(feb)) {
                        factor = 0.5;
                    } else {
                        factor = 0.25;
                    }
                    break;
            }
            log.info("accountAgeCategory={}, factor={}", accountAgeCategory, factor);
            return MathUtils.roundDoubleToLong((double) maxTradeLimit * factor);
        } else {
            return maxTradeLimit;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // My witness
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AccountAgeWitness getMyWitness(PaymentAccountPayload paymentAccountPayload) {
        try {
            byte[] accountInputDataWithSalt = getAccountInputDataWithSalt(paymentAccountPayload);
            byte[] hash = Utilities.concatenateByteArrays(accountInputDataWithSalt,
                Sig.sign(keyRing.getSignatureKeyPair().getPrivate(), accountInputDataWithSalt),
                keyRing.getPubKeyRing().getSignaturePubKeyBytes());
            long date = new Date().getTime();
            //TODO
            // test
            //date -= TimeUnit.DAYS.toMillis(75);
            return new AccountAgeWitness(hash, date);
        } catch (CryptoException e) {
            log.error(e.toString());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public byte[] getMyWitnessHash(PaymentAccountPayload paymentAccountPayload) {
        return getMyWitness(paymentAccountPayload).getHash();
    }

    public String getMyWitnessHashAsHex(PaymentAccountPayload paymentAccountPayload) {
        return Utilities.bytesAsHexString(getMyWitnessHash(paymentAccountPayload));
    }

    public long getMyAccountAge(PaymentAccountPayload paymentAccountPayload) {
        return getAccountAge(getMyWitness(paymentAccountPayload));
    }

    public long getMyTradeLimit(PaymentAccount paymentAccount, String currencyCode) {
        return getTradeLimit(paymentAccount.getPaymentMethod(), currencyCode, Optional.of(getMyWitness(paymentAccount.getPaymentAccountPayload())));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Peers witness
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<AccountAgeWitness> getPeersWitnessByHash(byte[] hash) {
        P2PDataStorage.ByteArray hashAsByteArray = new P2PDataStorage.ByteArray(hash);
        return accountAgeWitnessMap.containsKey(hashAsByteArray) ? Optional.of(accountAgeWitnessMap.get(hashAsByteArray)) : Optional.<AccountAgeWitness>empty();
    }

    public Optional<AccountAgeWitness> getPeersWitnessByHashAsHex(String hashAsHex) {
        return getPeersWitnessByHash(Utilities.decodeFromHex(hashAsHex));
    }

    public long getPeersAccountAge(Offer offer) {
        final Optional<String> accountAgeWitnessHash = offer.getAccountAgeWitnessHashAsHex();
        final Optional<AccountAgeWitness> witnessByHashAsHex = accountAgeWitnessHash.isPresent() ?
            getPeersWitnessByHashAsHex(accountAgeWitnessHash.get()) :
            Optional.<AccountAgeWitness>empty();
        return witnessByHashAsHex.isPresent() ? getAccountAge(witnessByHashAsHex.get()) : 0L;
    }

    public long getPeersTradeLimit(PaymentAccountPayload paymentAccountPayload, String currencyCode, Optional<AccountAgeWitness> accountAgeWitnessOptional) {
        return getTradeLimit(PaymentMethod.getPaymentMethodById(paymentAccountPayload.getPaymentMethodId()), currencyCode, accountAgeWitnessOptional);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Verification
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean verifyPeersAccountAgeWitness(Offer offer,
                                                PaymentAccountPayload peersPaymentAccountPayload,
                                                AccountAgeWitness witness,
                                                PubKeyRing peersPubKeyRing,
                                                byte[] peersSignatureOfAccountHash,
                                                byte[] nonce,
                                                byte[] signatureOfNonce,
                                                ErrorMessageHandler errorMessageHandler) {
        // Check if trade date in witness is not older than the release date of that feature (was added in v0.6)
        // TODO set date before releasing
        if (!isTradeDateAfterReleaseDate(witness.getDate(), new GregorianCalendar(2017, GregorianCalendar.OCTOBER, 17).getTime(), errorMessageHandler))
            return false;

        final byte[] peersAccountInputDataWithSalt = Utilities.concatenateByteArrays(peersPaymentAccountPayload.getAgeWitnessInputData(), peersPaymentAccountPayload.getSalt());
        byte[] hash = Utilities.concatenateByteArrays(peersAccountInputDataWithSalt, peersSignatureOfAccountHash, peersPubKeyRing.getSignaturePubKeyBytes());

        // Check if the hash in the witness data matches the hash derived from the data provided by the peer
        if (!verifyWitnessHash(witness.getHash(), hash, errorMessageHandler))
            return false;

        // Check if the witness signature is correct
        if (!verifyPeersTradeLimit(offer, peersPaymentAccountPayload, errorMessageHandler))
            return false;

        // Check if the witness signature is correct
        if (!verifySignature(peersPubKeyRing.getSignaturePubKey(), peersAccountInputDataWithSalt, peersSignatureOfAccountHash, errorMessageHandler))
            return false;

        // Check if the signature of the nonce is correct
        return verifySignatureOfNonce(peersPubKeyRing.getSignaturePubKey(), nonce, signatureOfNonce, errorMessageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope verification subroutines
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean isTradeDateAfterReleaseDate(long witnessDateAsLong, Date ageWitnessReleaseDate, ErrorMessageHandler errorMessageHandler) {
        // Release date minus 1 day as tolerance for not synced clocks
        Date releaseDateWithTolerance = new Date(ageWitnessReleaseDate.getTime() - TimeUnit.DAYS.toMillis(1));
        final Date witnessDate = new Date(witnessDateAsLong);
        final boolean result = witnessDate.after(releaseDateWithTolerance);
        if (!result) {
            final String msg = "Trade date is earlier than release date of ageWitness minus 1 day. " +
                "ageWitnessReleaseDate=" + ageWitnessReleaseDate + ", witnessDate=" + witnessDate;
            log.warn(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
        return result;
    }

    boolean verifyWitnessHash(byte[] witnessHash,
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
                                          PaymentAccountPayload paymentAccountPayload,
                                          ErrorMessageHandler errorMessageHandler) {
        final Optional<String> offerHashAsHexOptional = offer.getAccountAgeWitnessHashAsHex();
        Optional<AccountAgeWitness> accountAgeWitnessOptional = offerHashAsHexOptional.isPresent() ? getPeersWitnessByHashAsHex(offerHashAsHexOptional.get()) : Optional.<AccountAgeWitness>empty();
        long maxTradeLimit = getPeersTradeLimit(paymentAccountPayload, offer.getCurrencyCode(), accountAgeWitnessOptional);
        final Coin offerMaxTradeLimit = offer.getMaxTradeLimit();
        boolean result = offerMaxTradeLimit.value == maxTradeLimit;
        if (!result) {
            String msg = "Offers max trade limit does not match with the one based on his account age.\n" +
                "OfferMaxTradeLimit=" + offerMaxTradeLimit.toFriendlyString() +
                "; Account age based MaxTradeLimit=" + Coin.valueOf(maxTradeLimit).toFriendlyString();
            log.warn(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
        return result;
    }

    boolean verifySignature(PublicKey peersPublicKey,
                            byte[] data,
                            byte[] signature,
                            ErrorMessageHandler errorMessageHandler) {
        boolean result;
        try {
            result = Sig.verify(peersPublicKey, data, signature);
        } catch (CryptoException e) {
            log.warn(e.toString());
            result = false;
        }
        if (!result) {
            final String msg = "Signature of PaymentAccountAgeWitness is not correct. " +
                "peersPublicKey=" + peersPublicKey + ", data=" + Utilities.bytesAsHexString(data) +
                ", signature=" + Utilities.bytesAsHexString(signature);
            log.warn(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
        return result;
    }

    boolean verifySignatureOfNonce(PublicKey peersPublicKey,
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
