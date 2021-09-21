package bisq.cli.table;

import static bisq.cli.table.builder.TableType.BSQ_BALANCE_TBL;
import static bisq.cli.table.builder.TableType.BTC_BALANCE_TBL;



import bisq.cli.TableFormat;
import bisq.cli.table.builder.TableBuilder;

@SuppressWarnings("unused")
public class GetBalanceSmokeTest extends AbstractSmokeTest {

    public static void main(String[] args) {
        GetBalanceSmokeTest test = new GetBalanceSmokeTest();
        test.getBtcBalance();
        test.getBsqBalance();
    }

    public GetBalanceSmokeTest() {
        super();
    }

    private void getBtcBalance() {
        var balance = aliceClient.getBtcBalances();
        var oldTbl = TableFormat.formatBtcBalanceInfoTbl(balance);
        var newTbl = new TableBuilder(BTC_BALANCE_TBL, balance).build().toString();
        printOldTbl(oldTbl);
        printNewTbl(newTbl);
        showDiffsIgnoreWhitespace(oldTbl, newTbl);
    }

    private void getBsqBalance() {
        var balance = aliceClient.getBsqBalances();
        var oldTbl = TableFormat.formatBsqBalanceInfoTbl(balance);
        var newTbl = new TableBuilder(BSQ_BALANCE_TBL, balance).build().toString();
        printOldTbl(oldTbl);
        printNewTbl(newTbl);
        showDiffsIgnoreWhitespace(oldTbl, newTbl);
    }
}
