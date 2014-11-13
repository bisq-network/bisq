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

package io.bitsquare.btc;

import io.bitsquare.BitsquareException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeePolicy {

    public static final Coin TX_FEE = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;

    // The min. REGISTRATION_FEE calculated with Transaction.MIN_NONDUST_OUTPUT would be
    // 0.00015460 which might lead to problems for the spending wallet.
    // Some web wallets don't allow more then 4 decimal places (need more investigation)
    // So we use 0.0002 as that fits also to our 4 decimal places restriction for BTC values.
    // The remaining 0.0000454 BTC is given to miners at the moment as it is lower then dust.
    public static final Coin REGISTRATION_FEE = TX_FEE.add(TX_FEE);
    public static final Coin CREATE_OFFER_FEE = REGISTRATION_FEE; // 0.0002
    public static final Coin TAKE_OFFER_FEE = CREATE_OFFER_FEE;

    private final BitcoinNetwork bitcoinNetwork;
    private final String createOfferFeeAddress;
    private final String takeOfferFeeAddress;

    @Inject
    public FeePolicy(BitcoinNetwork bitcoinNetwork) {
        this.bitcoinNetwork = bitcoinNetwork;

        switch (bitcoinNetwork) {
            case TESTNET:
                createOfferFeeAddress = "mmm8BdTcHoc5wi75RmiQYsJ2Tr1NoZmM84";
                takeOfferFeeAddress = "mmm8BdTcHoc5wi75RmiQYsJ2Tr1NoZmM84";
                break;
            case MAINNET:
                // bitsquare donation address used for the moment...
                createOfferFeeAddress = "1BVxNn3T12veSK6DgqwU4Hdn7QHcDDRag7";
                takeOfferFeeAddress = "1BVxNn3T12veSK6DgqwU4Hdn7QHcDDRag7";
                break;
            case REGTEST:
                createOfferFeeAddress = "n2upbsaKAe4PD3cc4JfS7UCqPC5oNd7Ckg";
                takeOfferFeeAddress = "n2upbsaKAe4PD3cc4JfS7UCqPC5oNd7Ckg";
                break;
            default:
                throw new BitsquareException("Unknown bitcoin network: %s", bitcoinNetwork);
        }
    }

    //TODO get address form arbitrator list
    public Address getAddressForCreateOfferFee() {
        try {
            return new Address(bitcoinNetwork.getParameters(), createOfferFeeAddress);
        } catch (AddressFormatException ex) {
            throw new BitsquareException(ex);
        }
    }

    //TODO get address form the intersection of  both traders arbitrator lists
    public Address getAddressForTakeOfferFee() {
        try {
            return new Address(bitcoinNetwork.getParameters(), takeOfferFeeAddress);
        } catch (AddressFormatException ex) {
            throw new BitsquareException(ex);
        }
    }
}
