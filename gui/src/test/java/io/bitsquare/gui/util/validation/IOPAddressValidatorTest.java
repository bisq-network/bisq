package io.bitsquare.gui.util.validation;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class IOPAddressValidatorTest {

    @Test
    public void testIOP() {
    	
    	IOPAddressValidator validator = new IOPAddressValidator();

        assertTrue(validator.validate("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem").isValid);
        assertTrue(validator.validate("pKbz7iRUSiUaTgh4UuwQCnc6pWZnyCGWxM").isValid);
        
    }
    
    
}