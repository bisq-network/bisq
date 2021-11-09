package bisq.cli.table;

import lombok.extern.slf4j.Slf4j;

import static bisq.cli.table.builder.TableType.TRANSACTION_TBL;



import bisq.cli.AbstractCliTest;
import bisq.cli.TransactionFormat;
import bisq.cli.table.builder.TableBuilder;

@SuppressWarnings("unused")
@Slf4j
public class GetTransactionCliOutputDiffTest extends AbstractCliTest {

    public static void main(String[] args) {
        if (args.length == 0)
            throw new IllegalStateException("Need a single transaction-id program argument.");

        GetTransactionCliOutputDiffTest test = new GetTransactionCliOutputDiffTest(args[0]);
        test.getTransaction();
    }

    private final String transactionId;

    public GetTransactionCliOutputDiffTest(String transactionId) {
        super();
        this.transactionId = transactionId;
    }

    private void getTransaction() {
        var tx = aliceClient.getTransaction(transactionId);
        var oldTbl = TransactionFormat.format(tx);
        var newTbl = new TableBuilder(TRANSACTION_TBL, tx).build().toString();
        printOldTbl(oldTbl);
        printNewTbl(newTbl);
        // Should show 1 (OK) diff due to new 'Is Confirmed' column being left justified (fixed).
        checkDiffsIgnoreWhitespace(oldTbl, newTbl);
    }
}
