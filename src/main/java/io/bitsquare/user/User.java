package io.bitsquare.user;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.util.DSAKeyUtil;
import java.io.Serializable;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleBooleanProperty;

public class User implements Serializable
{
    private static final long serialVersionUID = 7409078808248518638L;

    transient private final SimpleBooleanProperty bankAccountChangedProperty = new SimpleBooleanProperty();
    transient private KeyPair messageKeyPair = DSAKeyUtil.getKeyPair();

    private PublicKey messagePublicKey;
    private String accountID;
    private boolean isOnline;
    private List<BankAccount> bankAccounts = new ArrayList<>();
    private BankAccount currentBankAccount = null;

    public User()
    {
        messagePublicKey = messageKeyPair.getPublic();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void updateFromStorage(User savedUser)
    {
        if (savedUser != null)
        {
            accountID = savedUser.getAccountId();
            // TODO handled by DSAKeyUtil -> change that storage check is only done here
            // messagePublicKey = savedUser.getMessagePublicKey();
            isOnline = savedUser.getIsOnline();
            bankAccounts = savedUser.getBankAccounts();
            currentBankAccount = savedUser.getCurrentBankAccount();
        }

        messagePublicKey = messageKeyPair.getPublic();
    }

    public void addBankAccount(BankAccount bankAccount)
    {
        if (!bankAccounts.contains(bankAccount))
        {
            bankAccounts.add(bankAccount);
        }

        currentBankAccount = bankAccount;
    }

    public void removeCurrentBankAccount()
    {
        if (currentBankAccount != null)
        {
            bankAccounts.remove(currentBankAccount);
        }

        if (bankAccounts.isEmpty())
        {
            currentBankAccount = null;
        }
        else
        {
            currentBankAccount = bankAccounts.get(0);
        }
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
            {
                bankAccountUIDs += ", ";
            }

        }
        return bankAccountUIDs;
    }


    public String getAccountId()
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

    public void setCurrentBankAccount(BankAccount bankAccount)
    {
        this.currentBankAccount = bankAccount;
        bankAccountChangedProperty.set(!bankAccountChangedProperty.get());
    }


    public BankAccount getBankAccount(String bankAccountId)
    {
        for (final BankAccount bankAccount : bankAccounts)
        {
            if (bankAccount.getUid().equals(bankAccountId))
            {
                return bankAccount;
            }
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


    public SimpleBooleanProperty getBankAccountChangedProperty()
    {
        return bankAccountChangedProperty;
    }

    @Override
    public String toString()
    {
        return "User{" +
                "bankAccountChangedProperty=" + bankAccountChangedProperty +
                ", messageKeyPair=" + messageKeyPair +
                ", messagePublicKey=" + messagePublicKey +
                ", accountID='" + accountID + '\'' +
                ", isOnline=" + isOnline +
                ", bankAccounts=" + bankAccounts +
                ", currentBankAccount=" + currentBankAccount +
                '}';
    }

    public KeyPair getMessageKeyPair()
    {
        return messageKeyPair;
    }

    public PublicKey getMessagePublicKey()
    {
        return messagePublicKey;
    }
}
