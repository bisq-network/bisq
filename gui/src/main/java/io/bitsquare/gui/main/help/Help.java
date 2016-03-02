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

package io.bitsquare.gui.main.help;

import io.bitsquare.common.util.Utilities;
import io.bitsquare.gui.main.overlays.popups.Popup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO Find good solution for a web based help content management system.
public class Help {
    private static final Logger log = LoggerFactory.getLogger(Help.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void openWindow(HelpId id) {
        try {
            // TODO create user guide
            Utilities.openWebPage("http://bitsquare.io/faq");
            // Utilities.openWebPage("https://github.com/bitsquare/bitsquare/wiki/User-Guide");
        } catch (Exception e) {
            log.error(e.getMessage());
            new Popup().warning("Opening browser failed. Please check your internet " +
                    "connection.").show();
        }
    }

}
