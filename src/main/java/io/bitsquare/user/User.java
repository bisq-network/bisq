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

    transient private final SimpleBooleanProperty changedProperty = new SimpleBooleanProperty();

    private String accountID;
    private String messageID;
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
            messageID = savedUser.getMessageID();
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

    public void setMessageID(String messageID)
    {
        this.messageID = messageID;
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
        triggerChange();
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
            bankAccountUIDs += bankAccount.getStringifiedBankAccount();

            if (iterator.hasNext())
                bankAccountUIDs += ", ";
        }
        return bankAccountUIDs;
    }

    public String getMessageID()
    {
        return messageID;
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

    public boolean getIsOnline()
    {
        return isOnline;
    }

    public SimpleBooleanProperty getChangedProperty()
    {
        return changedProperty;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void triggerChange()
    {
        changedProperty.set(!changedProperty.get());
    }
}
