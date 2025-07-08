package bisq.cli.table;

import bisq.proto.grpc.AddressBalanceInfo;

import java.util.List;

import static bisq.cli.table.builder.TableType.ADDRESS_BALANCE_TBL;
import static java.lang.System.err;
import static java.util.Collections.singletonList;



import bisq.cli.AbstractCliTest;
import bisq.cli.table.builder.TableBuilder;

@SuppressWarnings("unused")
public class AddressCliOutputDiffTest extends AbstractCliTest {

    public static void main(String[] args) {
        AddressCliOutputDiffTest test = new AddressCliOutputDiffTest();
        test.getFundingAddresses();
        test.getAddressBalance();
    }

    public AddressCliOutputDiffTest() {
        super();
    }

    private void getFundingAddresses() {
        var fundingAddresses = aliceClient.getFundingAddresses(false);
        if (fundingAddresses.size() > 0) {
            // TableFormat class had been deprecated, then deleted on 17-Feb-2022, but
            // these diff tests can be useful for testing changes to the current tbl formatting api.
            // var oldTbl = TableFormat.formatAddressBalanceTbl(fundingAddresses);
            var newTbl = new TableBuilder(ADDRESS_BALANCE_TBL, fundingAddresses).build().toString();
            // printOldTbl(oldTbl);
            printNewTbl(newTbl);
            // checkDiffsIgnoreWhitespace(oldTbl, newTbl);
        } else {
            err.println("no funding addresses found");
        }
    }

    private void getAddressBalance() {
        List<AddressBalanceInfo> addresses = aliceClient.getFundingAddresses(false);
        int numAddresses = addresses.size();
        // Check output for last 2 addresses.
        for (int i = numAddresses - 2; i < addresses.size(); i++) {
            var addressBalanceInfo = addresses.get(i);
            getAddressBalance(addressBalanceInfo.getAddress());
        }
    }

    private void getAddressBalance(String address) {
        var addressBalance = singletonList(aliceClient.getAddressBalance(address));
        // TableFormat class had been deprecated, then deleted on 17-Feb-2022, but these
        // diff tests can be useful for testing changes to the current tbl formatting api.
        // var oldTbl = TableFormat.formatAddressBalanceTbl(addressBalance);
        var newTbl = new TableBuilder(ADDRESS_BALANCE_TBL, addressBalance).build().toString();
        // printOldTbl(oldTbl);
        printNewTbl(newTbl);
        // checkDiffsIgnoreWhitespace(oldTbl, newTbl);
    }
}
