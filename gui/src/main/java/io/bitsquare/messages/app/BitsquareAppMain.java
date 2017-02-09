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

package io.bitsquare.messages.app;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.messages.app.BitsquareEnvironment.DEFAULT_APP_NAME;
import static io.bitsquare.messages.app.BitsquareEnvironment.DEFAULT_USER_DATA_DIR;

public class BitsquareAppMain extends BitsquareExecutable {
    private static final Logger log = LoggerFactory.getLogger(BitsquareAppMain.class);

    public static void main(String[] args) throws Exception {
        // We don't want to do the full argument parsing here as that might easily change in update versions
        // So we only handle the absolute minimum which is APP_NAME, APP_DATA_DIR_KEY and USER_DATA_DIR
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
        parser.accepts(AppOptionKeys.USER_DATA_DIR_KEY, description("User data directory", DEFAULT_USER_DATA_DIR))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.APP_NAME_KEY, description("Application name", DEFAULT_APP_NAME))
                .withRequiredArg();

        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException ex) {
            System.out.println("error: " + ex.getMessage());
            System.out.println();
            parser.printHelpOn(System.out);
            System.exit(EXIT_FAILURE);
            return;
        }
        BitsquareEnvironment bitsquareEnvironment = getBitsquareEnvironment(options);

        // need to call that before BitsquareAppMain().execute(args)
        BitsquareExecutable.initAppDir(bitsquareEnvironment.getProperty(AppOptionKeys.APP_DATA_DIR_KEY));

        // For some reason the JavaFX launch process results in us losing the thread context class loader: reset it.
        // In order to work around a bug in JavaFX 8u25 and below, you must include the following code as the first line of your realMain method:
        Thread.currentThread().setContextClassLoader(BitsquareAppMain.class.getClassLoader());

        new BitsquareAppMain().execute(args);
    }

    @Override
    protected void doExecute(OptionSet options) {
        BitsquareApp.setEnvironment(getBitsquareEnvironment(options));
        javafx.application.Application.launch(BitsquareApp.class);
    }
}
