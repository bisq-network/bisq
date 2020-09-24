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

package bisq.core.trade.protocol;

import bisq.core.trade.Trade;
import bisq.core.trade.messages.TradeMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.Task;

import java.util.HashSet;
import java.util.Set;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.util.Validator.isTradeIdValid;
import static com.google.common.base.Preconditions.checkArgument;

// Main class. Contains the condition and setup, if condition is valid it will execute the
// taskRunner and the optional runnable.
public class FluentProtocol {
    interface Event {
        String name();
    }

    private final TradeProtocol tradeProtocol;
    private Condition condition;
    private Setup setup;

    public FluentProtocol(TradeProtocol tradeProtocol) {
        this.tradeProtocol = tradeProtocol;
    }

    protected FluentProtocol condition(Condition condition) {
        this.condition = condition;
        return this;
    }

    protected FluentProtocol setup(Setup setup) {
        this.setup = setup;
        return this;
    }

    // Can be used before or after executeTasks
    public FluentProtocol run(Runnable runnable) {
        if (condition.isValid()) {
            runnable.run();
        }
        return this;
    }

    public FluentProtocol executeTasks() {
        if (condition.isValid()) {
            if (setup.getTimeoutSec() > 0) {
                tradeProtocol.startTimeout(setup.getTimeoutSec());
            }

            NodeAddress peer = condition.getPeer();
            if (peer != null) {
                tradeProtocol.processModel.setTempTradingPeerNodeAddress(peer);
            }

            TradeMessage message = condition.getMessage();
            if (message != null) {
                tradeProtocol.processModel.setTradeMessage(message);
            }

            TradeTaskRunner taskRunner = setup.getTaskRunner(message, condition.getEvent());
            taskRunner.addTasks(setup.getTasks());
            taskRunner.run();
        }
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Condition class
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Slf4j
    public static class Condition {
        private final Set<Trade.Phase> expectedPhases = new HashSet<>();
        private final Set<Trade.State> expectedStates = new HashSet<>();
        private final Set<Boolean> preConditions = new HashSet<>();
        private final Trade trade;

        @Nullable
        @Getter
        private TradeMessage message;
        @Nullable
        @Getter
        private Event event;
        @Nullable
        @Getter
        private NodeAddress peer;
        @Nullable
        private Runnable preConditionFailedHandler;

        private boolean isValid;
        private boolean isValidated; // We validate only once

        public Condition(Trade trade) {
            this.trade = trade;
        }

        public Condition phase(Trade.Phase expectedPhase) {
            checkArgument(!isValidated);
            this.expectedPhases.add(expectedPhase);
            return this;
        }

        public Condition anyPhase(Trade.Phase... expectedPhases) {
            checkArgument(!isValidated);
            this.expectedPhases.addAll(Set.of(expectedPhases));
            return this;
        }

        public Condition state(Trade.State state) {
            checkArgument(!isValidated);
            this.expectedStates.add(state);
            return this;
        }

        public Condition anyState(Trade.State... states) {
            checkArgument(!isValidated);
            this.expectedStates.addAll(Set.of(states));
            return this;
        }

        public Condition with(TradeMessage message) {
            checkArgument(!isValidated);
            this.message = message;
            return this;
        }

        public Condition with(Event event) {
            checkArgument(!isValidated);
            this.event = event;
            return this;
        }

        public Condition from(NodeAddress peer) {
            checkArgument(!isValidated);
            this.peer = peer;
            return this;
        }

        public Condition preCondition(boolean preCondition) {
            checkArgument(!isValidated);
            preConditions.add(preCondition);
            return this;
        }

        public Condition preCondition(boolean preCondition, Runnable conditionFailedHandler) {
            checkArgument(!isValidated);
            preCondition(preCondition);

            this.preConditionFailedHandler = conditionFailedHandler;
            return this;
        }

        public boolean isValid() {
            if (!isValidated) {
                boolean isPhaseValid = isPhaseValid();
                boolean isStateValid = isStateValid();

                boolean allPreConditionsMet = preConditions.stream().allMatch(e -> e);
                boolean isTradeIdValid = message == null || isTradeIdValid(trade.getId(), message);

                if (!allPreConditionsMet) {
                    log.error("PreConditions not met. preConditions={}, this={}", preConditions, this);
                    if (preConditionFailedHandler != null) {
                        preConditionFailedHandler.run();
                    }
                }
                if (!isTradeIdValid) {
                    log.error("TradeId does not match tradeId in message, TradeId={}, tradeId in message={}",
                            trade.getId(), message.getTradeId());
                }

                isValid = isPhaseValid && isStateValid && allPreConditionsMet && isTradeIdValid;
                isValidated = true;
            }
            return isValid;
        }

        private boolean isPhaseValid() {
            if (expectedPhases.isEmpty()) {
                return true;
            }

            boolean isPhaseValid = expectedPhases.stream().anyMatch(e -> e == trade.getPhase());
            String trigger = message != null ?
                    message.getClass().getSimpleName() :
                    event != null ?
                            event.name() + " event" :
                            "";
            if (isPhaseValid) {
                log.info("We received {} at phase {} and state {}",
                        trigger,
                        trade.getPhase(),
                        trade.getState());
            } else {
                log.error("We received {} but we are are not in the correct phase. Expected phases={}, " +
                                "Trade phase={}, Trade state= {} ",
                        trigger,
                        expectedPhases,
                        trade.getPhase(),
                        trade.getState());
            }

            return isPhaseValid;
        }

        private boolean isStateValid() {
            if (expectedStates.isEmpty()) {
                return true;
            }

            boolean isStateValid = expectedStates.stream().anyMatch(e -> e == trade.getState());
            String trigger = message != null ?
                    message.getClass().getSimpleName() :
                    event != null ?
                            event.name() + " event" :
                            "";
            if (isStateValid) {
                log.info("We received {} at state {}",
                        trigger,
                        trade.getState());
            } else {
                log.error("We received {} but we are are not in the correct state. Expected states={}, " +
                                "Trade state= {} ",
                        trigger,
                        expectedStates,
                        trade.getState());
            }

            return isStateValid;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setup class
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Slf4j
    public static class Setup {
        private final TradeProtocol tradeProtocol;
        private final Trade trade;
        @Getter
        private Class<? extends Task<Trade>>[] tasks;
        @Getter
        private int timeoutSec;
        @Nullable
        private TradeTaskRunner taskRunner;

        public Setup(TradeProtocol tradeProtocol, Trade trade) {
            this.tradeProtocol = tradeProtocol;
            this.trade = trade;
        }

        @SafeVarargs
        public final Setup tasks(Class<? extends Task<Trade>>... tasks) {
            this.tasks = tasks;
            return this;
        }

        public Setup withTimeout(int timeoutSec) {
            this.timeoutSec = timeoutSec;
            return this;
        }

        public Setup using(TradeTaskRunner taskRunner) {
            this.taskRunner = taskRunner;
            return this;
        }

        public TradeTaskRunner getTaskRunner(@Nullable TradeMessage message, @Nullable Event event) {
            if (taskRunner == null) {
                if (message != null) {
                    taskRunner = new TradeTaskRunner(trade,
                            () -> tradeProtocol.handleTaskRunnerSuccess(message),
                            errorMessage -> tradeProtocol.handleTaskRunnerFault(message, errorMessage));
                } else if (event != null) {
                    taskRunner = new TradeTaskRunner(trade,
                            () -> tradeProtocol.handleTaskRunnerSuccess(event),
                            errorMessage -> tradeProtocol.handleTaskRunnerFault(event, errorMessage));
                } else {
                    throw new IllegalStateException("addTasks must not be called without message or event " +
                            "set in case no taskRunner has been created yet");
                }
            }
            return taskRunner;
        }
    }
}
