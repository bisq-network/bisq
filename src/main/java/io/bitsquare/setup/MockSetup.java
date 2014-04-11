package io.bitsquare.setup;

import com.google.inject.Inject;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.IStorage;
import io.bitsquare.trade.Direction;
import io.bitsquare.trade.orderbook.OrderBookFilter;
import io.bitsquare.user.BankDetails;
import io.bitsquare.user.User;

import java.util.UUID;

public class MockSetup implements ISetup
{
    private IStorage storage;
    private User user;
    private OrderBookFilter orderBookFilter;

    @Inject
    public MockSetup(IStorage storage, User user, OrderBookFilter orderBookFilter)
    {
        this.storage = storage;
        this.user = user;
        this.orderBookFilter = orderBookFilter;
    }

    @Override
    public void applyPersistedData()
    {
        String accountID = (String) storage.read("User.accountID");
        if (accountID == null)
        {
            storage.write("User.accountID", UUID.randomUUID().toString());
            storage.write("User.messageID", UUID.randomUUID().toString());
            storage.write("User.country", "ES");

            storage.write("BankDetails.bankTransferType", "SEPA");
            storage.write("BankDetails.accountPrimaryID", "IBAN_12312");
            storage.write("BankDetails.accountSecondaryID", "BIC_123123");
            storage.write("BankDetails.accountHolderName", "Bob Brown");
        }

        user.setAccountID((String) storage.read("User.accountID"));
        user.setMessageID((String) storage.read("User.messageID"));
        user.setCountry((String) storage.read("User.country"));
        user.setBankDetails(new BankDetails((String) storage.read("BankDetails.bankTransferType"),
                (String) storage.read("BankDetails.accountPrimaryID"),
                (String) storage.read("BankDetails.accountSecondaryID"),
                (String) storage.read("BankDetails.accountHolderName")));

        // todo use persistence
        orderBookFilter.setAmount(0.0);
        orderBookFilter.setPrice(0.0);
        orderBookFilter.setDirection(Direction.BUY);
        orderBookFilter.setCurrency(Settings.getCurrency());


    }
}
