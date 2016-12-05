/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.util.validation;



import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bitsquare.gui.util.validation.ioputils.IoP_MainNetParams;

public final class IOPAddressValidator extends InputValidator {
	
    private static final Logger log = LoggerFactory.getLogger(IOPAddressValidator.class);
   // private static final NetworkParameters params = MainNetParams.get();
    private static  final 	NetworkParameters p = IoP_MainNetParams.get();
    
    @Override
    public ValidationResult validate(String input) {
    	
        ValidationResult validationResult = super.validate(input);
        
        if (!validationResult.isValid) {
        	return new ValidationResult(false);
        }else{
        	
        	try {
                new Address(p, input);
                return new ValidationResult(true);
            } catch (AddressFormatException e) {
            	return new ValidationResult(false);
            }
        	
        }
        
    }    
    
}