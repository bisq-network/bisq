package bisq.core.transaction;

import bisq.common.Payload;

public class Transaction implements Payload {
    private final String txId;
    private final String memo;

    public Transaction(String txId, String memo) {
        this.txId = txId;
        this.memo = memo;
    }

    @Override
    public protobuf.Transaction toProtoMessage() {
        final protobuf.Transaction.Builder builder = protobuf.Transaction.newBuilder()
                .setMemo(memo)
                .setTxId(txId);
        return builder.build();
    }

    public static Transaction fromProto(protobuf.Transaction protobufTransaction) {
        return new Transaction(protobufTransaction.getTxId(), protobufTransaction.getMemo());
    }

    public String getTxId() { return txId; }

    public String getMemo() { return memo; }
}
