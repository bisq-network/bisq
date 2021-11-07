package bisq.cli.table;

import lombok.extern.slf4j.Slf4j;

import static bisq.cli.TableFormat.formatPaymentAcctTbl;
import static bisq.cli.table.builder.TableType.PAYMENT_ACCOUNT_TBL;



import bisq.cli.AbstractCliTest;
import bisq.cli.table.builder.TableBuilder;

@SuppressWarnings("unused")
@Slf4j
public class PaymentAccountsCliOutputDiffTest extends AbstractCliTest {

    public static void main(String[] args) {
        PaymentAccountsCliOutputDiffTest test = new PaymentAccountsCliOutputDiffTest();
        test.getPaymentAccounts();
    }

    public PaymentAccountsCliOutputDiffTest() {
        super();
    }

    private void getPaymentAccounts() {
        var paymentAccounts = aliceClient.getPaymentAccounts();
        if (paymentAccounts.size() > 0) {
            var oldTbl = formatPaymentAcctTbl(paymentAccounts);
            var newTbl = new TableBuilder(PAYMENT_ACCOUNT_TBL, paymentAccounts).build().toString();
            printOldTbl(oldTbl);
            printNewTbl(newTbl);
            checkDiffsIgnoreWhitespace(oldTbl, newTbl);
        } else {
            log.warn("no payment accounts found");
        }
    }

}
