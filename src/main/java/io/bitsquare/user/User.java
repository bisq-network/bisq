package io.bitsquare.user;

import io.bitsquare.bank.BankAccount;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleBooleanProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class User implements Serializable
{
    private static final long serialVersionUID = 7409078808248518638L;

    transient private final SimpleBooleanProperty bankAccountChangedProperty = new SimpleBooleanProperty();

    @Nullable
    private String accountID;
    @Nullable
    private String messagePubKeyAsHex;
    private boolean isOnline;
    @NotNull
    private List<BankAccount> bankAccounts = new ArrayList<>();
    @Nullable
    private BankAccount currentBankAccount = null;

    public User()
    {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void updateFromStorage(@Nullable User savedUser)
    {
        if (savedUser != null)
        {
            accountID = savedUser.getAccountID();
            messagePubKeyAsHex = savedUser.getMessagePubKeyAsHex();
            isOnline = savedUser.getIsOnline();
            bankAccounts = savedUser.getBankAccounts();
            currentBankAccount = savedUser.getCurrentBankAccount();
        }
    }

    public void addBankAccount(@NotNull BankAccount bankAccount)
    {
        if (!bankAccounts.contains(bankAccount))
            bankAccounts.add(bankAccount);

        currentBankAccount = bankAccount;
    }

    public void removeCurrentBankAccount()
    {
        if (currentBankAccount != null) bankAccounts.remove(currentBankAccount);

        if (bankAccounts.isEmpty()) currentBankAccount = null;
        else currentBankAccount = bankAccounts.get(0);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @NotNull
    public String getStringifiedBankAccounts()
    {
        @NotNull String bankAccountUIDs = "";
        for (int i = 0; i < bankAccounts.size(); i++)
        {
            BankAccount bankAccount = bankAccounts.get(i);
            bankAccountUIDs += bankAccount.toString();

            if (i < bankAccounts.size() - 1)
                bankAccountUIDs += ", ";

        }
        return bankAccountUIDs;
    }

    @Nullable
    public String getMessagePubKeyAsHex()
    {
        return messagePubKeyAsHex;
    }

    public void setMessagePubKeyAsHex(@Nullable String messageID)
    {
        this.messagePubKeyAsHex = messageID;
    }

    @Nullable
    public String getAccountID()
    {
        return accountID;
    }

    public void setAccountID(@Nullable String accountID)
    {
        this.accountID = accountID;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @NotNull
    public List<BankAccount> getBankAccounts()
    {
        return bankAccounts;
    }

    public void setBankAccounts(@NotNull List<BankAccount> bankAccounts)
    {
        this.bankAccounts = bankAccounts;
    }

    @Nullable
    public BankAccount getCurrentBankAccount()
    {
        return currentBankAccount;
    }

    public void setCurrentBankAccount(@NotNull BankAccount bankAccount)
    {
        this.currentBankAccount = bankAccount;
        bankAccountChangedProperty.set(!bankAccountChangedProperty.get());
    }

    @Nullable
    public BankAccount getBankAccount(@NotNull String bankAccountId)
    {
        for (final BankAccount bankAccount : bankAccounts)
        {
            if (bankAccount.getUid().equals(bankAccountId))
                return bankAccount;
        }
        return null;
    }

    boolean getIsOnline()
    {
        return isOnline;
    }

    public void setIsOnline(boolean isOnline)
    {
        this.isOnline = isOnline;
    }

    @NotNull
    public SimpleBooleanProperty getBankAccountChangedProperty()
    {
        return bankAccountChangedProperty;
    }

    @NotNull
    @Override
    public String toString()
    {
        return "User{" +
                "bankAccountChangedProperty=" + bankAccountChangedProperty +
                ", accountID='" + accountID + '\'' +
                ", messagePubKeyAsHex='" + messagePubKeyAsHex + '\'' +
                ", isOnline=" + isOnline +
                ", bankAccounts=" + bankAccounts +
                ", currentBankAccount=" + currentBankAccount +
                '}';
    }
}
