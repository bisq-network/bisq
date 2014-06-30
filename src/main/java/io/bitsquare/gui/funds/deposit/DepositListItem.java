package io.bitsquare.gui.funds.deposit;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.funds.withdrawal.WithdrawalListItem;
import org.jetbrains.annotations.NotNull;

public class DepositListItem extends WithdrawalListItem
{
    public DepositListItem(@NotNull AddressEntry addressEntry, @NotNull WalletFacade walletFacade)
    {
        super(addressEntry, walletFacade);
    }

}
