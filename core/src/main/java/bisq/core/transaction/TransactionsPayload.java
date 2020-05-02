package bisq.core.transaction;

import bisq.common.proto.persistable.PersistableEnvelope;

import com.google.protobuf.Message;

import javax.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@Singleton
public class TransactionsPayload implements PersistableEnvelope {
    private final Map<String, Transaction> transactions;

    public TransactionsPayload(Map<String, Transaction> transactions) {
        this.transactions = transactions;
    }

    public Transaction findTransactionById(String txId) {
        @Nullable
        Transaction result = this.transactions.get(txId);

        return result != null ? result : new Transaction(txId, "");
    }

    @Override
    public Message toProtoMessage() {
        protobuf.TransactionsPayload.Builder builder = protobuf.TransactionsPayload.newBuilder();

        Optional.ofNullable(transactions)
                .ifPresent(transactions -> builder.addAllTransactions(
                        transactions
                                .values()
                                .stream()
                                .map(Transaction::toProtoMessage)
                                .collect(Collectors.toList())
                        )
                );

        return protobuf.PersistableEnvelope.newBuilder().setTransactionsPayload(builder).build();
    }

    public void addTransaction(Transaction transaction) {
        this.transactions.put(transaction.getTxId(), transaction);
    }

    public static TransactionsPayload fromProto(protobuf.TransactionsPayload proto) {
        Map<String, Transaction> map = new HashMap<>();
        proto.getTransactionsList().forEach(
                protoTransaction -> map.put(protoTransaction.getTxId(), Transaction.fromProto(protoTransaction))
        );

        return new TransactionsPayload(map);
    }
}
