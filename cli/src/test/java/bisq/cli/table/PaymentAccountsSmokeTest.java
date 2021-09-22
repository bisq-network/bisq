package bisq.cli.table;

import static bisq.cli.table.builder.TableType.PAYMENT_ACCOUNT_TBL;
import static java.lang.System.out;



import bisq.cli.table.builder.TableBuilder;

@SuppressWarnings("unused")
public class PaymentAccountsSmokeTest extends AbstractSmokeTest {

    public static void main(String[] args) {
        PaymentAccountsSmokeTest test = new PaymentAccountsSmokeTest();
        test.getPaymentAccounts();
    }

    public PaymentAccountsSmokeTest() {
        super();
    }

    private void getPaymentAccounts() {
        var paymentAccounts = aliceClient.getPaymentAccounts();
        if (paymentAccounts.size() > 0) {
            // var oldTbl = TODO
            var newTbl = new TableBuilder(PAYMENT_ACCOUNT_TBL, paymentAccounts).build().toString();
            // printOldTbl(oldTbl);
            printNewTbl(newTbl);
            // showDiffsIgnoreWhitespace(oldTbl, newTbl);
        } else {
            out.println("no payment accounts are saved");
        }
    }

}
