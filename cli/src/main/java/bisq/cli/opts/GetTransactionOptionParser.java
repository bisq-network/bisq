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

import static bisq.cli.opts.OptLabel.OPT_TRANSACTION_ID;

public class GetTransactionOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> txIdOpt = parser.accepts(OPT_TRANSACTION_ID, "id of transaction")
            .withRequiredArg();

    public GetTransactionOptionParser(String[] args) {
        super(args);
    }

    public GetTransactionOptionParser parse() {
        super.parse();

        // Short circuit opt validation if user just wants help.
        if (options.has(helpOpt))
            return this;

        if (!options.has(txIdOpt) || options.valueOf(txIdOpt).isEmpty())
            throw new IllegalArgumentException("no tx id specified");

        return this;
    }

    public String getTxId() {
        return options.valueOf(txIdOpt);
    }
}
