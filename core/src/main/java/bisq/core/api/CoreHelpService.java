/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.api;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import lombok.extern.slf4j.Slf4j;

import static java.io.File.separator;
import static java.lang.String.format;
import static java.lang.System.out;

@Singleton
@Slf4j
class CoreHelpService {

    @Inject
    public CoreHelpService() {
    }

    public String getMethodHelp(String methodName) {
        String resourceFile = "/help" + separator + methodName + "-" + "help.txt";
        try {
            return readHelpFile(resourceFile);
        } catch (NullPointerException ex) {
            log.error("", ex);
            throw new IllegalStateException(format("no help found for api method %s", methodName));
        } catch (IOException ex) {
            log.error("", ex);
            throw new IllegalStateException(format("could not read %s help doc", methodName));
        }
    }

    private String readHelpFile(String resourceFile) throws NullPointerException, IOException {
        // The deployed text file is in the core.jar file, so use
        // Class.getResourceAsStream to read it.
        InputStream is = getClass().getResourceAsStream(resourceFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuilder builder = new StringBuilder();
        while ((line = br.readLine()) != null)
            builder.append(line).append("\n");

        return builder.toString();
    }

    // Main method for devs to view help text without running the server.
    @SuppressWarnings("CommentedOutCode")
    public static void main(String[] args) {
        CoreHelpService coreHelpService = new CoreHelpService();
        out.println(coreHelpService.getMethodHelp("getversion"));
        // out.println(coreHelpService.getMethodHelp("getfundingaddresses"));
        // out.println(coreHelpService.getMethodHelp("getfundingaddresses"));
        // out.println(coreHelpService.getMethodHelp("getunusedbsqaddress"));
        // out.println(coreHelpService.getMethodHelp("unsettxfeerate"));
        // out.println(coreHelpService.getMethodHelp("getpaymentmethods"));
        // out.println(coreHelpService.getMethodHelp("getpaymentaccts"));
        // out.println(coreHelpService.getMethodHelp("lockwallet"));
        // out.println(coreHelpService.getMethodHelp("gettxfeerate"));
        // out.println(coreHelpService.getMethodHelp("createoffer"));
        // out.println(coreHelpService.getMethodHelp("takeoffer"));
        // out.println(coreHelpService.getMethodHelp("garbage"));
        // out.println(coreHelpService.getMethodHelp(""));
        // out.println(coreHelpService.getMethodHelp(null));
    }
}
