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

package bisq.common.app;

import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.util.FileSize;

public class Log {
    public static final String JAVA_FX_THREAD_NAME = "JavaFX Application Thread";
    private static Logger logbackLogger;
    private static final ThreadLocal<Boolean> SENSITIVE_HTTP = new ThreadLocal<>();
    private static final TurboFilter APACHE_HTTP_FILTER = new TurboFilter() {
        @Override
        public FilterReply decide(Marker marker, Logger logger, Level level, String format,
                                  Object[] params, Throwable t) {
            return suppressHttpLog(logger.getName()) ? FilterReply.DENY : FilterReply.NEUTRAL;
        }
    };
    public static final Level DEFAULT_LOG_LEVEL = Level.INFO;

    public interface HttpLoggingScope extends AutoCloseable {
        @Override
        void close();
    }

    public static synchronized HttpLoggingScope suppressHttpClientLogging() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        if (!context.getTurboFilterList().contains(APACHE_HTTP_FILTER)) {
            APACHE_HTTP_FILTER.start();
            context.getTurboFilterList().add(0, APACHE_HTTP_FILTER);
        }
        java.util.logging.LogManager manager = java.util.logging.LogManager.getLogManager();
        var names = manager.getLoggerNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            java.util.logging.Logger logger = manager.getLogger(name);
            if (logger == null || (!name.isEmpty() && !isApacheHttpLogger(name)))
                continue;
            for (java.util.logging.Handler handler : logger.getHandlers()) {
                if (!(handler.getFilter() instanceof HttpLoggingFilter))
                    handler.setFilter(new HttpLoggingFilter(handler.getFilter()));
            }
        }
        Boolean previous = SENSITIVE_HTTP.get();
        SENSITIVE_HTTP.set(true);
        return () -> {
            if (previous == null)
                SENSITIVE_HTTP.remove();
            else
                SENSITIVE_HTTP.set(previous);
        };
    }

    private static boolean isApacheHttpLogger(String name) {
        return name != null && (name.equals("org.apache.http") || name.startsWith("org.apache.http."));
    }

    private static boolean suppressHttpLog(String name) {
        return Boolean.TRUE.equals(SENSITIVE_HTTP.get()) && isApacheHttpLogger(name);
    }

    private record HttpLoggingFilter(java.util.logging.Filter delegate) implements java.util.logging.Filter {
        @Override
        public boolean isLoggable(java.util.logging.LogRecord record) {
            return !suppressHttpLog(record.getLoggerName()) && (delegate == null || delegate.isLoggable(record));
        }
    }

    public static void setLevel(Level logLevel) {
        logbackLogger.setLevel(logLevel);
    }

    public static void setup(String fileName) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
        appender.setContext(loggerContext);
        appender.setFile(fileName + ".log");

        FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
        rollingPolicy.setContext(loggerContext);
        rollingPolicy.setParent(appender);
        rollingPolicy.setFileNamePattern(fileName + "_%i.log");
        rollingPolicy.setMinIndex(1);
        rollingPolicy.setMaxIndex(20);
        rollingPolicy.start();

        SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
        triggeringPolicy.setMaxFileSize(FileSize.valueOf("10MB"));
        triggeringPolicy.start();

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{MMM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{15}: %msg %xEx%n");
        encoder.start();

        appender.setEncoder(encoder);
        appender.setRollingPolicy(rollingPolicy);
        appender.setTriggeringPolicy(triggeringPolicy);
        appender.start();

        logbackLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logbackLogger.addAppender(appender);
        logbackLogger.setLevel(DEFAULT_LOG_LEVEL);
    }

    public static void setCustomLogLevel(String pattern, Level logLevel) {
        ((Logger) LoggerFactory.getLogger(pattern)).setLevel(logLevel);
    }

    public static String pushCustomLogLevel(String pattern, String logLevel) {
        Level oldLevel = ((Logger) LoggerFactory.getLogger(pattern)).getLevel();
        ((Logger) LoggerFactory.getLogger(pattern)).setLevel(Level.toLevel(logLevel));
        return oldLevel == null ? "INFO" : oldLevel.toString();
    }

    public static void filterByThreadName(String threadName) {
        logbackLogger.getLoggerContext().addTurboFilter(new TurboFilter() {
            @Override
            public FilterReply decide(Marker marker, Logger logger, Level level, String format,
                                      Object[] params, Throwable t) {
                return threadName.equals(Thread.currentThread().getName()) ?
                        FilterReply.ACCEPT : FilterReply.DENY;
            }
        });
    }

    public static void clearFilters() {
        logbackLogger.getLoggerContext().resetTurboFilterList();
    }
}
