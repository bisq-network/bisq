package io.bitsquare.bank;

import java.util.ArrayList;
import java.util.Arrays;

public enum BankAccountType
{
    SEPA("IBAN", "BIC"),
    WIRE("primary ID", "secondary ID"),
    INTERNATIONAL("primary ID", "secondary ID"),
    OK_PAY("primary ID", "secondary ID"),
    NET_TELLER("primary ID", "secondary ID"),
    PERFECT_MONEY("primary ID", "secondary ID"),
    OTHER("primary ID", "secondary ID");


    private final String primaryId;

    private final String secondaryId;

    BankAccountType(String primaryId, String secondaryId)
    {
        this.primaryId = primaryId;
        this.secondaryId = secondaryId;
    }


    public static ArrayList<BankAccountType> getAllBankAccountTypes()
    {
        return new ArrayList<>(Arrays.asList(BankAccountType.values()));
    }


    public String getPrimaryId()
    {
        return primaryId;
    }


    public String getSecondaryId()
    {
        return secondaryId;
    }
}
