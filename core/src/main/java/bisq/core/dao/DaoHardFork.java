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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao;

import bisq.common.config.Config;

public final class DaoHardFork {
    // TODO The mainnet and testnet heights below are placeholders. They require explicit developer approval and a
    //  coordinated rollout before release. A future height does not prevent an old node from parsing past the fork;
    //  incompatible nodes would derive different DAO state at the first illegal spend. The mainnet value is about
    //  94 days after the DAO state snapshot shipped in the resources (chain height 961400).
    private static final int ACTIVATE_HARD_FORK_3_HEIGHT_MAINNET = 975_000;
    private static final int ACTIVATE_HARD_FORK_3_HEIGHT_TESTNET = 3_000_000;
    private static final int ACTIVATE_HARD_FORK_3_HEIGHT_REGTEST = 1;

    public static boolean isHardFork3Activated(int blockHeight) {
        return blockHeight >= getHardFork3ActivationHeight();
    }

    public static int getHardFork3ActivationHeight() {
        return switch (Config.baseCurrencyNetwork()) {
            case BTC_MAINNET -> ACTIVATE_HARD_FORK_3_HEIGHT_MAINNET;
            case BTC_TESTNET -> ACTIVATE_HARD_FORK_3_HEIGHT_TESTNET;
            case BTC_REGTEST, BTC_DAO_TESTNET, BTC_DAO_BETANET, BTC_DAO_REGTEST ->
                    ACTIVATE_HARD_FORK_3_HEIGHT_REGTEST;
        };
    }

    private DaoHardFork() {
    }
}
