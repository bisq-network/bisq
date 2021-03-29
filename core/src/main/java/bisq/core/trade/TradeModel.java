package bisq.core.trade;

import bisq.core.trade.protocol.ProcessModel;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.Model;

public abstract class TradeModel implements Tradable, Model {
    public abstract ProcessModel getProcessModel();

    public abstract NodeAddress getTradingPeerNodeAddress();

    public abstract void setErrorMessage(String errorMessage);

    // TODO(sq): used for tradeModel.stateProperty().get()
    public abstract String getStateInfo();
}
