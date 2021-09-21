package bisq.cli.table;

import static bisq.cli.table.builder.TableType.TRANSACTION_TBL;



import bisq.cli.TransactionFormat;
import bisq.cli.table.builder.TableBuilder;

@SuppressWarnings("unused")
public class GetTransactionSmokeTest extends AbstractSmokeTest {

    public static void main(String[] args) {
        if (args.length == 0)
            throw new IllegalStateException("Need a single transaction-id program argument.");

        GetTransactionSmokeTest test = new GetTransactionSmokeTest(args[0]);
        test.getTransaction();
    }

    private final String transactionId;

    public GetTransactionSmokeTest(String transactionId) {
        super();
        this.transactionId = transactionId;
    }

    private void getTransaction() {
        var tx = aliceClient.getTransaction(transactionId);
        var oldTbl = TransactionFormat.format(tx);
        var newTbl = new TableBuilder(TRANSACTION_TBL, tx).build().toString();
        printOldTbl(oldTbl);
        printNewTbl(newTbl);
        // Should show 1 diff due to new 'Is Confirmed' column being left justified (fixed).
        showDiffsIgnoreWhitespace(oldTbl, newTbl);
    }
}
