package io.bitsquare.user;

import io.bitsquare.bank.BankAccount;
import javafx.beans.property.SimpleBooleanProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
        bankAccounts.add(bankAccount);
        currentBankAccount = bankAccount;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setMessagePubKeyAsHex(String messageID)
    {
        this.messagePubKeyAsHex = messageID;
    }

    public void setAccountID(String accountID)
    {
        this.accountID = accountID;
    }

    public void setBankAccounts(List<BankAccount> bankAccounts)
    {
        this.bankAccounts = bankAccounts;
    }

    public void setCurrentBankAccount(BankAccount currentBankAccount)
    {
        this.currentBankAccount = currentBankAccount;
        bankAccountChangedProperty.set(!bankAccountChangedProperty.get());
    }

    public void setIsOnline(boolean isOnline)
    {
        this.isOnline = isOnline;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getStringifiedBankAccounts()
    {
        String bankAccountUIDs = "";
        for (Iterator<BankAccount> iterator = getBankAccounts().iterator(); iterator.hasNext(); )
        {
            BankAccount bankAccount = iterator.next();
            bankAccountUIDs += bankAccount.toString();

            if (iterator.hasNext())
                bankAccountUIDs += ", ";
        }
        return bankAccountUIDs;
    }

    public String getMessagePubKeyAsHex()
    {
        return messagePubKeyAsHex;
    }

    public String getAccountID()
    {
        return accountID;
    }

    public List<BankAccount> getBankAccounts()
    {
        return bankAccounts;
    }

    public BankAccount getCurrentBankAccount()
    {
        return currentBankAccount;
    }

    public BankAccount getBankAccount(String bankAccountUID)
    {
        for (Iterator<BankAccount> iterator = bankAccounts.iterator(); iterator.hasNext(); )
        {
            BankAccount bankAccount = iterator.next();
            if (bankAccount.getUid().equals(bankAccountUID))
                return bankAccount;
        }
        return null;
    }

    public boolean getIsOnline()
    {
        return isOnline;
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
