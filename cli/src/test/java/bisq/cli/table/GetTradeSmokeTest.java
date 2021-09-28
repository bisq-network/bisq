package bisq.cli.table;

import static bisq.cli.table.builder.TableType.TRADE_DETAIL_TBL;
import static java.lang.System.out;



import bisq.cli.GrpcClient;
import bisq.cli.table.builder.TableBuilder;

@SuppressWarnings("unused")
public class GetTradeSmokeTest extends AbstractSmokeTest {

    public static void main(String[] args) {
        if (args.length == 0)
            throw new IllegalStateException("Need a single trade-id program argument.");

        GetTradeSmokeTest test = new GetTradeSmokeTest(args[0]);
        test.getAlicesTrade();
        out.println();
        test.getBobsTrade();
    }

    private final String tradeId;

    public GetTradeSmokeTest(String tradeId) {
        super();
        this.tradeId = tradeId;
    }

    private void getAlicesTrade() {
        getTrade(aliceClient);
    }

    private void getBobsTrade() {
        getTrade(bobClient);
    }

    private void getTrade(GrpcClient client) {
        var trade = client.getTrade(tradeId);
        // var oldTbl = TODO
        var newTbl = new TableBuilder(TRADE_DETAIL_TBL, trade).build().toString();
        // printOldTbl(oldTbl);
        printNewTbl(newTbl);
        // showDiffsIgnoreWhitespace(oldTbl, newTbl);
    }
}
