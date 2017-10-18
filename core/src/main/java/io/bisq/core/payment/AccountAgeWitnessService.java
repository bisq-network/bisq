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

import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.Hash;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.Sig;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.util.MathUtils;
import io.bisq.common.util.Utilities;
import io.bisq.core.offer.Offer;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.storage.P2PDataStorage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
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
    private final Map<P2PDataStorage.ByteArray, AccountAgeWitness> accountAgeWitnessMap = new HashMap<>();

    @Inject
    public AccountAgeWitnessService(KeyRing keyRing, P2PService p2PService) {
        this.keyRing = keyRing;
        this.p2PService = p2PService;
    }

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
    }

    private void addToMap(AccountAgeWitness accountAgeWitness) {
        log.debug("addToMap hash=" + Utilities.bytesAsHexString(accountAgeWitness.getHash()));
        if (!accountAgeWitnessMap.containsKey(accountAgeWitness.getHashAsByteArray()))
            accountAgeWitnessMap.put(accountAgeWitness.getHashAsByteArray(), accountAgeWitness);
    }

    public void publishAccountAgeWitness(PaymentAccountPayload paymentAccountPayload) {
        try {
            AccountAgeWitness accountAgeWitness = getAccountAgeWitness(paymentAccountPayload);
            if (!accountAgeWitnessMap.containsKey(accountAgeWitness.getHashAsByteArray()))
                p2PService.addPersistableNetworkPayload(accountAgeWitness);
        } catch (CryptoException e) {
            e.printStackTrace();
            log.error(e.toString());
        }
    }

    public Optional<AccountAgeWitness> getWitnessByHash(String hashAsHex) {
        P2PDataStorage.ByteArray hashAsByteArray = new P2PDataStorage.ByteArray(Utilities.decodeFromHex(hashAsHex));
        return accountAgeWitnessMap.containsKey(hashAsByteArray) ? Optional.of(accountAgeWitnessMap.get(hashAsByteArray)) : Optional.<AccountAgeWitness>empty();
    }

    public Optional<AccountAgeWitness> getWitnessByPaymentAccountPayload(PaymentAccountPayload paymentAccountPayload) {
        return getWitnessByHash(getWitnessHashAsHex(paymentAccountPayload));
    }

    public long getAccountAge(Offer offer) {
        if (offer.getAccountAgeWitnessHash().isPresent())
            return getAccountAge(getWitnessByHash(offer.getAccountAgeWitnessHash().get()));
        else
            return 0L;
    }

    public long getAccountAge(PaymentAccountPayload paymentAccountPayload) {
        return getAccountAge(getWitnessByPaymentAccountPayload(paymentAccountPayload));
    }

    private long getAccountAge(Optional<AccountAgeWitness> accountAgeWitnessOptional) {
        if (accountAgeWitnessOptional.isPresent()) {
            return new Date().getTime() - accountAgeWitnessOptional.get().getDate();
        } else {
            return 0L;
        }
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

    private AccountAgeWitness getAccountAgeWitness(PaymentAccountPayload paymentAccountPayload) throws CryptoException {
        byte[] hash = getWitnessHash(paymentAccountPayload);
        byte[] signature = Sig.sign(keyRing.getSignatureKeyPair().getPrivate(), hash);
        byte[] sigPubKeyHash = Hash.getSha256Ripemd160hash(keyRing.getPubKeyRing().getSignaturePubKeyBytes());
        long now = new Date().getTime();

        //TODO
        // test
        //now -= TimeUnit.DAYS.toMillis(75);

        return new AccountAgeWitness(hash,
                sigPubKeyHash,
                signature,
                now);
    }

    public byte[] getWitnessHash(PaymentAccountPayload paymentAccountPayload) {
        return getWitnessHash(paymentAccountPayload, paymentAccountPayload.getSalt());
    }

    public String getWitnessHashAsHex(PaymentAccountPayload paymentAccountPayload) {
        return Utilities.bytesAsHexString(getWitnessHash(paymentAccountPayload));
    }

    private byte[] getWitnessHash(PaymentAccountPayload paymentAccountPayload, byte[] salt) {
        byte[] ageWitnessInputData = paymentAccountPayload.getAgeWitnessInputData();
        final byte[] combined = ArrayUtils.addAll(ageWitnessInputData, salt);
        final byte[] hash = Hash.getSha256Ripemd160hash(combined);
        log.debug("getWitnessHash paymentAccountPayload={}, salt={}, ageWitnessInputData={}, combined={}, hash={}",
                paymentAccountPayload.getPaymentDetails(),
                Utilities.encodeToHex(salt),
                Utilities.encodeToHex(ageWitnessInputData),
                Utilities.encodeToHex(combined),
                Utilities.encodeToHex(hash));
        return hash;
    }

    public long getTradeLimit(PaymentAccount paymentAccount, String currencyCode) {
        return getTradeLimit(paymentAccount.getPaymentAccountPayload(), currencyCode);
    }

    public long getTradeLimit(PaymentAccountPayload paymentAccountPayload, String currencyCode) {
        final long maxTradeLimit = PaymentMethod.getPaymentMethodById(paymentAccountPayload.getPaymentMethodId()).getMaxTradeLimitAsCoin(currencyCode).value;
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            double factor;

            // TODO test
            /*Optional<AccountAgeWitness> accountAgeWitnessOptional = paymentAccount.getName() != null ?
                    getWitnessByHash(getWitnessHashAsHex(paymentAccountPayload)) :
                    Optional.empty();*/

            Optional<AccountAgeWitness> accountAgeWitnessOptional = getWitnessByHash(getWitnessHashAsHex(paymentAccountPayload));

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

    public boolean verifyAccountAgeWitness(byte[] peersAgeWitnessInputData,
                                           AccountAgeWitness witness,
                                           byte[] peersSalt,
                                           PublicKey peersPublicKey,
                                           byte[] nonce,
                                           byte[] signatureOfNonce,
                                           ErrorMessageHandler errorMessageHandler) {

        // Check if trade date in witness is not older than the release date of that feature (was added in v0.6)
        // TODO set date before releasing
        if (!isTradeDateAfterReleaseDate(witness.getDate(), new GregorianCalendar(2017, GregorianCalendar.OCTOBER, 17).getTime(), errorMessageHandler))
            return false;

        // Check if peer's pubkey is matching the one from the witness data
        if (!verifySigPubKeyHash(witness.getSigPubKeyHash(), peersPublicKey, errorMessageHandler))
            return false;

        final byte[] combined = ArrayUtils.addAll(peersAgeWitnessInputData, peersSalt);
        byte[] hash = Hash.getSha256Ripemd160hash(combined);

        // Check if the hash in the witness data matches the peer's payment account input data + salt
        if (!verifyWitnessHash(witness.getHash(), hash, errorMessageHandler))
            return false;

        // Check if the witness signature is correct 
        if (!verifySignature(peersPublicKey, hash, witness.getSignature(), errorMessageHandler))
            return false;

        // Check if the signature of the nonce is correct 
        return verifySignatureOfNonce(peersPublicKey, nonce, signatureOfNonce, errorMessageHandler);
    }

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

    boolean verifySigPubKeyHash(byte[] sigPubKeyHash,
                                PublicKey peersPublicKey,
                                ErrorMessageHandler errorMessageHandler) {
        final byte[] peersPublicKeyHash = Hash.getSha256Ripemd160hash(Sig.getPublicKeyBytes(peersPublicKey));
        final boolean result = Arrays.equals(peersPublicKeyHash, sigPubKeyHash);
        if (!result) {
            final String msg = "sigPubKeyHash is not matching peers peersPublicKey. " +
                    "sigPubKeyHash=" + Utilities.bytesAsHexString(sigPubKeyHash) + ", peersPublicKeyHash=" +
                    Utilities.bytesAsHexString(peersPublicKeyHash);
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

    public boolean verifyOffersAccountAgeWitness(PaymentAccountPayload paymentAccountPayload,
                                                 byte[] offersWitness,
                                                 ErrorMessageHandler errorMessageHandler) {
        byte[] witnessHash = getWitnessHash(paymentAccountPayload, paymentAccountPayload.getSalt());
        final boolean result = Arrays.equals(witnessHash, offersWitness);
        if (!result) {
            final String msg = "witnessHash is not matching peers offersWitness. " +
                    "witnessHash=" + Utilities.bytesAsHexString(witnessHash) + ", offersWitness=" + Utilities.bytesAsHexString(offersWitness);
            log.warn(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
        return result;
    }

    public boolean verifyTradeLimit(Offer offer,
                                    PaymentAccountPayload paymentAccountPayload,
                                    ErrorMessageHandler errorMessageHandler) {
        long maxTradeLimit = getTradeLimit(paymentAccountPayload, offer.getCurrencyCode());
        final Coin offerMaxTradeLimit = offer.getMaxTradeLimit();
        final boolean result = offerMaxTradeLimit.value == maxTradeLimit;
        if (!result) {
            String msg = "Offers max trade limit does not match with the one we calculated.\n" +
                    "OfferMaxTradeLimit=" + offerMaxTradeLimit.toFriendlyString() +
                    "; MaxTradeLimit=" + Coin.valueOf(maxTradeLimit).toFriendlyString();
            log.warn(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }
        return result;
    }
}
