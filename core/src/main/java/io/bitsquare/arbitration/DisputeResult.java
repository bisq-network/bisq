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

package io.bitsquare.arbitration;

import io.bitsquare.app.Version;
import io.bitsquare.arbitration.messages.DisputeCommunicationMessage;
import io.bitsquare.common.wire.Payload;
import javafx.beans.property.*;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.Date;

public final class DisputeResult implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private static final Logger log = LoggerFactory.getLogger(DisputeResult.class);

    public enum DisputeFeePolicy {
        LOSER,
        SPLIT,
        WAIVE
    }

    public enum Winner {
        BUYER,
        SELLER,
        STALE_MATE
    }

    public final String tradeId;
    public final int traderId;
    private DisputeFeePolicy disputeFeePolicy;

    private boolean tamperProofEvidence;
    private boolean idVerification;
    private boolean screenCast;
    private String summaryNotes;
    private DisputeCommunicationMessage disputeCommunicationMessage;
    private byte[] arbitratorSignature;
    private long buyerPayoutAmount;
    private long sellerPayoutAmount;
    private long arbitratorPayoutAmount;
    private String arbitratorAddressAsString;
    private byte[] arbitratorPubKey;
    private long closeDate;
    private Winner winner;

    transient private BooleanProperty tamperProofEvidenceProperty = new SimpleBooleanProperty();
    transient private BooleanProperty idVerificationProperty = new SimpleBooleanProperty();
    transient private BooleanProperty screenCastProperty = new SimpleBooleanProperty();
    transient private ObjectProperty<DisputeFeePolicy> feePaymentPolicyProperty = new SimpleObjectProperty<>();
    transient private StringProperty summaryNotesProperty = new SimpleStringProperty();

    public DisputeResult(String tradeId, int traderId) {
        this.tradeId = tradeId;
        this.traderId = traderId;

        disputeFeePolicy = DisputeFeePolicy.LOSER;
        init();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            init();
        } catch (Throwable t) {
            log.trace("Cannot be deserialized." + t.getMessage());
        }
    }

    private void init() {
        tamperProofEvidenceProperty = new SimpleBooleanProperty(tamperProofEvidence);
        idVerificationProperty = new SimpleBooleanProperty(idVerification);
        screenCastProperty = new SimpleBooleanProperty(screenCast);
        feePaymentPolicyProperty = new SimpleObjectProperty<>(disputeFeePolicy);
        summaryNotesProperty = new SimpleStringProperty(summaryNotes);

        tamperProofEvidenceProperty.addListener((observable, oldValue, newValue) -> {
            tamperProofEvidence = newValue;
        });
        idVerificationProperty.addListener((observable, oldValue, newValue) -> {
            idVerification = newValue;
        });
        screenCastProperty.addListener((observable, oldValue, newValue) -> {
            screenCast = newValue;
        });
        feePaymentPolicyProperty.addListener((observable, oldValue, newValue) -> {
            disputeFeePolicy = newValue;
        });
        summaryNotesProperty.addListener((observable, oldValue, newValue) -> {
            summaryNotes = newValue;
        });
    }

    public BooleanProperty tamperProofEvidenceProperty() {
        return tamperProofEvidenceProperty;
    }

    public BooleanProperty idVerificationProperty() {
        return idVerificationProperty;
    }

    public BooleanProperty screenCastProperty() {
        return screenCastProperty;
    }

    public void setDisputeFeePolicy(DisputeFeePolicy disputeFeePolicy) {
        this.disputeFeePolicy = disputeFeePolicy;
        feePaymentPolicyProperty.set(disputeFeePolicy);
    }

    public ReadOnlyObjectProperty<DisputeFeePolicy> disputeFeePolicyProperty() {
        return feePaymentPolicyProperty;
    }

    public DisputeFeePolicy getDisputeFeePolicy() {
        return disputeFeePolicy;
    }


    public StringProperty summaryNotesProperty() {
        return summaryNotesProperty;
    }

    public void setDisputeCommunicationMessage(DisputeCommunicationMessage disputeCommunicationMessage) {
        this.disputeCommunicationMessage = disputeCommunicationMessage;
    }

    public DisputeCommunicationMessage getDisputeCommunicationMessage() {
        return disputeCommunicationMessage;
    }

    public void setArbitratorSignature(byte[] arbitratorSignature) {
        this.arbitratorSignature = arbitratorSignature;
    }

    public byte[] getArbitratorSignature() {
        return arbitratorSignature;
    }

    public void setBuyerPayoutAmount(Coin buyerPayoutAmount) {
        this.buyerPayoutAmount = buyerPayoutAmount.value;
    }

    public Coin getBuyerPayoutAmount() {
        return Coin.valueOf(buyerPayoutAmount);
    }

    public void setSellerPayoutAmount(Coin sellerPayoutAmount) {
        this.sellerPayoutAmount = sellerPayoutAmount.value;
    }

    public Coin getSellerPayoutAmount() {
        return Coin.valueOf(sellerPayoutAmount);
    }

    public void setArbitratorPayoutAmount(Coin arbitratorPayoutAmount) {
        this.arbitratorPayoutAmount = arbitratorPayoutAmount.value;
    }

    public Coin getArbitratorPayoutAmount() {
        return Coin.valueOf(arbitratorPayoutAmount);
    }

    public void setArbitratorAddressAsString(String arbitratorAddressAsString) {
        this.arbitratorAddressAsString = arbitratorAddressAsString;
    }

    public String getArbitratorAddressAsString() {
        return arbitratorAddressAsString;
    }

    public void setArbitratorPubKey(byte[] arbitratorPubKey) {
        this.arbitratorPubKey = arbitratorPubKey;
    }

    public byte[] getArbitratorPubKey() {
        return arbitratorPubKey;
    }

    public void setCloseDate(Date closeDate) {
        this.closeDate = closeDate.getTime();
    }

    public Date getCloseDate() {
        return new Date(closeDate);
    }

    public void setWinner(Winner winner) {
        this.winner = winner;
    }

    public Winner getWinner() {
        return winner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DisputeResult)) return false;

        DisputeResult that = (DisputeResult) o;

        if (traderId != that.traderId) return false;
        if (tamperProofEvidence != that.tamperProofEvidence) return false;
        if (idVerification != that.idVerification) return false;
        if (screenCast != that.screenCast) return false;
        if (buyerPayoutAmount != that.buyerPayoutAmount) return false;
        if (sellerPayoutAmount != that.sellerPayoutAmount) return false;
        if (arbitratorPayoutAmount != that.arbitratorPayoutAmount) return false;
        if (closeDate != that.closeDate) return false;
        if (tradeId != null ? !tradeId.equals(that.tradeId) : that.tradeId != null) return false;
        if (disputeFeePolicy != that.disputeFeePolicy) return false;
        if (summaryNotes != null ? !summaryNotes.equals(that.summaryNotes) : that.summaryNotes != null) return false;
        if (disputeCommunicationMessage != null ? !disputeCommunicationMessage.equals(that.disputeCommunicationMessage) : that.disputeCommunicationMessage != null)
            return false;
        if (!Arrays.equals(arbitratorSignature, that.arbitratorSignature)) return false;
        if (arbitratorAddressAsString != null ? !arbitratorAddressAsString.equals(that.arbitratorAddressAsString) : that.arbitratorAddressAsString != null)
            return false;
        if (!Arrays.equals(arbitratorPubKey, that.arbitratorPubKey)) return false;
        return winner == that.winner;

    }

    @Override
    public int hashCode() {
        int result = tradeId != null ? tradeId.hashCode() : 0;
        result = 31 * result + traderId;
        result = 31 * result + (disputeFeePolicy != null ? disputeFeePolicy.hashCode() : 0);
        result = 31 * result + (tamperProofEvidence ? 1 : 0);
        result = 31 * result + (idVerification ? 1 : 0);
        result = 31 * result + (screenCast ? 1 : 0);
        result = 31 * result + (summaryNotes != null ? summaryNotes.hashCode() : 0);
        result = 31 * result + (disputeCommunicationMessage != null ? disputeCommunicationMessage.hashCode() : 0);
        result = 31 * result + (arbitratorSignature != null ? Arrays.hashCode(arbitratorSignature) : 0);
        result = 31 * result + (int) (buyerPayoutAmount ^ (buyerPayoutAmount >>> 32));
        result = 31 * result + (int) (sellerPayoutAmount ^ (sellerPayoutAmount >>> 32));
        result = 31 * result + (int) (arbitratorPayoutAmount ^ (arbitratorPayoutAmount >>> 32));
        result = 31 * result + (arbitratorAddressAsString != null ? arbitratorAddressAsString.hashCode() : 0);
        result = 31 * result + (arbitratorPubKey != null ? Arrays.hashCode(arbitratorPubKey) : 0);
        result = 31 * result + (int) (closeDate ^ (closeDate >>> 32));
        result = 31 * result + (winner != null ? winner.hashCode() : 0);
        return result;
    }
}
