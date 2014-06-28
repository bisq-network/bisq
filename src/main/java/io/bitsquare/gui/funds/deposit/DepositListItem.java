package io.bitsquare.gui.funds.deposit;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.funds.withdrawal.WithdrawalListItem;

public class DepositListItem extends WithdrawalListItem
{
    public DepositListItem(AddressEntry addressEntry, WalletFacade walletFacade)
    {
        super(addressEntry, walletFacade);
    }

}
