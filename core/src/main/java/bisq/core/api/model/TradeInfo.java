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

import java.util.function.BiFunction;
import java.util.function.Function;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import static bisq.core.api.model.BsqSwapTradeInfo.toBsqSwapTradeInfo;
import static bisq.core.api.model.OfferInfo.toMyInactiveOfferInfo;
import static bisq.core.api.model.OfferInfo.toOfferInfo;
import static bisq.core.api.model.PaymentAccountPayloadInfo.toPaymentAccountPayloadInfo;
import static bisq.core.offer.OfferDirection.BUY;
import static bisq.core.offer.OfferDirection.SELL;
import static bisq.core.util.PriceUtil.reformatMarketPrice;
import static bisq.core.util.VolumeUtil.formatVolume;
import static java.util.Objects.requireNonNull;

@EqualsAndHashCode
@Getter
public class TradeInfo implements Payload {

    // The client cannot see Trade or its fromProto method.  We use the
    // lighter weight TradeInfo proto wrapper instead, containing just enough fields to
    // view and interact with trades.

    private static final BiFunction<TradeModel, Boolean, OfferInfo> toOfferInfo = (tradeModel, isMyOffer) ->
            isMyOffer ? toMyInactiveOfferInfo(tradeModel.getOffer()) : toOfferInfo(tradeModel.getOffer());

    private static final Function<TradeModel, String> toPeerNodeAddress = (tradeModel) ->
            tradeModel.getTradingPeerNodeAddress() == null
                    ? ""
                    : tradeModel.getTradingPeerNodeAddress().getFullAddress();

    private static final Function<TradeModel, String> toRoundedVolume = (tradeModel) ->
            tradeModel.getVolume() == null
                    ? ""
                    : formatVolume(requireNonNull(tradeModel.getVolume()));

    private static final Function<TradeModel, String> toPreciseTradePrice = (tradeModel) ->
            reformatMarketPrice(requireNonNull(tradeModel.getPrice()).toPlainString(),
                    tradeModel.getOffer().getCurrencyCode());

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
    private final String tradePrice;
    private final String tradeVolume;
    private final String tradingPeerNodeAddress;
    private final String state;
    private final String phase;
    private final String tradePeriodState;
    private final boolean isDepositPublished;
    private final boolean isDepositConfirmed;
    private final boolean isPaymentStartedMessageSent;
    private final boolean isPaymentReceivedMessageSent;
    private final boolean isPayoutPublished;
    private final boolean isCompleted;
    private final String contractAsJson;
    private final ContractInfo contract;
    // Optional BSQ swap trade protocol details (post v1).
    private BsqSwapTradeInfo bsqSwapTradeInfo;
    private final boolean isTakerApiUser;
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
        this.isPaymentStartedMessageSent = builder.isPaymentStartedMessageSent();
        this.isPaymentReceivedMessageSent = builder.isPaymentReceivedMessageSent();
        this.isPayoutPublished = builder.isPayoutPublished();
        this.isCompleted = builder.isCompleted();
        this.contractAsJson = builder.getContractAsJson();
        this.contract = builder.getContract();
        this.bsqSwapTradeInfo = null;
        this.isTakerApiUser = builder.isTakerApiUser();
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
        var offerInfo = toOfferInfo.apply(bsqSwapTrade, isMyOffer);
        // A BSQ Swap miner tx fee is paid in full by the BTC seller (buying BSQ).
        // The BTC buyer's payout = tradeamount minus his share of miner fee.
        var isBtcSeller = (isMyOffer && bsqSwapTrade.getOffer().getDirection().equals(SELL))
                || (!isMyOffer && bsqSwapTrade.getOffer().getDirection().equals(BUY));
        var txFeeInBtc = isBtcSeller ? bsqSwapTrade.getTxFee().value : 0L;
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
                .withIsCurrencyForTakerFeeBtc(false) // BSQ Swap fee is always paid in BSQ.
                .withTxFeeAsLong(txFeeInBtc)
                .withTakerFeeAsLong(takerFeeInBsq)
                // N/A for bsq-swaps: takerFeeTxId, depositTxId, payoutTxId
                .withTradeAmountAsLong(bsqSwapTrade.getAmountAsLong())
                .withTradePrice(toPreciseTradePrice.apply(bsqSwapTrade))
                .withTradeVolume(toRoundedVolume.apply(bsqSwapTrade))
                .withTradingPeerNodeAddress(toPeerNodeAddress.apply(bsqSwapTrade))
                .withState(bsqSwapTrade.getTradeState().name())
                .withPhase(bsqSwapTrade.getTradePhase().name())
                // N/A for bsq-swaps: tradePeriodState, isDepositPublished, isDepositConfirmed
                // N/A for bsq-swaps: isPaymentStartedMessageSent, isPaymentReceivedMessageSent, isPayoutPublished
                // N/A for bsq-swaps: isCompleted, contractAsJson, contract
                .withIsTakerApiUser(bsqSwapTrade.isTakerApiUser())
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

        var offerInfo = toOfferInfo.apply(trade, isMyOffer);
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
                .withTradePrice(toPreciseTradePrice.apply(trade))
                .withTradeVolume(toRoundedVolume.apply(trade))
                .withTradingPeerNodeAddress(toPeerNodeAddress.apply(trade))
                .withState(trade.getTradeState().name())
                .withPhase(trade.getTradePhase().name())
                .withTradePeriodState(trade.getTradePeriodState().name())
                .withIsDepositPublished(trade.isDepositPublished())
                .withIsDepositConfirmed(trade.isDepositConfirmed())
                .withIsPaymentStartedMessageSent(trade.isFiatSent())
                .withIsPaymentReceivedMessageSent(trade.isFiatReceived())
                .withIsPayoutPublished(trade.isPayoutPublished())
                .withIsCompleted(trade.isWithdrawn())
                .withContractAsJson(trade.getContractAsJson())
                .withContract(contractInfo)
                .withIsTakerApiUser(trade.isTakerApiUser())
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
                        .setTradePrice(tradePrice == null ? "" : tradePrice)
                        .setTradeVolume(tradeVolume == null ? "" : tradeVolume)
                        .setTradingPeerNodeAddress(tradingPeerNodeAddress)
                        .setState(state == null ? "" : state)
                        .setPhase(phase == null ? "" : phase)
                        .setTradePeriodState(tradePeriodState == null ? "" : tradePeriodState)
                        .setIsDepositPublished(isDepositPublished)
                        .setIsDepositConfirmed(isDepositConfirmed)
                        .setIsPaymentStartedMessageSent(isPaymentStartedMessageSent)
                        .setIsPaymentReceivedMessageSent(isPaymentReceivedMessageSent)
                        .setIsPayoutPublished(isPayoutPublished)
                        .setIsCompleted(isCompleted)
                        .setIsTakerApiUser(isTakerApiUser)
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
                .withIsPaymentStartedMessageSent(proto.getIsPaymentStartedMessageSent())
                .withIsPaymentReceivedMessageSent(proto.getIsPaymentReceivedMessageSent())
                .withIsPayoutPublished(proto.getIsPayoutPublished())
                .withIsCompleted(proto.getIsCompleted())
                .withContractAsJson(proto.getContractAsJson())
                .withContract((ContractInfo.fromProto(proto.getContract())))
                .withIsTakerApiUser(proto.getIsTakerApiUser())
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
                ", isPaymentStartedMessageSent=" + isPaymentStartedMessageSent + "\n" +
                ", isPaymentReceivedMessageSent=" + isPaymentReceivedMessageSent + "\n" +
                ", isPayoutPublished=" + isPayoutPublished + "\n" +
                ", isCompleted=" + isCompleted + "\n" +
                ", offer=" + offer + "\n" +
                ", contractAsJson=" + contractAsJson + "\n" +
                ", contract=" + contract + "\n" +
                ", bsqSwapTradeInfo=" + bsqSwapTradeInfo + "\n" +
                ", isTakerApiUser=" + isTakerApiUser + "\n" +
                ", closingStatus=" + closingStatus + "\n" +
                '}';
    }
}
