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

package bisq.apitest.botsupport.protocol;

import bisq.proto.grpc.AvailabilityResultWithDescription;
import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TakeOfferReply;
import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import lombok.Getter;

import javax.annotation.Nullable;

import static bisq.apitest.botsupport.shutdown.ManualShutdown.checkIfShutdownCalled;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static protobuf.AvailabilityResult.PRICE_OUT_OF_TOLERANCE;
import static protobuf.AvailabilityResult.UNCONF_TX_LIMIT_HIT;



import bisq.apitest.botsupport.BotClient;


/**
 * Convenience for re-attempting to take an offer after non-fatal errors.
 *
 * One instance can be used to attempt to take an offer several times, but an
 * instance should never be re-used to take different offers.  An instance should be
 * discarded after the run() method returns and the server's reply or exception is
 * processed.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class TakeOfferHelper {

    // Don't use @Slf4j annotation to init log and use in Functions w/out IDE warnings.
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TakeOfferHelper.class);

    @Getter
    private final BotClient botClient;
    private final String botDescription;
    private final OfferInfo offer;
    private final PaymentAccount paymentAccount;
    private final String feeCurrency;
    @Getter
    private final long takeOfferRequestDeadlineInSec;
    private final int maxAttemptsBeforeFail;
    private final long attemptDelayInSec;

    private final AtomicLong startTime = new AtomicLong();
    private final AtomicLong stopTime = new AtomicLong();
    private final Consumer<Long> setSingleAttemptDeadline = (now) -> {
        startTime.set(now);
        stopTime.set(now + SECONDS.toMillis(this.getTakeOfferRequestDeadlineInSec()));
    };
    private final Predicate<Long> deadlineReached = (t) -> t > stopTime.get();

    @Nullable
    @Getter
    private TradeInfo newTrade;
    @Nullable
    @Getter
    private AvailabilityResultWithDescription takeOfferErrorReason;
    @Nullable
    @Getter
    private Throwable fatalThrowable;

    public final Supplier<Boolean> hasNewTrade = () -> newTrade != null;
    public final Supplier<Boolean> hasTakeOfferError = () -> takeOfferErrorReason != null;
    private final CountDownLatch attemptDeadlineLatch = new CountDownLatch(1);

    public TakeOfferHelper(BotClient botClient,
                           String botDescription,
                           OfferInfo offer,
                           PaymentAccount paymentAccount,
                           String feeCurrency,
                           long takeOfferRequestDeadlineInSec,
                           int maxAttemptsBeforeFail,
                           long attemptDelayInSec) {
        this.botClient = botClient;
        this.botDescription = botDescription;
        this.offer = offer;
        this.paymentAccount = paymentAccount;
        this.feeCurrency = feeCurrency;
        this.takeOfferRequestDeadlineInSec = takeOfferRequestDeadlineInSec;
        this.maxAttemptsBeforeFail = maxAttemptsBeforeFail;
        this.attemptDelayInSec = attemptDelayInSec;
    }

    public synchronized void run() {
        checkIfShutdownCalled("Interrupted before attempting to take offer " + offer.getId());
        int attemptCount = 0;
        while (++attemptCount < maxAttemptsBeforeFail) {
            logCurrentTakeOfferAttempt(attemptCount);

            AtomicReference<TakeOfferReply> resultHandler = new AtomicReference(null);
            AtomicReference<Throwable> errorHandler = new AtomicReference(null);
            sendTakeOfferRequest(resultHandler, errorHandler);
            handleTakeOfferReply(resultHandler, errorHandler);

            // If we have a new trade, exit now.
            // If we have an AvailabilityResultWithDescription with a fatal error,
            // the fatalThrowable field was set in handleTakeOfferReply and we exit now.
            // If we have an AvailabilityResultWithDescription with a non-fatal error,
            // try again.
            if (hasNewTrade.get()) {
                break;
            } else if (fatalThrowable != null) {
                break;
            } else {
                logNextTakeOfferAttemptAndWait(attemptCount);
                setSingleAttemptDeadline.accept(currentTimeMillis());
            }
        }
    }

    private void sendTakeOfferRequest(AtomicReference<TakeOfferReply> resultHandler,
                                      AtomicReference<Throwable> errorHandler) {
        // A TakeOfferReply can contain a trade or an AvailabilityResultWithDescription.
        // An AvailabilityResultWithDescription contains an AvailabilityResult enum and
        // a client friendly error/reason message.
        // If the grpc server threw us a StatusRuntimeException instead, the takeoffer
        // request resulted in an unrecoverable error.
        checkIfShutdownCalled("Interrupted while attempting to take offer " + offer.getId());
        botClient.tryToTakeOffer(offer.getId(),
                paymentAccount,
                feeCurrency,
                resultHandler::set,
                errorHandler::set);

        Supplier<Boolean> isReplyReceived = () ->
                resultHandler.get() != null || errorHandler.get() != null;

        setSingleAttemptDeadline.accept(currentTimeMillis());
        while (!deadlineReached.test(currentTimeMillis()) && !isReplyReceived.get()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                attemptDeadlineLatch.await(10, MILLISECONDS);
            } catch (InterruptedException ignored) {
                // empty
            }
        }
        logRequestResult(resultHandler, errorHandler, isReplyReceived);
    }

    private void handleTakeOfferReply(AtomicReference<TakeOfferReply> resultHandler,
                                      AtomicReference<Throwable> errorHandler) {
        if (isSuccessfulTakeOfferRequest.test(resultHandler)) {
            this.newTrade = resultHandler.get().getTrade();
        } else if (resultHandler.get().hasFailureReason()) {
            // Offer was not taken for reason (AvailabilityResult) given by server.
            // Determine if the error is fatal.  If fatal set the fatalThrowable field.
            handleFailureReason(resultHandler, errorHandler);
        } else {
            // Server threw an exception or gave no reason for the failure.
            handleFatalError(errorHandler);
        }
    }

    private void handleFailureReason(AtomicReference<TakeOfferReply> resultHandler,
                                     AtomicReference<Throwable> errorHandler) {
        this.takeOfferErrorReason = resultHandler.get().getFailureReason();
        if (isTakeOfferAttemptErrorNonFatal.test(takeOfferErrorReason)) {
            log.warn("Non fatal error attempting to take offer {}.\n"
                            + "\tReason: {} Description: {}",
                    offer.getId(),
                    takeOfferErrorReason.getAvailabilityResult().name(),
                    takeOfferErrorReason.getDescription());
            this.fatalThrowable = null;
        } else {
            log.error("Fatal error attempting to take offer {}.\n"
                            + "\tReason: {} Description: {}",
                    offer.getId(),
                    takeOfferErrorReason.getAvailabilityResult().name(),
                    takeOfferErrorReason.getDescription());
            this.fatalThrowable = errorHandler.get();
        }
    }

    private void handleFatalError(AtomicReference<Throwable> errorHandler) {
        if (errorHandler.get() != null) {
            log.error("", errorHandler.get());
            throw new IllegalStateException(
                    format("fatal error attempting to take offer %s: %s",
                            offer.getId(),
                            errorHandler.get().getMessage().toLowerCase()));
        } else {
            throw new IllegalStateException(
                    format("programmer error: fatal error attempting to take offer %s with no reason from server",
                            offer.getId()));
        }
    }

    private void logRequestResult(AtomicReference<TakeOfferReply> resultHandler,
                                  AtomicReference<Throwable> errorHandler,
                                  Supplier<Boolean> isReplyReceived) {
        if (isReplyReceived.get()) {
            if (resultHandler.get() != null)
                log.info("The takeoffer request returned new trade: {}.",
                        resultHandler.get().getTrade().getTradeId());
            else
                log.warn("The takeoffer request returned error: {}.",
                        errorHandler.get().getMessage());
        } else {
            log.error("The takeoffer request failed: no reply received within the {} second deadline.",
                    takeOfferRequestDeadlineInSec);
        }
    }

    private void logNextTakeOfferAttemptAndWait(int attemptCount) {
        // Take care to not let bots exceed call rate limit on mainnet.
        log.info("The takeoffer {} request attempt #{} will be made in {} seconds.",
                offer.getId(),
                attemptCount + 1,
                attemptDelayInSec);
        try {
            SECONDS.sleep(attemptDelayInSec);
        } catch (InterruptedException ignored) {
            // empty
        }
    }

    private void logCurrentTakeOfferAttempt(int attemptCount) {
        log.info("{} taking {} / {} offer {}.  Attempt # {}.",
                botDescription,
                offer.getDirection(),
                offer.getCounterCurrencyCode(),
                offer.getId(),
                attemptCount);
    }

    private final Predicate<AtomicReference<TakeOfferReply>> isSuccessfulTakeOfferRequest = (resultHandler) -> {
        var takeOfferReply = resultHandler.get();
        if (takeOfferReply.hasTrade()) {
            try {
                log.info("Created trade {}.  Allowing 5s for trade prep before continuing.",
                        takeOfferReply.getTrade().getTradeId());
                SECONDS.sleep(5);
            } catch (InterruptedException ignored) {
                // empty
            }
            return true;
        } else {
            return false;
        }
    };

    private final Predicate<AvailabilityResultWithDescription> isTakeOfferAttemptErrorNonFatal = (reason) -> {
        if (reason != null) {
            return this.getBotClient().takeOfferFailedForOneOfTheseReasons(reason,
                    PRICE_OUT_OF_TOLERANCE,
                    UNCONF_TX_LIMIT_HIT);
        } else {
            return false;
        }
    };
}
