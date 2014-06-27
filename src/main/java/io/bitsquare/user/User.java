package io.bitsquare.user;

import io.bitsquare.bank.BankAccount;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleBooleanProperty;

public class User implements Serializable
{
    private static final long serialVersionUID = 7409078808248518638L;

    transient private final SimpleBooleanProperty bankAccountChangedProperty = new SimpleBooleanProperty();

    private String accountID;
    private String messagePubKeyAsHex;
    private boolean isOnline;
    private List<BankAccount> bankAccounts = new ArrayList<>();
    private BankAccount currentBankAccount = null;

    public User()
    {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void updateFromStorage(User savedUser)
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

    public void addBankAccount(BankAccount bankAccount)
    {
        if (!bankAccounts.contains(bankAccount))
            bankAccounts.add(bankAccount);

        currentBankAccount = bankAccount;
    }

    public void removeCurrentBankAccount()
    {
        if (currentBankAccount != null)
            bankAccounts.remove(currentBankAccount);

        if (bankAccounts.size() > 0)
            currentBankAccount = bankAccounts.get(0);
        else
            currentBankAccount = null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getStringifiedBankAccounts()
    {
        String bankAccountUIDs = "";
        for (int i = 0; i < bankAccounts.size(); i++)
        {
            BankAccount bankAccount = bankAccounts.get(i);
            bankAccountUIDs += bankAccount.toString();

            if (i < bankAccounts.size() - 1)
                bankAccountUIDs += ", ";

        }
        return bankAccountUIDs;
    }

    public String getMessagePubKeyAsHex()
    {
        return messagePubKeyAsHex;
    }

    public void setMessagePubKeyAsHex(String messageID)
    {
        this.messagePubKeyAsHex = messageID;
    }

    public String getAccountID()
    {
        return accountID;
    }

    public void setAccountID(String accountID)
    {
        this.accountID = accountID;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<BankAccount> getBankAccounts()
    {
        return bankAccounts;
    }

    public void setBankAccounts(List<BankAccount> bankAccounts)
    {
        this.bankAccounts = bankAccounts;
    }

    public BankAccount getCurrentBankAccount()
    {
        return currentBankAccount;
    }

    public void setCurrentBankAccount(BankAccount currentBankAccount)
    {
        this.currentBankAccount = currentBankAccount;
        bankAccountChangedProperty.set(!bankAccountChangedProperty.get());
    }

    public BankAccount getBankAccount(String bankAccountUID)
    {
        for (BankAccount bankAccount : bankAccounts)
        {
            if (bankAccount.getUid().equals(bankAccountUID))
                return bankAccount;
        }
        return null;
    }

    public boolean getIsOnline()
    {
        return isOnline;
    }

    public void setIsOnline(boolean isOnline)
    {
        this.isOnline = isOnline;
    }

    public SimpleBooleanProperty getBankAccountChangedProperty()
    {
        return bankAccountChangedProperty;
    }

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
