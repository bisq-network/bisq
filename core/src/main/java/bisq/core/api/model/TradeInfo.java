/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.api.model;

import bisq.core.api.model.builder.TradeInfoV1Builder;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import bisq.common.Payload;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import static bisq.core.api.model.BsqSwapTradeInfo.toBsqSwapTradeInfo;
import static bisq.core.api.model.OfferInfo.toMyOfferInfo;
import static bisq.core.api.model.OfferInfo.toOfferInfo;
import static bisq.core.api.model.PaymentAccountPayloadInfo.toPaymentAccountPayloadInfo;
import static bisq.core.offer.OfferDirection.BUY;
import static bisq.core.offer.OfferDirection.SELL;
import static java.util.Objects.requireNonNull;

@EqualsAndHashCode
@Getter
public class TradeInfo implements Payload {

    // The client cannot see Trade or its fromProto method.  We use the
    // lighter weight TradeInfo proto wrapper instead, containing just enough fields to
    // view and interact with trades.

    // Bisq v1 trade protocol fields (some are in common with the BSQ Swap protocol).
    private final OfferInfo offer;
    private final String tradeId;
    private final String shortId;
    private final long date;
    private final String role;
    private final boolean isCurrencyForTakerFeeBtc;
    private final long txFeeAsLong;
    private final long takerFeeAsLong;
    private final String takerFeeTxId;
    private final String depositTxId;
    private final String payoutTxId;
    private final long tradeAmountAsLong;
    private final long tradePrice;
    private final long tradeVolume;
    private final String tradingPeerNodeAddress;
    private final String state;
    private final String phase;
    private final String tradePeriodState;
    private final boolean isDepositPublished;
    private final boolean isDepositConfirmed;
    private final boolean isFiatSent;
    private final boolean isFiatReceived;
    private final boolean isPayoutPublished;
    private final boolean isWithdrawn;
    private final String contractAsJson;
    private final ContractInfo contract;
    // Optional BSQ swap trade protocol details (post v1).
    private BsqSwapTradeInfo bsqSwapTradeInfo;
    private final String closingStatus;

    public TradeInfo(TradeInfoV1Builder builder) {
        this.offer = builder.getOffer();
        this.tradeId = builder.getTradeId();
        this.shortId = builder.getShortId();
        this.date = builder.getDate();
        this.role = builder.getRole();
        this.isCurrencyForTakerFeeBtc = builder.isCurrencyForTakerFeeBtc();
        this.txFeeAsLong = builder.getTxFeeAsLong();
        this.takerFeeAsLong = builder.getTakerFeeAsLong();
        this.takerFeeTxId = builder.getTakerFeeTxId();
        this.depositTxId = builder.getDepositTxId();
        this.payoutTxId = builder.getPayoutTxId();
        this.tradeAmountAsLong = builder.getTradeAmountAsLong();
        this.tradePrice = builder.getTradePrice();
        this.tradeVolume = builder.getTradeVolume();
        this.tradingPeerNodeAddress = builder.getTradingPeerNodeAddress();
        this.state = builder.getState();
        this.phase = builder.getPhase();
        this.tradePeriodState = builder.getTradePeriodState();
        this.isDepositPublished = builder.isDepositPublished();
        this.isDepositConfirmed = builder.isDepositConfirmed();
        this.isFiatSent = builder.isFiatSent();
        this.isFiatReceived = builder.isFiatReceived();
        this.isPayoutPublished = builder.isPayoutPublished();
        this.isWithdrawn = builder.isWithdrawn();
        this.contractAsJson = builder.getContractAsJson();
        this.contract = builder.getContract();
        this.bsqSwapTradeInfo = null;
        this.closingStatus = builder.getClosingStatus();
    }

    public static TradeInfo toNewTradeInfo(BsqSwapTrade trade, String role) {
        // Always called by the taker, isMyOffer=false.
        return toTradeInfo(trade, role, false, 0, "Pending");
    }

    public static TradeInfo toNewTradeInfo(Trade trade) {
        // Always called by the taker, isMyOffer=false.
        return toTradeInfo(trade, null, false, "Pending");
    }

    public static TradeInfo toTradeInfo(TradeModel tradeModel,
                                        String role,
                                        boolean isMyOffer,
                                        String closingStatus) {
        if (tradeModel instanceof Trade)
            return toTradeInfo((Trade) tradeModel, role, isMyOffer, closingStatus);
        else if (tradeModel instanceof BsqSwapTrade)
            return toTradeInfo(tradeModel, role, isMyOffer, closingStatus);
        else
            throw new IllegalStateException("unsupported trade type: " + tradeModel.getClass().getSimpleName());
    }

    public static TradeInfo toTradeInfo(BsqSwapTrade bsqSwapTrade,
                                        String role,
                                        boolean isMyOffer,
                                        int numConfirmations,
                                        String closingStatus) {
        OfferInfo offerInfo = isMyOffer ? toMyOfferInfo(bsqSwapTrade.getOffer()) : toOfferInfo(bsqSwapTrade.getOffer());
        // A BSQ Swap miner tx fee is paid in full by the BTC seller (buying BSQ).
        // The BTC buyer's payout = tradeamount minus his share of miner fee.
        var isBtcSeller = (isMyOffer && bsqSwapTrade.getOffer().getDirection().equals(SELL))
                || (!isMyOffer && bsqSwapTrade.getOffer().getDirection().equals(BUY));
        var txFeeInBtc = isBtcSeller
                ? bsqSwapTrade.getTxFee().value
                : 0L;
        // A BSQ Swap trade fee is paid in full by the BTC buyer (selling BSQ).
        // The transferred BSQ (payout) is reduced by the peer's trade fee.
        var takerFeeInBsq = !isMyOffer && bsqSwapTrade.getOffer().getDirection().equals(SELL)
                ? bsqSwapTrade.getTakerFeeAsLong()
                : 0L;
        TradeInfo tradeInfo = new TradeInfoV1Builder()
                .withOffer(offerInfo)
                .withTradeId(bsqSwapTrade.getId())
                .withShortId(bsqSwapTrade.getShortId())
                .withDate(bsqSwapTrade.getDate().getTime())
                .withRole(role == null ? "" : role)
                .withIsCurrencyForTakerFeeBtc(false) // BSQ Swap fees always paid in BSQ.
                .withTxFeeAsLong(txFeeInBtc)
                .withTakerFeeAsLong(takerFeeInBsq)
                // N/A for bsq-swaps: .withTakerFeeTxId(""), .withDepositTxId(""), .withPayoutTxId("")
                .withTradeAmountAsLong(bsqSwapTrade.getAmountAsLong())
                .withTradePrice(bsqSwapTrade.getPrice().getValue())
                .withTradeVolume(bsqSwapTrade.getVolume() == null ? 0 : bsqSwapTrade.getVolume().getValue())
                .withTradingPeerNodeAddress(requireNonNull(bsqSwapTrade.getTradingPeerNodeAddress().getFullAddress()))
                .withState(bsqSwapTrade.getTradeState().name())
                .withPhase(bsqSwapTrade.getTradePhase().name())
                // N/A for bsq-swaps: .withTradePeriodState(""), .withIsDepositPublished(false), .withIsDepositConfirmed(false)
                // N/A for bsq-swaps: .withIsFiatSent(false), .withIsFiatReceived(false), .withIsPayoutPublished(false)
                // N/A for bsq-swaps: .withIsWithdrawn(false), .withContractAsJson(""), .withContract(null)
                .withClosingStatus(closingStatus)
                .build();
        tradeInfo.bsqSwapTradeInfo = toBsqSwapTradeInfo(bsqSwapTrade, isMyOffer, numConfirmations);
        return tradeInfo;
    }

    private static TradeInfo toTradeInfo(Trade trade,
                                         String role,
                                         boolean isMyOffer,
                                         String closingStatus) {
        ContractInfo contractInfo;
        if (trade.getContract() != null) {
            Contract contract = trade.getContract();
            contractInfo = new ContractInfo(contract.getBuyerPayoutAddressString(),
                    contract.getSellerPayoutAddressString(),
                    contract.getMediatorNodeAddress().getFullAddress(),
                    contract.getRefundAgentNodeAddress().getFullAddress(),
                    contract.isBuyerMakerAndSellerTaker(),
                    contract.getMakerAccountId(),
                    contract.getTakerAccountId(),
                    toPaymentAccountPayloadInfo(contract.getMakerPaymentAccountPayload()),
                    toPaymentAccountPayloadInfo(contract.getTakerPaymentAccountPayload()),
                    contract.getMakerPayoutAddressString(),
                    contract.getTakerPayoutAddressString(),
                    contract.getLockTime());
        } else {
            contractInfo = ContractInfo.emptyContract.get();
        }

        OfferInfo offerInfo = isMyOffer ? toMyOfferInfo(trade.getOffer()) : toOfferInfo(trade.getOffer());
        return new TradeInfoV1Builder()
                .withOffer(offerInfo)
                .withTradeId(trade.getId())
                .withShortId(trade.getShortId())
                .withDate(trade.getDate().getTime())
                .withRole(role == null ? "" : role)
                .withIsCurrencyForTakerFeeBtc(trade.isCurrencyForTakerFeeBtc())
                .withTxFeeAsLong(trade.getTradeTxFeeAsLong())
                .withTakerFeeAsLong(trade.getTakerFeeAsLong())
                .withTakerFeeTxId(trade.getTakerFeeTxId())
                .withDepositTxId(trade.getDepositTxId())
                .withPayoutTxId(trade.getPayoutTxId())
                .withTradeAmountAsLong(trade.getAmountAsLong())
                .withTradePrice(trade.getPrice().getValue())
                .withTradeVolume(trade.getVolume() == null ? 0 : trade.getVolume().getValue())
                .withTradingPeerNodeAddress(requireNonNull(trade.getTradingPeerNodeAddress().getFullAddress()))
                .withState(trade.getTradeState().name())
                .withPhase(trade.getTradePhase().name())
                .withTradePeriodState(trade.getTradePeriodState().name())
                .withIsDepositPublished(trade.isDepositPublished())
                .withIsDepositConfirmed(trade.isDepositConfirmed())
                .withIsFiatSent(trade.isFiatSent())
                .withIsFiatReceived(trade.isFiatReceived())
                .withIsPayoutPublished(trade.isPayoutPublished())
                .withIsWithdrawn(trade.isWithdrawn())
                .withContractAsJson(trade.getContractAsJson())
                .withContract(contractInfo)
                .withClosingStatus(closingStatus)
                .build();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.TradeInfo toProtoMessage() {
        var protoBuilder =
                bisq.proto.grpc.TradeInfo.newBuilder()
                        .setOffer(offer.toProtoMessage())
                        .setTradeId(tradeId)
                        .setShortId(shortId)
                        .setDate(date)
                        .setRole(role)
                        .setIsCurrencyForTakerFeeBtc(isCurrencyForTakerFeeBtc)
                        .setTxFeeAsLong(txFeeAsLong)
                        .setTakerFeeAsLong(takerFeeAsLong)
                        .setTakerFeeTxId(takerFeeTxId == null ? "" : takerFeeTxId)
                        .setDepositTxId(depositTxId == null ? "" : depositTxId)
                        .setPayoutTxId(payoutTxId == null ? "" : payoutTxId)
                        .setTradeAmountAsLong(tradeAmountAsLong)
                        .setTradePrice(tradePrice)
                        .setTradeVolume(tradeVolume)
                        .setTradingPeerNodeAddress(tradingPeerNodeAddress)
                        .setState(state == null ? "" : state)
                        .setPhase(phase == null ? "" : phase)
                        .setTradePeriodState(tradePeriodState == null ? "" : tradePeriodState)
                        .setIsDepositPublished(isDepositPublished)
                        .setIsDepositConfirmed(isDepositConfirmed)
                        .setIsFiatSent(isFiatSent)
                        .setIsFiatReceived(isFiatReceived)
                        .setIsPayoutPublished(isPayoutPublished)
                        .setIsWithdrawn(isWithdrawn)
                        .setClosingStatus(closingStatus);
        if (offer.isBsqSwapOffer()) {
            protoBuilder.setBsqSwapTradeInfo(bsqSwapTradeInfo.toProtoMessage());
        } else {
            protoBuilder.setContractAsJson(contractAsJson == null ? "" : contractAsJson);
            protoBuilder.setContract(contract.toProtoMessage());
        }

        return protoBuilder.build();
    }

    public static TradeInfo fromProto(bisq.proto.grpc.TradeInfo proto) {
        var tradeInfo = new TradeInfoV1Builder()
                .withOffer(OfferInfo.fromProto(proto.getOffer()))
                .withTradeId(proto.getTradeId())
                .withShortId(proto.getShortId())
                .withDate(proto.getDate())
                .withRole(proto.getRole())
                .withIsCurrencyForTakerFeeBtc(proto.getIsCurrencyForTakerFeeBtc())
                .withTxFeeAsLong(proto.getTxFeeAsLong())
                .withTakerFeeAsLong(proto.getTakerFeeAsLong())
                .withTakerFeeTxId(proto.getTakerFeeTxId())
                .withDepositTxId(proto.getDepositTxId())
                .withPayoutTxId(proto.getPayoutTxId())
                .withTradeAmountAsLong(proto.getTradeAmountAsLong())
                .withTradePrice(proto.getTradePrice())
                .withTradeVolume(proto.getTradeVolume())
                .withTradePeriodState(proto.getTradePeriodState())
                .withState(proto.getState())
                .withPhase(proto.getPhase())
                .withTradingPeerNodeAddress(proto.getTradingPeerNodeAddress())
                .withIsDepositPublished(proto.getIsDepositPublished())
                .withIsDepositConfirmed(proto.getIsDepositConfirmed())
                .withIsFiatSent(proto.getIsFiatSent())
                .withIsFiatReceived(proto.getIsFiatReceived())
                .withIsPayoutPublished(proto.getIsPayoutPublished())
                .withIsWithdrawn(proto.getIsWithdrawn())
                .withContractAsJson(proto.getContractAsJson())
                .withContract((ContractInfo.fromProto(proto.getContract())))
                .withClosingStatus(proto.getClosingStatus())
                .build();

        if (proto.getOffer().getIsBsqSwapOffer())
            tradeInfo.bsqSwapTradeInfo = BsqSwapTradeInfo.fromProto(proto.getBsqSwapTradeInfo());

        return tradeInfo;
    }

    @Override
    public String toString() {
        return "TradeInfo{" +
                "  tradeId='" + tradeId + '\'' + "\n" +
                ", shortId='" + shortId + '\'' + "\n" +
                ", date='" + date + '\'' + "\n" +
                ", role='" + role + '\'' + "\n" +
                ", isCurrencyForTakerFeeBtc='" + isCurrencyForTakerFeeBtc + '\'' + "\n" +
                ", txFeeAsLong='" + txFeeAsLong + '\'' + "\n" +
                ", takerFeeAsLong='" + takerFeeAsLong + '\'' + "\n" +
                ", takerFeeTxId='" + takerFeeTxId + '\'' + "\n" +
                ", depositTxId='" + depositTxId + '\'' + "\n" +
                ", payoutTxId='" + payoutTxId + '\'' + "\n" +
                ", tradeAmountAsLong='" + tradeAmountAsLong + '\'' + "\n" +
                ", tradePrice='" + tradePrice + '\'' + "\n" +
                ", tradeVolume='" + tradeVolume + '\'' + "\n" +
                ", tradingPeerNodeAddress='" + tradingPeerNodeAddress + '\'' + "\n" +
                ", state='" + state + '\'' + "\n" +
                ", phase='" + phase + '\'' + "\n" +
                ", tradePeriodState='" + tradePeriodState + '\'' + "\n" +
                ", isDepositPublished=" + isDepositPublished + "\n" +
                ", isDepositConfirmed=" + isDepositConfirmed + "\n" +
                ", isFiatSent=" + isFiatSent + "\n" +
                ", isFiatReceived=" + isFiatReceived + "\n" +
                ", isPayoutPublished=" + isPayoutPublished + "\n" +
                ", isWithdrawn=" + isWithdrawn + "\n" +
                ", offer=" + offer + "\n" +
                ", contractAsJson=" + contractAsJson + "\n" +
                ", contract=" + contract + "\n" +
                ", bsqSwapTradeInfo=" + bsqSwapTradeInfo + "\n" +
                ", closingStatus=" + closingStatus + "\n" +
                '}';
    }
}
