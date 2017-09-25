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
import io.bisq.common.util.Utilities;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.trade.Trade;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.bitcoinj.core.Sha256Hash;

import javax.inject.Inject;
import java.math.BigInteger;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AccountAgeWitnessService {

    private KeyRing keyRing;

    @Inject
    public AccountAgeWitnessService(KeyRing keyRing) {
        this.keyRing = keyRing;
    }

    public AccountAgeWitness getPaymentAccountWitness(PaymentAccount paymentAccount, Trade trade) throws CryptoException {
        byte[] hash = getWitnessHash(paymentAccount);
        byte[] signature = Sig.sign(keyRing.getSignatureKeyPair().getPrivate(), hash);
        long tradeDate = trade.getTakeOfferDate().getTime();
        byte[] hashOfPubKey = Sha256Hash.hash(keyRing.getPubKeyRing().getSignaturePubKeyBytes());
        return new AccountAgeWitness(hash,
                hashOfPubKey,
                signature,
                tradeDate);
    }

    public byte[] getWitnessHash(PaymentAccount paymentAccount) {
        return getWitnessHash(paymentAccount.getPaymentAccountPayload(), paymentAccount.getSalt());
    }

    public byte[] getWitnessHash(PaymentAccountPayload paymentAccountPayload, byte[] salt) {
        byte[] ageWitnessInputData = paymentAccountPayload.getAgeWitnessInputData();
        final byte[] combined = ArrayUtils.addAll(ageWitnessInputData, salt);
        return Sha256Hash.hash(combined);
    }

    boolean verifyAgeWitness(byte[] peersAgeWitnessInputData,
                             AccountAgeWitness witness,
                             byte[] peersSalt,
                             PublicKey peersPublicKey,
                             int nonce,
                             byte[] signatureOfNonce) throws CryptoException {

        // Check if trade date in witness is not older than the release date of that feature (was added in v0.6)
        Date ageWitnessReleaseDate = new GregorianCalendar(2017, 9, 23).getTime();
        if (!isTradeDateAfterReleaseDate(witness.getTradeDate(), ageWitnessReleaseDate))
            return false;


        // Check if peer's pubkey is matching the hash in the witness data
        if (!verifyPubKeyHash(witness.getHashOfPubKey(), peersPublicKey))
            return false;

        final byte[] combined = ArrayUtils.addAll(peersAgeWitnessInputData, peersSalt);
        byte[] hash = Sha256Hash.hash(combined);

        // Check if the hash in the witness data matches the peer's payment account input data + salt
        if (!verifyWitnessHash(witness.getHash(), hash))
            return false;

        // Check if the witness signature is correct 
        if (!verifySignature(peersPublicKey, hash, witness.getSignature()))
            return false;

        // Check if the signature of the nonce is correct 
        return !verifySignatureOfNonce(peersPublicKey, nonce, signatureOfNonce);
    }

    boolean isTradeDateAfterReleaseDate(long tradeDateAsLong, Date ageWitnessReleaseDate) {
        // Release date minus 1 day as tolerance for not synced clocks
        Date releaseDateWithTolerance = new Date(ageWitnessReleaseDate.getTime() - TimeUnit.DAYS.toMillis(1));
        final Date tradeDate = new Date(tradeDateAsLong);
        final boolean result = tradeDate.after(releaseDateWithTolerance);
        if (!result)
            log.warn("Trade date is earlier than release date of ageWitness minus 1 day. " +
                    "ageWitnessReleaseDate={}, tradeDate={}", ageWitnessReleaseDate, tradeDate);
        return result;
    }

    boolean verifyPubKeyHash(byte[] hashOfPubKey,
                             PublicKey peersPublicKey) {
        final boolean result = Arrays.equals(Hash.getHash(Sig.getPublicKeyBytes(peersPublicKey)), hashOfPubKey);
        if (!result)
            log.warn("hashOfPubKey is not matching peers peersPublicKey. " +
                    "hashOfPubKey={}, peersPublicKey={}", Utilities.bytesAsHexString(hashOfPubKey), peersPublicKey);
        return result;
    }

    boolean verifyWitnessHash(byte[] witnessHash,
                              byte[] hash) {
        final boolean result = Arrays.equals(witnessHash, hash);
        if (!result)
            log.warn("witnessHash is not matching peers hash. " +
                    "witnessHash={}, hash={}", Utilities.bytesAsHexString(witnessHash), Utilities.bytesAsHexString(hash));
        return result;
    }

    boolean verifySignature(PublicKey peersPublicKey, byte[] data, byte[] signature) {
        try {
            return Sig.verify(peersPublicKey, data, signature);
        } catch (CryptoException e) {
            log.warn("Signature of PaymentAccountAgeWitness is not correct. " +
                            "peersPublicKey={}, data={}, signature={}",
                    peersPublicKey, Utilities.bytesAsHexString(data), Utilities.bytesAsHexString(signature));
            return false;
        }
    }

    boolean verifySignatureOfNonce(PublicKey peersPublicKey, int nonce, byte[] signature) {
        try {
            return Sig.verify(peersPublicKey, BigInteger.valueOf(nonce).toByteArray(), signature);
        } catch (CryptoException e) {
            log.warn("Signature of nonce is not correct. " +
                            "peersPublicKey={}, nonce={}, signature={}",
                    peersPublicKey, nonce, Utilities.bytesAsHexString(signature));
            return false;
        }
    }

    public boolean verifyOffersAccountAgeWitness(PaymentAccountPayload paymentAccountPayload,
                                                 byte[] peersSalt,
                                                 byte[] offersWitness) {
        byte[] witnessHash = getWitnessHash(paymentAccountPayload, peersSalt);
        final boolean result = Arrays.equals(witnessHash, offersWitness);
        if (!result)
            log.warn("witnessHash is not matching peers offersWitness. " +
                    "witnessHash={}, offersWitness={}", Utilities.bytesAsHexString(witnessHash), 
                    Utilities.bytesAsHexString(offersWitness));
        return result;
    }
}
