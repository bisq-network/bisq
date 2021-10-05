package bisq.core.trade.model;

import bisq.core.trade.protocol.ProtocolModel;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.protocol.TradePeer;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.Model;

public abstract class TradeModel implements Tradable, Model {
    public abstract ProtocolModel<? extends TradePeer> getTradeProtocolModel();

    public abstract NodeAddress getTradingPeerNodeAddress();

    public abstract void setErrorMessage(String errorMessage);

    // TODO(sq): used for tradeModel.stateProperty().get()
    public abstract String getStateInfo();

    public abstract String getUid();

    public abstract boolean isCompleted();

    public abstract void initialize(Provider provider);
}
