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

package io.bisq.core.arbitration;

import com.google.protobuf.ByteString;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.core.arbitration.messages.DisputeCommunicationMessage;
import io.bisq.generated.protobuffer.PB;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;

import java.util.Date;
import java.util.Optional;

@EqualsAndHashCode
@Getter
@ToString
@Slf4j
public final class DisputeResult implements NetworkPayload {
    public enum Winner {
        BUYER,
        SELLER
    }

    public enum Reason {
        OTHER,
        BUG,
        USABILITY,
        SCAM,
        PROTOCOL_VIOLATION,
        NO_REPLY,
        BANK_PROBLEMS
    }

    private final String tradeId;
    private final int traderId;
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
    private byte[] arbitratorPubKey;
    private long closeDate;
    private boolean isLoserPublisher;

    transient private BooleanProperty tamperProofEvidenceProperty;
    transient private BooleanProperty idVerificationProperty;
    transient private BooleanProperty screenCastProperty;
    transient private StringProperty summaryNotesProperty;

    public DisputeResult(String tradeId, int traderId) {
        this.tradeId = tradeId;
        this.traderId = traderId;

        init();
    }

    public DisputeResult(String tradeId,
                         int traderId,
                         Winner winner,
                         int reasonOrdinal,
                         boolean tamperProofEvidence,
                         boolean idVerification,
                         boolean screenCast,
                         String summaryNotes,
                         DisputeCommunicationMessage disputeCommunicationMessage,
                         byte[] arbitratorSignature,
                         long buyerPayoutAmount,
                         long sellerPayoutAmount,
                         byte[] arbitratorPubKey,
                         long closeDate,
                         boolean isLoserPublisher) {
        this.tradeId = tradeId;
        this.traderId = traderId;
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
        this.arbitratorPubKey = arbitratorPubKey;
        this.closeDate = closeDate;
        this.isLoserPublisher = isLoserPublisher;

        init();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Optional<DisputeResult> fromProto(PB.DisputeResult proto) {
        return proto.equals(proto.getDefaultInstanceForType()) ? Optional.empty() :
                Optional.of(new DisputeResult(proto.getTradeId(),
                        proto.getTraderId(),
                        ProtoUtil.enumFromProto(DisputeResult.Winner.class, proto.getWinner().name()),
                        proto.getReasonOrdinal(),
                        proto.getTamperProofEvidence(),
                        proto.getIdVerification(),
                        proto.getScreenCast(),
                        proto.getSummaryNotes(),
                        DisputeCommunicationMessage.fromProto(proto.getDisputeCommunicationMessage()),
                        proto.getArbitratorSignature().toByteArray(),
                        proto.getBuyerPayoutAmount(),
                        proto.getSellerPayoutAmount(),
                        proto.getArbitratorPubKey().toByteArray(),
                        proto.getCloseDate(),
                        proto.getIsLoserPublisher()));
    }

    @Override
    public PB.DisputeResult toProtoMessage() {
        return PB.DisputeResult.newBuilder()
                .setTradeId(tradeId)
                .setTraderId(traderId)
                .setWinner(PB.DisputeResult.Winner.valueOf(winner.name()))
                .setReasonOrdinal(reasonOrdinal)
                .setTamperProofEvidence(tamperProofEvidence)
                .setIdVerification(idVerification)
                .setScreenCast(screenCast)
                .setSummaryNotes(summaryNotes)
                .setDisputeCommunicationMessage(disputeCommunicationMessage.toProtoNetworkEnvelope().getDisputeCommunicationMessage())
                .setArbitratorSignature(ByteString.copyFrom(arbitratorSignature))
                .setBuyerPayoutAmount(buyerPayoutAmount)
                .setSellerPayoutAmount(sellerPayoutAmount)
                .setArbitratorPubKey(ByteString.copyFrom(arbitratorPubKey))
                .setCloseDate(closeDate)
                .setIsLoserPublisher(isLoserPublisher).build();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void init() {
        tamperProofEvidenceProperty = new SimpleBooleanProperty(tamperProofEvidence);
        idVerificationProperty = new SimpleBooleanProperty(idVerification);
        screenCastProperty = new SimpleBooleanProperty(screenCast);
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
}
