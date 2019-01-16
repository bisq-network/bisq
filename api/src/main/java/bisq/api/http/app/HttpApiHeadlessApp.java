package bisq.api.http.app;

import bisq.core.app.BisqHeadlessApp;

import bisq.common.setup.UncaughtExceptionHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * BisqHeadlessApp implementation for HttpApi.
 * This is only used in case of the headless version to startup Bisq.
 */
@Slf4j
class HttpApiHeadlessApp extends BisqHeadlessApp implements UncaughtExceptionHandler {
}
