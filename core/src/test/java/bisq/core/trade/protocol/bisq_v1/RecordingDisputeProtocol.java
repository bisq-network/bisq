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

package bisq.core.trade.protocol.bisq_v1;

import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.FluentProtocol;

import bisq.common.taskrunner.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * A DisputeProtocol that records the tasks of a handled message or event instead of running them.
 */
class RecordingDisputeProtocol extends DisputeProtocol {
    final List<Class<? extends Task<TradeModel>>> executedTasks = new ArrayList<>();
    // Runs the result handler of a recorded task runner, as if its tasks had completed
    boolean runResultHandlers;

    RecordingDisputeProtocol(Trade trade) {
        super(trade);
    }

    @Override
    protected FluentProtocol expect(FluentProtocol.Condition condition) {
        return new FluentProtocol(this) {
            private Setup recordedSetup;

            {
                condition(condition);
            }

            @Override
            public FluentProtocol setup(Setup setup) {
                recordedSetup = setup;
                return this;
            }

            // Records the tasks only if the condition is met, as executeTasks would run them
            @Override
            public FluentProtocol executeTasks() {
                return run(() -> {
                    executedTasks.addAll(List.of(recordedSetup.getTasks()));
                    if (runResultHandlers) {
                        // Without added tasks the runner calls its result handler at once
                        recordedSetup.getTaskRunner(condition.getMessage(), condition.getEvent()).run();
                    }
                });
            }
        };
    }
}
