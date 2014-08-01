package io.bitsquare.user;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.util.DSAKeyUtil;
import java.io.Serializable;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleBooleanProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The User is persisted locally it is never transmitted over the wire (messageKeyPair contains private key!).
 */
public class User implements Serializable
{
    private static final Logger log = LoggerFactory.getLogger(User.class);
    private static final long serialVersionUID = 7409078808248518638L;

    transient private final SimpleBooleanProperty bankAccountChangedProperty = new SimpleBooleanProperty();

    private KeyPair messageKeyPair;
    private String accountID;
    private List<BankAccount> bankAccounts;
    private BankAccount currentBankAccount;

    public User()
    {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void applyPersistedUser(User persistedUser)
    {
        if (persistedUser != null)
        {
            accountID = persistedUser.getAccountId();
            bankAccounts = persistedUser.getBankAccounts();
            setCurrentBankAccount(persistedUser.getCurrentBankAccount());
            messageKeyPair = persistedUser.getMessageKeyPair();
        }
        else
        {
            // First time
            bankAccounts = new ArrayList<>();
            messageKeyPair = DSAKeyUtil.generateKeyPair();  // DSAKeyUtil.getKeyPair() runs in same thread now
        }
        DSAKeyUtil.generateKeyPair();
    }

    public void addBankAccount(BankAccount bankAccount)
    {
        if (!bankAccounts.contains(bankAccount)) bankAccounts.add(bankAccount);

        setCurrentBankAccount(bankAccount);
    }

    public void removeCurrentBankAccount()
    {
        if (currentBankAccount != null) bankAccounts.remove(currentBankAccount);

        if (bankAccounts.isEmpty()) currentBankAccount = null;
        else setCurrentBankAccount(bankAccounts.get(0));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Will be written after registration.
    // Public key from the input for the registration payment tx (or address) will be used
    public void setAccountID(String accountID)
    {
        this.accountID = accountID;
    }

    public void setCurrentBankAccount(BankAccount bankAccount)
    {
        this.currentBankAccount = bankAccount;
        bankAccountChangedProperty.set(!bankAccountChangedProperty.get());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
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

    public List<BankAccount> getBankAccounts()
    {
        return bankAccounts;
    }

    public BankAccount getCurrentBankAccount()
    {
        return currentBankAccount;
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

    public SimpleBooleanProperty getBankAccountChangedProperty()
    {
        return bankAccountChangedProperty;
    }

    public KeyPair getMessageKeyPair()
    {
        return messageKeyPair;
    }

    public PublicKey getMessagePublicKey()
    {
        return messageKeyPair.getPublic();
    }

    @Override
    public String toString()
    {
        return "User{" +
                "bankAccountChangedProperty=" + bankAccountChangedProperty +
                ", messageKeyPair=" + messageKeyPair +
                ", accountID='" + accountID + '\'' +
                ", bankAccounts=" + bankAccounts +
                ", currentBankAccount=" + currentBankAccount +
                '}';
    }
}
