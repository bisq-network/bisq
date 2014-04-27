package io.bitsquare.bank;

import java.io.Serializable;

public class BankAccount implements Serializable
{

    private static final long serialVersionUID = 1792577576443221268L;

    public BankAccountType bankAccountType;
    public String accountPrimaryID;
    public String accountSecondaryID;
    public String accountHolderName;
    private String uid;

    // TODO just for mock yet
    public BankAccount(BankAccountType bankAccountType)
    {
        this.bankAccountType = bankAccountType;
    }

    public BankAccount(BankAccountType bankAccountType, String accountPrimaryID, String accountSecondaryID, String accountHolderName)
    {
        this.bankAccountType = bankAccountType;
        this.accountPrimaryID = accountPrimaryID;
        this.accountSecondaryID = accountSecondaryID;
        this.accountHolderName = accountHolderName;

        uid = bankAccountType + "_" + accountPrimaryID + "_" + accountSecondaryID + "_" + accountHolderName;
    }

    public String getAccountPrimaryID()
    {
        return accountPrimaryID;
    }

    public String getAccountSecondaryID()
    {
        return accountSecondaryID;
    }

    public String getAccountHolderName()
    {
        return accountHolderName;
    }

    public BankAccountType getBankAccountType()
    {
        return bankAccountType;
    }

    public String getUid()
    {
        return uid;
    }

    @Override
    public String toString()
    {
        return "BankAccount{" +
                "bankAccountType=" + bankAccountType +
                ", accountPrimaryID='" + accountPrimaryID + '\'' +
                ", accountSecondaryID='" + accountSecondaryID + '\'' +
                ", accountHolderName='" + accountHolderName + '\'' +
                '}';
    }
}
