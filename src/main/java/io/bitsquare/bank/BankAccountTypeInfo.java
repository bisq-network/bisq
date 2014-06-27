package io.bitsquare.bank;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Objects;

public class BankAccountTypeInfo implements Serializable
{
    private static final long serialVersionUID = -8772708150197835288L;
    private final BankAccountType type;
    private final String primaryIDName;
    private final String secondaryIDName;

    public BankAccountTypeInfo(BankAccountType type, String primaryIDName, String secondaryIDName)
    {
        this.type = type;
        this.primaryIDName = primaryIDName;
        this.secondaryIDName = secondaryIDName;
    }

    public static ArrayList<BankAccountTypeInfo> getAllBankAccountTypeInfoObjects()
    {
        ArrayList<BankAccountTypeInfo> bankTransferTypes = new ArrayList<>();
        bankTransferTypes.add(new BankAccountTypeInfo(BankAccountType.SEPA, "IBAN", "BIC"));
        bankTransferTypes.add(new BankAccountTypeInfo(BankAccountType.WIRE, "Prim_todo", "Sec_todo"));
        bankTransferTypes.add(new BankAccountTypeInfo(BankAccountType.INTERNATIONAL, "Prim_todo", "Sec_todo"));
        bankTransferTypes.add(new BankAccountTypeInfo(BankAccountType.OK_PAY, "Prim_todo", "Sec_todo"));
        bankTransferTypes.add(new BankAccountTypeInfo(BankAccountType.NET_TELLER, "Prim_todo", "Sec_todo"));
        bankTransferTypes.add(new BankAccountTypeInfo(BankAccountType.PERFECT_MONEY, "Prim_todo", "Sec_todo"));
        bankTransferTypes.add(new BankAccountTypeInfo(BankAccountType.OTHER, "Prim_todo", "Sec_todo"));
        return bankTransferTypes;
    }

    public int hashCode()
    {
        return Objects.hashCode(type);
    }

    public boolean equals(Object obj)
    {
        if (!(obj instanceof BankAccountTypeInfo))
            return false;
        if (obj == this)
            return true;

        BankAccountTypeInfo other = (BankAccountTypeInfo) obj;
        return other.getType().equals(type);
    }

    public BankAccountType getType()
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

    public static enum BankAccountType
    {
        SEPA, WIRE, INTERNATIONAL, OK_PAY, NET_TELLER, PERFECT_MONEY, OTHER
    }
}
