package io.bitsquare.gui.util;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Verification
{

    private static final Logger log = LoggerFactory.getLogger(Verification.class);

    @Inject
    public Verification()
    {
    }

    // TODO define rules for prim. and sec. ID per bank account type
    public static boolean verifyAccountIDsByBankTransferType(Object bankTransferTypeSelectedItem, String accountPrimaryID, String accountSecondaryID)
    {
        return true;
    }
}
