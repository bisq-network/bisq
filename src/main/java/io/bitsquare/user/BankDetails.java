package io.bitsquare.user;

public class BankDetails
{
    private String bankTransferType;
    private String accountPrimaryID;
    private String accountSecondaryID;
    private String accountHolderName;

    public BankDetails(String bankTransferType, String accountPrimaryID, String accountSecondaryID, String accountHolderName)
    {
        this.bankTransferType = bankTransferType;
        this.accountPrimaryID = accountPrimaryID;
        this.accountSecondaryID = accountSecondaryID;
        this.accountHolderName = accountHolderName;
    }

    public BankDetails()
    {
    }

    public void setBankTransferType(String bankTransferType)
    {
        this.bankTransferType = bankTransferType;
    }

    public String getAccountPrimaryID()
    {
        return accountPrimaryID;
    }

    public void setAccountPrimaryID(String accountPrimaryID)
    {
        this.accountPrimaryID = accountPrimaryID;
    }

    public String getAccountSecondaryID()
    {
        return accountSecondaryID;
    }

    public void setAccountSecondaryID(String accountSecondaryID)
    {
        this.accountSecondaryID = accountSecondaryID;
    }

    public String getAccountHolderName()
    {
        return accountHolderName;
    }

    public void setAccountHolderName(String accountHolderName)
    {
        this.accountHolderName = accountHolderName;
    }

    public String getBankTransferType()
    {
        return bankTransferType;
    }
}
