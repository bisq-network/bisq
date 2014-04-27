package io.bitsquare.gui.util;

import io.bitsquare.bank.BankAccountType;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class VerificationTest
{
    @Test
    public void testVerifyBankAccountData()
    {
        // TODO define rules for prim. and sec. ID per bank account type
        assertTrue(Verification.verifyAccountIDsByBankTransferType(BankAccountType.BankAccountTypeEnum.SEPA, "DE11876543210000123456", "12345678"));
    }
}
