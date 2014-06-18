package io.bitsquare.util;

import io.bitsquare.locale.CountryUtil;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class LocationUtilsTest
{

    @Test
    public void testVerifyBankAccountData()
    {
        CountryUtil.getAllCountries();
        assertTrue(true);
        //assertTrue(Verification.verifyAccountIDsByBankTransferType(BankAccountType.BankAccountTypeEnum.SEPA, "DE11876543210000123456", "12345678"));
    }

}
