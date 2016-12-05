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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IOPAddressValidator extends InputValidator {
	
    private static final Logger log = LoggerFactory.getLogger(IOPAddressValidator.class);
   // private static final NetworkParameters params = MainNetParams.get();
    
    @Override
    public ValidationResult validate(String input) {
    	
        ValidationResult validationResult = super.validate(input);
        
        if (!validationResult.isValid) {
        	return new ValidationResult(false);
        }else{
        	
    	  //new Address(params, input);
    	  if (input.matches("[p][A-Za-z1-9]{25,34}$"))
        	 return new ValidationResult(true);
          else
             return new ValidationResult(false, "Invalid format of the IOP address.");
        	
        }
        
    }    
    
}