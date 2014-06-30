package io.bitsquare.bank;

import java.util.ArrayList;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

public enum BankAccountType
{
    SEPA("IBAN", "BIC"),
    WIRE("primary ID", "secondary ID"),
    INTERNATIONAL("primary ID", "secondary ID"),
    OK_PAY("primary ID", "secondary ID"),
    NET_TELLER("primary ID", "secondary ID"),
    PERFECT_MONEY("primary ID", "secondary ID"),
    OTHER("primary ID", "secondary ID");

    @NotNull
    private final String primaryId;
    @NotNull
    private final String secondaryId;

    BankAccountType(@NotNull String primaryId, @NotNull String secondaryId)
    {
        this.primaryId = primaryId;
        this.secondaryId = secondaryId;
    }

    @NotNull
    public static ArrayList<BankAccountType> getAllBankAccountTypes()
    {
        return new ArrayList<>(Arrays.asList(BankAccountType.values()));
    }

    @NotNull
    public String getPrimaryId()
    {
        return primaryId;
    }

    @NotNull
    public String getSecondaryId()
    {
        return secondaryId;
    }
}
