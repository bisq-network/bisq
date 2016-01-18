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

package io.bitsquare.trade;

import io.bitsquare.app.Version;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.common.util.JsonExclude;
import io.bitsquare.p2p.Address;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.trade.offer.Offer;
import org.bitcoinj.core.Coin;

import javax.annotation.concurrent.Immutable;
import java.io.Serializable;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

@SuppressWarnings("WeakerAccess")
@Immutable
public class Contract implements Serializable {
    // That object is sent over the wire, so we need to take care of version compatibility.
    @JsonExclude
    public static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final Offer offer;
    private final long tradeAmount;
    public final String takeOfferFeeTxID;
    public final Address arbitratorAddress;
    private final boolean isBuyerOffererAndSellerTaker;
    private final String offererAccountId;
    private final String takerAccountId;
    private final PaymentAccountContractData offererPaymentAccountContractData;
    private final PaymentAccountContractData takerPaymentAccountContractData;
    @JsonExclude
    private final PubKeyRing offererPubKeyRing;
    @JsonExclude
    private final PubKeyRing takerPubKeyRing;
    private final Address buyerAddress;
    private final Address sellerAddress;


    private final String offererPayoutAddressString;
    private final String takerPayoutAddressString;
    @JsonExclude
    private final byte[] offererBtcPubKey;
    @JsonExclude
    private final byte[] takerBtcPubKey;

    public Contract(Offer offer,
                    Coin tradeAmount,
                    String takeOfferFeeTxID,
                    Address buyerAddress,
                    Address sellerAddress,
                    Address arbitratorAddress,
                    boolean isBuyerOffererAndSellerTaker,
                    String offererAccountId,
                    String takerAccountId,
                    PaymentAccountContractData offererPaymentAccountContractData,
                    PaymentAccountContractData takerPaymentAccountContractData,
                    PubKeyRing offererPubKeyRing,
                    PubKeyRing takerPubKeyRing,
                    String offererPayoutAddressString,
                    String takerPayoutAddressString,
                    byte[] offererBtcPubKey,
                    byte[] takerBtcPubKey) {
        this.offer = offer;
        this.buyerAddress = buyerAddress;
        this.sellerAddress = sellerAddress;
        this.tradeAmount = tradeAmount.value;
        this.takeOfferFeeTxID = takeOfferFeeTxID;
        this.arbitratorAddress = arbitratorAddress;
        this.isBuyerOffererAndSellerTaker = isBuyerOffererAndSellerTaker;
        this.offererAccountId = offererAccountId;
        this.takerAccountId = takerAccountId;
        this.offererPaymentAccountContractData = offererPaymentAccountContractData;
        this.takerPaymentAccountContractData = takerPaymentAccountContractData;
        this.offererPubKeyRing = offererPubKeyRing;
        this.takerPubKeyRing = takerPubKeyRing;
        this.offererPayoutAddressString = offererPayoutAddressString;
        this.takerPayoutAddressString = takerPayoutAddressString;
        this.offererBtcPubKey = offererBtcPubKey;
        this.takerBtcPubKey = takerBtcPubKey;
    }

    public String getBuyerAccountId() {
        return isBuyerOffererAndSellerTaker ? offererAccountId : takerAccountId;
    }

    public String getSellerAccountId() {
        return isBuyerOffererAndSellerTaker ? takerAccountId : offererAccountId;
    }


    public String getBuyerPayoutAddressString() {
        return isBuyerOffererAndSellerTaker ? offererPayoutAddressString : takerPayoutAddressString;
    }

    public String getSellerPayoutAddressString() {
        return isBuyerOffererAndSellerTaker ? takerPayoutAddressString : offererPayoutAddressString;
    }

    public PubKeyRing getBuyerPubKeyRing() {
        return isBuyerOffererAndSellerTaker ? offererPubKeyRing : takerPubKeyRing;
    }

    public PubKeyRing getSellerPubKeyRing() {
        return isBuyerOffererAndSellerTaker ? takerPubKeyRing : offererPubKeyRing;
    }

    public byte[] getBuyerBtcPubKey() {
        return isBuyerOffererAndSellerTaker ? offererBtcPubKey : takerBtcPubKey;
    }

    public byte[] getSellerBtcPubKey() {
        return isBuyerOffererAndSellerTaker ? takerBtcPubKey : offererBtcPubKey;
    }

    public PaymentAccountContractData getBuyerPaymentAccountContractData() {
        return isBuyerOffererAndSellerTaker ? offererPaymentAccountContractData : takerPaymentAccountContractData;
    }

    public PaymentAccountContractData getSellerPaymentAccountContractData() {
        return isBuyerOffererAndSellerTaker ? takerPaymentAccountContractData : offererPaymentAccountContractData;
    }

    public String getPaymentMethodName() {
        // PaymentMethod need to be the same
        checkArgument(offererPaymentAccountContractData.getPaymentMethodName().equals(takerPaymentAccountContractData.getPaymentMethodName()),
                "NOT offererPaymentAccountContractData.getPaymentMethodName().equals(takerPaymentAccountContractData.getPaymentMethodName())");
        return offererPaymentAccountContractData.getPaymentMethodName();
    }

    public Coin getTradeAmount() {
        return Coin.valueOf(tradeAmount);
    }

    public Address getBuyerAddress() {
        return buyerAddress;
    }


    public Address getSellerAddress() {
        return sellerAddress;
    }

    @Override
    public String toString() {
        return "Contract{" +
                "offer=" + offer +
                ", tradeAmount=" + tradeAmount +
                ", takeOfferFeeTxID='" + takeOfferFeeTxID + '\'' +
                ", arbitratorAddress=" + arbitratorAddress +
                ", isBuyerOffererAndSellerTaker=" + isBuyerOffererAndSellerTaker +
                ", offererAccountId='" + offererAccountId + '\'' +
                ", takerAccountId='" + takerAccountId + '\'' +
                ", offererPaymentAccountContractData=" + offererPaymentAccountContractData +
                ", takerPaymentAccountContractData=" + takerPaymentAccountContractData +
                ", offererPubKeyRing=" + offererPubKeyRing +
                ", takerPubKeyRing=" + takerPubKeyRing +
                ", buyerAddress=" + buyerAddress +
                ", sellerAddress=" + sellerAddress +
                ", offererPayoutAddressString='" + offererPayoutAddressString + '\'' +
                ", takerPayoutAddressString='" + takerPayoutAddressString + '\'' +
                ", offererBtcPubKey=" + Arrays.toString(offererBtcPubKey) +
                ", takerBtcPubKey=" + Arrays.toString(takerBtcPubKey) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Contract)) return false;

        Contract contract = (Contract) o;

        if (tradeAmount != contract.tradeAmount) return false;
        if (isBuyerOffererAndSellerTaker != contract.isBuyerOffererAndSellerTaker) return false;
        if (offer != null ? !offer.equals(contract.offer) : contract.offer != null) return false;
        if (takeOfferFeeTxID != null ? !takeOfferFeeTxID.equals(contract.takeOfferFeeTxID) : contract.takeOfferFeeTxID != null)
            return false;
        if (arbitratorAddress != null ? !arbitratorAddress.equals(contract.arbitratorAddress) : contract.arbitratorAddress != null)
            return false;
        if (offererAccountId != null ? !offererAccountId.equals(contract.offererAccountId) : contract.offererAccountId != null)
            return false;
        if (takerAccountId != null ? !takerAccountId.equals(contract.takerAccountId) : contract.takerAccountId != null)
            return false;
        if (offererPaymentAccountContractData != null ? !offererPaymentAccountContractData.equals(contract.offererPaymentAccountContractData) : contract.offererPaymentAccountContractData != null)
            return false;
        if (takerPaymentAccountContractData != null ? !takerPaymentAccountContractData.equals(contract.takerPaymentAccountContractData) : contract.takerPaymentAccountContractData != null)
            return false;
        if (offererPubKeyRing != null ? !offererPubKeyRing.equals(contract.offererPubKeyRing) : contract.offererPubKeyRing != null)
            return false;
        if (takerPubKeyRing != null ? !takerPubKeyRing.equals(contract.takerPubKeyRing) : contract.takerPubKeyRing != null)
            return false;
        if (buyerAddress != null ? !buyerAddress.equals(contract.buyerAddress) : contract.buyerAddress != null)
            return false;
        if (sellerAddress != null ? !sellerAddress.equals(contract.sellerAddress) : contract.sellerAddress != null)
            return false;
        if (offererPayoutAddressString != null ? !offererPayoutAddressString.equals(contract.offererPayoutAddressString) : contract.offererPayoutAddressString != null)
            return false;
        if (takerPayoutAddressString != null ? !takerPayoutAddressString.equals(contract.takerPayoutAddressString) : contract.takerPayoutAddressString != null)
            return false;
        if (!Arrays.equals(offererBtcPubKey, contract.offererBtcPubKey)) return false;
        return Arrays.equals(takerBtcPubKey, contract.takerBtcPubKey);

    }

    @Override
    public int hashCode() {
        int result = offer != null ? offer.hashCode() : 0;
        result = 31 * result + (int) (tradeAmount ^ (tradeAmount >>> 32));
        result = 31 * result + (takeOfferFeeTxID != null ? takeOfferFeeTxID.hashCode() : 0);
        result = 31 * result + (arbitratorAddress != null ? arbitratorAddress.hashCode() : 0);
        result = 31 * result + (isBuyerOffererAndSellerTaker ? 1 : 0);
        result = 31 * result + (offererAccountId != null ? offererAccountId.hashCode() : 0);
        result = 31 * result + (takerAccountId != null ? takerAccountId.hashCode() : 0);
        result = 31 * result + (offererPaymentAccountContractData != null ? offererPaymentAccountContractData.hashCode() : 0);
        result = 31 * result + (takerPaymentAccountContractData != null ? takerPaymentAccountContractData.hashCode() : 0);
        result = 31 * result + (offererPubKeyRing != null ? offererPubKeyRing.hashCode() : 0);
        result = 31 * result + (takerPubKeyRing != null ? takerPubKeyRing.hashCode() : 0);
        result = 31 * result + (buyerAddress != null ? buyerAddress.hashCode() : 0);
        result = 31 * result + (sellerAddress != null ? sellerAddress.hashCode() : 0);
        result = 31 * result + (offererPayoutAddressString != null ? offererPayoutAddressString.hashCode() : 0);
        result = 31 * result + (takerPayoutAddressString != null ? takerPayoutAddressString.hashCode() : 0);
        result = 31 * result + (offererBtcPubKey != null ? Arrays.hashCode(offererBtcPubKey) : 0);
        result = 31 * result + (takerBtcPubKey != null ? Arrays.hashCode(takerBtcPubKey) : 0);
        return result;
    }

}
