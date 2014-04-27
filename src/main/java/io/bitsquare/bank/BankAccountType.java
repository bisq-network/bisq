package io.bitsquare.bank;

import java.io.Serializable;

public class BankAccountType implements Serializable
{

    private static final long serialVersionUID = -8772708150197835288L;

    private BankAccountTypeEnum type;
    private String primaryIDName;
    private String secondaryIDName;

    public BankAccountType(BankAccountTypeEnum type, String primaryIDName, String secondaryIDName)
    {
        this.type = type;
        this.primaryIDName = primaryIDName;
        this.secondaryIDName = secondaryIDName;
    }

    public BankAccountTypeEnum getType()
    {
        return type;
    }

    public String getPrimaryIDName()
    {
        return primaryIDName;
    }

    public String getSecondaryIDName()
    {
        return secondaryIDName;
    }

    @Override
    public String toString()
    {
        //TODO localisation
        return type.toString();
    }

    public static enum BankAccountTypeEnum
    {
        SEPA, WIRE, INTERNATIONAL, OK_PAY, NET_TELLER, PERFECT_MONEY, OTHER
    }
}
