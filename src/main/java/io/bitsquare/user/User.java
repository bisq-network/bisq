package io.bitsquare.user;

public class User
{
    private String accountID;
    private String messageID;
    private boolean isOnline;
    private BankDetails bankDetails;
    private String country;

    public User(String accountID, String messageID, String country, BankDetails bankDetails)
    {
        this.accountID = accountID;
        this.messageID = messageID;
        this.country = country;
        this.bankDetails = bankDetails;
    }

    public User()
    {
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


    public BankDetails getBankDetails()
    {
        return bankDetails;
    }

    public void setBankDetails(BankDetails bankDetails)
    {
        this.bankDetails = bankDetails;
    }


    public boolean getOnline()
    {
        return isOnline;
    }

    public void setOnline(boolean online)
    {
        this.isOnline = online;
    }
}
