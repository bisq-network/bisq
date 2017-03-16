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

package io.bisq.network_messages.arbitration;

import com.google.protobuf.ByteString;
import io.bisq.app.Version;
import io.bisq.network_messages.wire.Payload;
import io.bisq.common.wire.proto.Messages;
import javafx.beans.property.*;
import lombok.EqualsAndHashCode;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Date;

@EqualsAndHashCode
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

    // only append new values as we use the ordinal value
    public enum Reason {
        OTHER,
        BUG,
        USABILITY,
        SCAM,
        PROTOCOL_VIOLATION,
        NO_REPLY
    }

    public final String tradeId;
    public final int traderId;
    private DisputeFeePolicy disputeFeePolicy;
    private Winner winner;

    private int reasonOrdinal = Reason.OTHER.ordinal();

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
    private boolean isLoserPublisher;

    transient private BooleanProperty tamperProofEvidenceProperty = new SimpleBooleanProperty();
    transient private BooleanProperty idVerificationProperty = new SimpleBooleanProperty();
    transient private BooleanProperty screenCastProperty = new SimpleBooleanProperty();
    transient private ObjectProperty<DisputeFeePolicy> feePaymentPolicyProperty = new SimpleObjectProperty<>();
    transient private StringProperty summaryNotesProperty = new SimpleStringProperty();

    public DisputeResult(String tradeId, int traderId) {
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.disputeFeePolicy = DisputeFeePolicy.LOSER;
        init();
    }

    public DisputeResult(String tradeId, int traderId, DisputeFeePolicy disputeFeePolicy, Winner winner,
                         int reasonOrdinal, boolean tamperProofEvidence, boolean idVerification, boolean screenCast,
                         String summaryNotes, DisputeCommunicationMessage disputeCommunicationMessage,
                         byte[] arbitratorSignature, long buyerPayoutAmount, long sellerPayoutAmount,
                         long arbitratorPayoutAmount, String arbitratorAddressAsString, byte[] arbitratorPubKey,
                         long closeDate, boolean isLoserPublisher) {
        this.tradeId = tradeId;
        this.traderId = traderId;
        this.disputeFeePolicy = disputeFeePolicy;
        this.winner = winner;
        this.reasonOrdinal = reasonOrdinal;
        this.tamperProofEvidence = tamperProofEvidence;
        this.idVerification = idVerification;
        this.screenCast = screenCast;
        this.summaryNotes = summaryNotes;
        this.disputeCommunicationMessage = disputeCommunicationMessage;
        this.arbitratorSignature = arbitratorSignature;
        this.buyerPayoutAmount = buyerPayoutAmount;
        this.sellerPayoutAmount = sellerPayoutAmount;
        this.arbitratorPayoutAmount = arbitratorPayoutAmount;
        this.arbitratorAddressAsString = arbitratorAddressAsString;
        this.arbitratorPubKey = arbitratorPubKey;
        this.closeDate = closeDate;
        this.isLoserPublisher = isLoserPublisher;
        init();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            init();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
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

    public void setReason(Reason reason) {
        this.reasonOrdinal = reason.ordinal();
    }

    public Reason getReason() {
        if (reasonOrdinal < Reason.values().length)
            return Reason.values()[reasonOrdinal];
        else
            return Reason.OTHER;
    }

    public void setSummaryNotes(String summaryNotes) {
        this.summaryNotesProperty.set(summaryNotes);
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

    public void setLoserIsPublisher(boolean loserPublisher) {
        this.isLoserPublisher = loserPublisher;
    }

    public boolean isLoserPublisher() {
        return isLoserPublisher;
    }

    @Override
    public Messages.DisputeResult toProtoBuf() {
        return Messages.DisputeResult.newBuilder()
                .setTradeId(tradeId)
                .setTraderId(traderId)
                .setDisputeFeePolicy(Messages.DisputeResult.DisputeFeePolicy.forNumber(disputeFeePolicy.ordinal()))
                .setWinner(Messages.DisputeResult.Winner.forNumber(winner.ordinal()))
                .setReasonOrdinal(reasonOrdinal)
                .setTamperProofEvidence(tamperProofEvidence)
                .setIdVerification(idVerification)
                .setScreenCast(screenCast)
                .setSummaryNotes(summaryNotes)
                .setDisputeCommunicationMessage(disputeCommunicationMessage.toProtoBuf().getDisputeCommunicationMessage())
                .setArbitratorSignature(ByteString.copyFrom(arbitratorSignature))
                .setBuyerPayoutAmount(buyerPayoutAmount)
                .setSellerPayoutAmount(sellerPayoutAmount)
                .setArbitratorPayoutAmount(arbitratorPayoutAmount)
                .setArbitratorAddressAsString(arbitratorAddressAsString)
                .setArbitratorPubKey(ByteString.copyFrom(arbitratorPubKey))
                .setCloseDate(closeDate)
                .setIsLoserPublisher(isLoserPublisher).build();
    }
}
