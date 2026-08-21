package bisq.cli.table;

import lombok.extern.slf4j.Slf4j;

import static bisq.cli.table.builder.TableType.TRADE_DETAIL_TBL;
import static java.lang.System.out;



import bisq.cli.AbstractCliTest;
import bisq.cli.GrpcClient;
import bisq.cli.table.builder.TableBuilder;

@SuppressWarnings("unused")
@Slf4j
public class GetTradeCliOutputDiffTest extends AbstractCliTest {

    public static void main(String[] args) {
        if (args.length == 0)
            throw new IllegalStateException("Need a single trade-id program argument.");

        GetTradeCliOutputDiffTest test = new GetTradeCliOutputDiffTest(args[0]);
        test.getAlicesTrade();
        out.println();
        test.getBobsTrade();
    }

    private final String tradeId;

    public GetTradeCliOutputDiffTest(String tradeId) {
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
        // TradeFormat class had been deprecated, then deleted on 17-Feb-2022, but these
        // diff tests can be useful for testing changes to the current tbl formatting api.
        // var oldTbl = TradeFormat.format(trade);
        var newTbl = new TableBuilder(TRADE_DETAIL_TBL, trade).build().toString();
        // printOldTbl(oldTbl);
        printNewTbl(newTbl);
        // checkDiffsIgnoreWhitespace(oldTbl, newTbl);
    }
}
