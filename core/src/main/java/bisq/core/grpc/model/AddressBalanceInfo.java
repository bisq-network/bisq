package bisq.core.grpc.model;

import bisq.common.Payload;

public class AddressBalanceInfo implements Payload {

    private final String address;
    private final long balance;             // address' balance in satoshis
    private final long numConfirmations;    // # confirmations for address' most recent tx

    public AddressBalanceInfo(String address, long balance, long numConfirmations) {
        this.address = address;
        this.balance = balance;
        this.numConfirmations = numConfirmations;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.AddressBalanceInfo toProtoMessage() {
        return bisq.proto.grpc.AddressBalanceInfo.newBuilder()
                .setAddress(address)
                .setBalance(balance)
                .setNumConfirmations(numConfirmations).build();
    }

    public static AddressBalanceInfo fromProto(bisq.proto.grpc.AddressBalanceInfo proto) {
        return new AddressBalanceInfo(proto.getAddress(),
                proto.getBalance(),
                proto.getNumConfirmations());
    }

    @Override
    public String toString() {
        return "AddressBalanceInfo{" +
                "address='" + address + '\'' +
                ", balance=" + balance +
                ", numConfirmations=" + numConfirmations +
                '}';
    }
}
