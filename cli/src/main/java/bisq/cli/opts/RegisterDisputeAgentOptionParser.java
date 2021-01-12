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

package bisq.cli.opts;


import joptsimple.OptionSpec;

import static joptsimple.internal.Strings.EMPTY;

public class RegisterDisputeAgentOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> disputeAgentTypeOpt = parser.accepts("t", "dispute agent type")
            .withRequiredArg()
            .defaultsTo(EMPTY);

    final OptionSpec<String> registrationKeyOpt = parser.accepts("k", "registration key")
            .withRequiredArg()
            .defaultsTo(EMPTY);

    public RegisterDisputeAgentOptionParser(String[] args) {
        super(args);
    }

    public RegisterDisputeAgentOptionParser parse() {
        super.parse();

        if (!options.has(disputeAgentTypeOpt))
            throw new IllegalArgumentException("no dispute agent type specified");

        if (!options.has(registrationKeyOpt))
            throw new IllegalArgumentException("no registration key specified");

        return this;
    }

    public String getDisputeAgentType() {
        return options.valueOf(disputeAgentTypeOpt);
    }

    public String getRegistrationKey() {
        return options.valueOf(registrationKeyOpt);
    }
}
