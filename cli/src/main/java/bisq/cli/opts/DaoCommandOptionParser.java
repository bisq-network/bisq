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

import static bisq.cli.opts.OptLabel.OPT_ASSET_CODE;
import static bisq.cli.opts.OptLabel.OPT_BONDED_ROLE_TYPE;
import static bisq.cli.opts.OptLabel.OPT_BURNINGMAN_RECEIVER;
import static bisq.cli.opts.OptLabel.OPT_CYCLE_INDEX;
import static bisq.cli.opts.OptLabel.OPT_FILTER;
import static bisq.cli.opts.OptLabel.OPT_LINK;
import static bisq.cli.opts.OptLabel.OPT_LOCKUP_TX_ID;
import static bisq.cli.opts.OptLabel.OPT_NAME;
import static bisq.cli.opts.OptLabel.OPT_PARAM;
import static bisq.cli.opts.OptLabel.OPT_PARAM_VALUE;
import static bisq.cli.opts.OptLabel.OPT_PROPOSAL_TX_ID;
import static bisq.cli.opts.OptLabel.OPT_REQUESTED_BSQ;
import static bisq.cli.opts.OptLabel.OPT_STAKE;
import static bisq.cli.opts.OptLabel.OPT_VOTE;

/**
 * Unified parser for every DAO CLI command. Each command sets only the options it needs;
 * unused options are simply absent. Keeping all DAO options in one place avoids having
 * a separate parser file per verb (there are 17 DAO verbs).
 */
public class DaoCommandOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    final OptionSpec<String> nameOpt = parser.accepts(OPT_NAME, "proposal name").withRequiredArg();
    final OptionSpec<String> linkOpt = parser.accepts(OPT_LINK, "proposal link").withRequiredArg();
    final OptionSpec<Long> requestedBsqOpt = parser.accepts(OPT_REQUESTED_BSQ,
            "requested BSQ amount in sats").withRequiredArg().ofType(Long.class).defaultsTo(0L);
    final OptionSpec<String> paramOpt = parser.accepts(OPT_PARAM,
            "DAO Param enum name").withRequiredArg();
    final OptionSpec<String> paramValueOpt = parser.accepts(OPT_PARAM_VALUE,
            "new value for DAO param").withRequiredArg();
    final OptionSpec<String> bondedRoleTypeOpt = parser.accepts(OPT_BONDED_ROLE_TYPE,
            "BondedRoleType enum name").withRequiredArg();
    final OptionSpec<String> lockupTxIdOpt = parser.accepts(OPT_LOCKUP_TX_ID,
            "lockup tx id to confiscate").withRequiredArg();
    final OptionSpec<String> assetCodeOpt = parser.accepts(OPT_ASSET_CODE,
            "asset ticker code").withRequiredArg();
    final OptionSpec<String> burningManReceiverOpt = parser.accepts(OPT_BURNINGMAN_RECEIVER,
            "optional burning man receiver address").withRequiredArg().defaultsTo("");
    final OptionSpec<String> proposalTxIdOpt = parser.accepts(OPT_PROPOSAL_TX_ID,
            "proposal transaction id").withRequiredArg();
    final OptionSpec<String> voteOpt = parser.accepts(OPT_VOTE,
            "vote: accept | reject | ignore").withRequiredArg();
    final OptionSpec<Long> stakeOpt = parser.accepts(OPT_STAKE,
            "blind vote stake in BSQ sats").withRequiredArg().ofType(Long.class).defaultsTo(0L);
    final OptionSpec<Integer> cycleIndexOpt = parser.accepts(OPT_CYCLE_INDEX,
            "cycle index (negative = most recent completed)")
            .withRequiredArg().ofType(Integer.class).defaultsTo(-1);
    final OptionSpec<String> filterOpt = parser.accepts(OPT_FILTER,
            "proposal filter: active | my | all | for-cycle").withRequiredArg().defaultsTo("active");

    public DaoCommandOptionParser(String[] args) {
        super(args);
    }

    public DaoCommandOptionParser parse() {
        super.parse();
        return this;
    }

    public String getName() {
        if (!options.has(nameOpt) || options.valueOf(nameOpt).isEmpty())
            throw new IllegalArgumentException("--" + OPT_NAME + " is required");
        return options.valueOf(nameOpt);
    }

    public String getLink() {
        if (!options.has(linkOpt) || options.valueOf(linkOpt).isEmpty())
            throw new IllegalArgumentException("--" + OPT_LINK + " is required");
        return options.valueOf(linkOpt);
    }

    public long getRequestedBsq() {
        long v = options.valueOf(requestedBsqOpt);
        if (v <= 0) throw new IllegalArgumentException("--" + OPT_REQUESTED_BSQ + " must be > 0");
        return v;
    }

    public String getParam() {
        if (!options.has(paramOpt) || options.valueOf(paramOpt).isEmpty())
            throw new IllegalArgumentException("--" + OPT_PARAM + " is required");
        return options.valueOf(paramOpt);
    }

    public String getParamValue() {
        if (!options.has(paramValueOpt))
            throw new IllegalArgumentException("--" + OPT_PARAM_VALUE + " is required");
        return options.valueOf(paramValueOpt);
    }

    public String getBondedRoleType() {
        if (!options.has(bondedRoleTypeOpt) || options.valueOf(bondedRoleTypeOpt).isEmpty())
            throw new IllegalArgumentException("--" + OPT_BONDED_ROLE_TYPE + " is required");
        return options.valueOf(bondedRoleTypeOpt);
    }

    public String getLockupTxId() {
        if (!options.has(lockupTxIdOpt) || options.valueOf(lockupTxIdOpt).isEmpty())
            throw new IllegalArgumentException("--" + OPT_LOCKUP_TX_ID + " is required");
        return options.valueOf(lockupTxIdOpt);
    }

    public String getAssetCode() {
        if (!options.has(assetCodeOpt) || options.valueOf(assetCodeOpt).isEmpty())
            throw new IllegalArgumentException("--" + OPT_ASSET_CODE + " is required");
        return options.valueOf(assetCodeOpt);
    }

    public String getBurningManReceiver() {
        return options.valueOf(burningManReceiverOpt);
    }

    public String getProposalTxId() {
        if (!options.has(proposalTxIdOpt) || options.valueOf(proposalTxIdOpt).isEmpty())
            throw new IllegalArgumentException("--" + OPT_PROPOSAL_TX_ID + " is required");
        return options.valueOf(proposalTxIdOpt);
    }

    public String getVote() {
        if (!options.has(voteOpt) || options.valueOf(voteOpt).isEmpty())
            throw new IllegalArgumentException("--" + OPT_VOTE + " is required");
        return options.valueOf(voteOpt);
    }

    public long getStake() {
        long v = options.valueOf(stakeOpt);
        if (v <= 0) throw new IllegalArgumentException("--" + OPT_STAKE + " must be > 0");
        return v;
    }

    public int getCycleIndex() {
        return options.valueOf(cycleIndexOpt);
    }

    public String getFilter() {
        return options.valueOf(filterOpt);
    }
}
