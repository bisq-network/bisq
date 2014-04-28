package io.bitsquare.user;

import io.bitsquare.bank.BankAccount;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class User implements Serializable
{
    private static final long serialVersionUID = 7409078808248518638L;

    private String accountID;
    private String messageID;
    private boolean online;
    private BankAccount currentBankAccount = null;

    private Map<String, BankAccount> bankAccounts = new HashMap<>();
    private String country;

    public User()
    {
    }

    public void updateFromStorage(User savedUser)
    {
        if (savedUser != null)
        {
            accountID = savedUser.getAccountID();
            messageID = savedUser.getMessageID();
            online = savedUser.isOnline();
            currentBankAccount = savedUser.getCurrentBankAccount();
            bankAccounts = savedUser.getBankAccounts();
            country = savedUser.getCountry();
        }
    }

    public String getStringifiedBankAccounts()
    {
        String bankAccountUIDs = "";
        for (Iterator<Map.Entry<String, BankAccount>> iterator = getBankAccounts().entrySet().iterator(); iterator.hasNext(); )
        {
            Map.Entry<String, BankAccount> entry = iterator.next();
            bankAccountUIDs += entry.getValue().getStringifiedBankAccount();

            if (iterator.hasNext())
                bankAccountUIDs += ", ";
        }
        return bankAccountUIDs;
    }

    public void addBankAccount(BankAccount bankAccount)
    {
        if (currentBankAccount == null)
            currentBankAccount = bankAccount;

        bankAccounts.put(bankAccount.getUid(), bankAccount);
    }

    public Map<String, BankAccount> getBankAccounts()
    {
        return bankAccounts;
    }

    public BankAccount getBankAccountByUID(String uid)
    {
        return bankAccounts.get(uid);
    }

    public String getMessageID()
    {
        return messageID;
    }

    public void setMessageID(String messageID)
    {
        this.messageID = messageID;
    }

    public String getAccountID()
    {
        return accountID;
    }

    public void setAccountID(String accountID)
    {
        this.accountID = accountID;
    }

    public void setCountry(String country)
    {
        this.country = country;
    }

    public String getCountry()
    {
        return country;
    }

    public BankAccount getCurrentBankAccount()
    {
        return currentBankAccount;
    }

    public boolean isOnline()
    {
        return online;
    }


}
