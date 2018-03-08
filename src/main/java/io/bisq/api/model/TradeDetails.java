package io.bisq.api.model;

import io.bisq.core.offer.Offer;
import io.bisq.core.trade.Trade;
import io.bisq.network.p2p.NodeAddress;

public class TradeDetails {

    public String id;
    public String offerId;
    public boolean isCurrencyForTakerFeeBtc;
    public long txFee;
    public long takerFee;
    public long takeOfferDate;
    public String takerFeeTxId;
    public String depositTxId;
    public String payoutTxId;
    public long tradeAmount;
    public long tradePrice;
    public Trade.State state;
    public Trade.DisputeState disputeState;
    public Trade.TradePeriodState tradePeriodState;
    public byte[] arbitratorBtcPubKey;
    public byte[] contractHash;
    public String mediatorNodeAddress;
    public String takerContractSignature;
    public String makerContractSignature;
    public String arbitratorNodeAddress;
    public String tradingPeerNodeAddress;
    public String takerPaymentAccountId;
    public String errorMessage;
    public String counterCurrencyTxId;

    public TradeDetails() {
    }

    public TradeDetails(Trade trade) {
        this.id = trade.getId();
        final Offer offer = trade.getOffer();
        if (null != offer)
            this.offerId = offer.getId();
        this.isCurrencyForTakerFeeBtc = trade.isCurrencyForTakerFeeBtc();
        this.txFee = trade.getTxFeeAsLong();
        this.takerFee = trade.getTakerFeeAsLong();
        this.takeOfferDate = trade.getTakeOfferDate().getTime();
        this.takerFeeTxId = trade.getTakerFeeTxId();
        this.depositTxId = trade.getDepositTxId();
        this.payoutTxId = trade.getPayoutTxId();
        this.tradeAmount = trade.getTradeAmountAsLong();
        this.tradePrice = trade.getTradePrice().getValue();
        this.state = trade.getState();
        this.disputeState = trade.getDisputeState();
        this.tradePeriodState = trade.getTradePeriodState();
        this.arbitratorBtcPubKey = trade.getArbitratorBtcPubKey();
        this.contractHash = trade.getContractHash();
        final NodeAddress mediatorNodeAddress = trade.getMediatorNodeAddress();
        if (null != mediatorNodeAddress)
            this.mediatorNodeAddress = mediatorNodeAddress.getFullAddress();
        this.takerContractSignature = trade.getTakerContractSignature();
        this.makerContractSignature = trade.getMakerContractSignature();
        final NodeAddress arbitratorNodeAddress = trade.getArbitratorNodeAddress();
        if (null != arbitratorNodeAddress)
            this.arbitratorNodeAddress = arbitratorNodeAddress.getFullAddress();
        final NodeAddress tradingPeerNodeAddress = trade.getTradingPeerNodeAddress();
        if (null != tradingPeerNodeAddress)
            this.tradingPeerNodeAddress = tradingPeerNodeAddress.getFullAddress();
        this.takerPaymentAccountId = trade.getTakerPaymentAccountId();
        this.errorMessage = trade.getErrorMessage();
        this.counterCurrencyTxId = trade.getCounterCurrencyTxId();
    }

}
