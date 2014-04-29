package io.bitsquare.user;

import io.bitsquare.bank.BankAccount;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class User implements Serializable
{
    private static final long serialVersionUID = 7409078808248518638L;

    private String accountID;
    private String messageID;
    private boolean online;
    private List<BankAccount> bankAccounts = new ArrayList<>();
    private BankAccount currentBankAccount = null;
    private List<Locale> languageLocales = new ArrayList<>();
    private Locale currentLanguageLocale = null;

    public User()
    {
        addLanguageLocales(Locale.getDefault());
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
            languageLocales = savedUser.getLanguageLocales();
            currentLanguageLocale = savedUser.getCurrentLanguageLocale();
        }
    }

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

    public void addLanguageLocales(Locale locale)
    {
        languageLocales.add(locale);
        currentLanguageLocale = locale;
    }

    public void addBankAccount(BankAccount bankAccount)
    {

        bankAccounts.add(bankAccount);
        currentBankAccount = bankAccount;
    }

    // setter
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
    }

    public void setLanguageLocales(List<Locale> languageLocales)
    {
        this.languageLocales = languageLocales;
    }

    public void setCurrentLanguageLocale(Locale currentLanguageLocale)
    {
        this.currentLanguageLocale = currentLanguageLocale;
    }

    public void setOnline(boolean online)
    {
        this.online = online;
    }


    // getter
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

    public List<Locale> getLanguageLocales()
    {
        return languageLocales;
    }

    public Locale getCurrentLanguageLocale()
    {
        return currentLanguageLocale;
    }

    public boolean isOnline()
    {
        return online;
    }


}
