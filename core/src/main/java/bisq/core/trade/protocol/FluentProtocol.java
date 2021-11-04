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

import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.TradePhase;
import bisq.core.trade.model.TradeState;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.Task;

import java.text.MessageFormat;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.util.Validator.isTradeIdValid;
import static com.google.common.base.Preconditions.checkArgument;

// Main class. Contains the condition and setup, if condition is valid it will execute the
// taskRunner and the optional runnable.
public class FluentProtocol {

    public interface Event {
        String name();
    }

    private final TradeProtocol tradeProtocol;
    private Condition condition;
    private Setup setup;
    private Consumer<Condition.Result> resultHandler;

    public FluentProtocol(TradeProtocol tradeProtocol) {
        this.tradeProtocol = tradeProtocol;
    }

    protected FluentProtocol condition(Condition condition) {
        this.condition = condition;
        return this;
    }

    public FluentProtocol setup(Setup setup) {
        this.setup = setup;
        return this;
    }


    public FluentProtocol resultHandler(Consumer<Condition.Result> resultHandler) {
        this.resultHandler = resultHandler;
        return this;
    }

    // Can be used before or after executeTasks
    public FluentProtocol run(Runnable runnable) {
        Condition.Result result = condition.getResult();
        if (result.isValid) {
            runnable.run();
        } else if (resultHandler != null) {
            resultHandler.accept(result);
        }
        return this;
    }

    public FluentProtocol executeTasks() {
        Condition.Result result = condition.getResult();
        if (!result.isValid) {
            if (resultHandler != null) {
                resultHandler.accept(result);
            }
            return this;
        }

        if (setup.getTimeoutSec() > 0) {
            tradeProtocol.startTimeout(setup.getTimeoutSec());
        }

        NodeAddress peer = condition.getPeer();
        if (peer != null) {
            tradeProtocol.protocolModel.setTempTradingPeerNodeAddress(peer);
            tradeProtocol.protocolModel.getTradeManager().requestPersistence();
        }

        TradeMessage message = condition.getMessage();
        if (message != null) {
            tradeProtocol.protocolModel.setTradeMessage(message);
            tradeProtocol.protocolModel.getTradeManager().requestPersistence();
        }

        TradeTaskRunner taskRunner = setup.getTaskRunner(message, condition.getEvent());
        taskRunner.addTasks(setup.getTasks());
        taskRunner.run();
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Condition class
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Slf4j
    public static class Condition {
        enum Result {
            VALID(true),
            INVALID_PHASE,
            INVALID_STATE,
            INVALID_PRE_CONDITION,
            INVALID_TRADE_ID;

            @Getter
            private boolean isValid;
            @Getter
            private String info;

            Result() {
            }

            Result(boolean isValid) {
                this.isValid = isValid;
            }

            public Result info(String info) {
                this.info = info;
                return this;
            }
        }

        private final Set<TradePhase> expectedPhases = new HashSet<>();
        private final Set<TradeState> expectedStates = new HashSet<>();
        private final Set<Boolean> preConditions = new HashSet<>();
        private final TradeModel tradeModel;
        @Nullable
        private Result result;

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


        public Condition(TradeModel tradeModel) {
            this.tradeModel = tradeModel;
        }

        public Condition phase(TradePhase expectedPhase) {
            checkArgument(result == null);
            this.expectedPhases.add(expectedPhase);
            return this;
        }

        public Condition anyPhase(TradePhase... expectedPhases) {
            checkArgument(result == null);
            this.expectedPhases.addAll(Set.of(expectedPhases));
            return this;
        }

        public Condition state(TradeState state) {
            checkArgument(result == null);
            this.expectedStates.add(state);
            return this;
        }

        public Condition anyState(TradeState... states) {
            checkArgument(result == null);
            this.expectedStates.addAll(Set.of(states));
            return this;
        }

        public Condition with(TradeMessage message) {
            checkArgument(result == null);
            this.message = message;
            return this;
        }

        public Condition with(Event event) {
            checkArgument(result == null);
            this.event = event;
            return this;
        }

        public Condition from(NodeAddress peer) {
            checkArgument(result == null);
            this.peer = peer;
            return this;
        }

        public Condition preCondition(boolean preCondition) {
            checkArgument(result == null);
            preConditions.add(preCondition);
            return this;
        }

        public Condition preCondition(boolean preCondition, Runnable conditionFailedHandler) {
            checkArgument(result == null);
            preCondition(preCondition);

            this.preConditionFailedHandler = conditionFailedHandler;
            return this;
        }

        public Result getResult() {
            if (result == null) {
                boolean isTradeIdValid = message == null || isTradeIdValid(tradeModel.getId(), message);
                if (!isTradeIdValid) {
                    String info = MessageFormat.format("TradeId does not match tradeId in message, TradeId={0}, tradeId in message={1}",
                            tradeModel.getId(), message.getTradeId());
                    result = Result.INVALID_TRADE_ID.info(info);
                    return result;
                }


                Result phaseValidationResult = getPhaseResult();
                if (!phaseValidationResult.isValid) {
                    result = phaseValidationResult;
                    return result;
                }

                Result stateResult = getStateResult();
                if (!stateResult.isValid) {
                    result = stateResult;
                    return result;
                }

                boolean allPreConditionsMet = preConditions.stream().allMatch(e -> e);
                if (!allPreConditionsMet) {
                    String info = MessageFormat.format("PreConditions not met. preConditions={0}, this={1}, tradeId={2}",
                            preConditions, this, tradeModel.getId());
                    result = Result.INVALID_PRE_CONDITION.info(info);

                    if (preConditionFailedHandler != null) {
                        preConditionFailedHandler.run();
                    }
                    return result;
                }

                result = Result.VALID;
            }
            return result;
        }

        private Result getPhaseResult() {
            if (expectedPhases.isEmpty()) {
                return Result.VALID;
            }

            boolean isPhaseValid = expectedPhases.stream().anyMatch(e -> e == tradeModel.getTradePhase());
            String trigger = message != null ?
                    message.getClass().getSimpleName() :
                    event != null ?
                            event.name() + " event" :
                            "";
            if (isPhaseValid) {
                String info = MessageFormat.format("We received a {0} at phase {1} and state {2}, tradeId={3}",
                        trigger,
                        tradeModel.getTradePhase(),
                        tradeModel.getTradeState(),
                        tradeModel.getId());
                log.info(info);
                return Result.VALID.info(info);
            } else {
                String info = MessageFormat.format("We received a {0} but we are are not in the expected phase.\n" +
                                "This can be an expected case if we get a repeated CounterCurrencyTransferStartedMessage " +
                                "after we have already received one as the peer re-sends that message at each startup.\n" +
                                "Expected phases={1},\nTrade phase={2},\nTrade state= {3},\ntradeId={4}",
                        trigger,
                        expectedPhases,
                        tradeModel.getTradePhase(),
                        tradeModel.getTradeState(),
                        tradeModel.getId());
                return Result.INVALID_PHASE.info(info);
            }
        }

        private Result getStateResult() {
            if (expectedStates.isEmpty()) {
                return Result.VALID;
            }

            boolean isStateValid = expectedStates.stream().anyMatch(e -> e == tradeModel.getTradeState());
            String trigger = message != null ?
                    message.getClass().getSimpleName() :
                    event != null ?
                            event.name() + " event" :
                            "";
            if (isStateValid) {
                String info = MessageFormat.format("We received a {0} at state {1}, tradeId={2}",
                        trigger,
                        tradeModel.getTradeState(),
                        tradeModel.getId());
                log.info(info);
                return Result.VALID.info(info);
            } else {
                String info = MessageFormat.format("We received a {0} but we are are not in the expected state. " +
                                "Expected states={1}, Trade state= {2}, tradeId={3}",
                        trigger,
                        expectedStates,
                        tradeModel.getTradeState(),
                        tradeModel.getId());
                return Result.INVALID_STATE.info(info);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setup class
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Slf4j
    public static class Setup {
        private final TradeProtocol tradeProtocol;
        private final TradeModel tradeModel;
        @Getter
        private Class<? extends Task<TradeModel>>[] tasks;
        @Getter
        private int timeoutSec;
        @Nullable
        private TradeTaskRunner taskRunner;

        public Setup(TradeProtocol tradeProtocol, TradeModel tradeModel) {
            this.tradeProtocol = tradeProtocol;
            this.tradeModel = tradeModel;
        }

        @SafeVarargs
        public final Setup tasks(Class<? extends Task<TradeModel>>... tasks) {
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
                    taskRunner = new TradeTaskRunner(tradeModel,
                            () -> tradeProtocol.handleTaskRunnerSuccess(message),
                            errorMessage -> tradeProtocol.handleTaskRunnerFault(message, errorMessage));
                } else if (event != null) {
                    taskRunner = new TradeTaskRunner(tradeModel,
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
