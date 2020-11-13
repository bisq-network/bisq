package bisq.core.api.model;

import bisq.common.Payload;

import lombok.Getter;

@Getter
public class BalancesInfo implements Payload {

    private final BsqBalanceInfo bsqBalanceInfo;
    private final BtcBalanceInfo btcBalanceInfo;

    public BalancesInfo(BsqBalanceInfo bsqBalanceInfo, BtcBalanceInfo btcBalanceInfo) {
        this.bsqBalanceInfo = bsqBalanceInfo;
        this.btcBalanceInfo = btcBalanceInfo;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.BalancesInfo toProtoMessage() {
        return bisq.proto.grpc.BalancesInfo.newBuilder()
                .setBsqBalanceInfo(bsqBalanceInfo.toProtoMessage())
                .setBtcBalanceInfo(btcBalanceInfo.toProtoMessage())
                .build();
    }

    public static BalancesInfo fromProto(bisq.proto.grpc.BalancesInfo proto) {
        return new BalancesInfo(BsqBalanceInfo.fromProto(proto.getBsqBalanceInfo()),
                BtcBalanceInfo.fromProto(proto.getBtcBalanceInfo()));
    }

    @Override
    public String toString() {
        return "BalancesInfo{" + "\n" +
                "  " + bsqBalanceInfo.toString() + "\n" +
                ", " + btcBalanceInfo.toString() + "\n" +
                '}';
    }
}
