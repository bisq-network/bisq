/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.app;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import org.slf4j.LoggerFactory;

public class Log {
    public static boolean PRINT_TRACE_METHOD = true;
    private static SizeBasedTriggeringPolicy triggeringPolicy;
    private static Logger logbackLogger;

    public static void setup(String fileName, boolean releaseVersion) {
        Log.PRINT_TRACE_METHOD = !releaseVersion;
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        RollingFileAppender appender = new RollingFileAppender();
        appender.setContext(loggerContext);
        appender.setFile(fileName + ".log");

        FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
        rollingPolicy.setContext(loggerContext);
        rollingPolicy.setParent(appender);
        rollingPolicy.setFileNamePattern(fileName + "_%i.log");
        rollingPolicy.setMinIndex(1);
        rollingPolicy.setMaxIndex(10);
        rollingPolicy.start();

        triggeringPolicy = new SizeBasedTriggeringPolicy();
        triggeringPolicy.setMaxFileSize(releaseVersion ? "1MB" : "50MB");
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
        logbackLogger.setLevel(releaseVersion ? Level.DEBUG : Level.TRACE);
        logbackLogger.addAppender(appender);
    }

    public static void traceCall() {
        StackTraceElement stackTraceElement = new Throwable().getStackTrace()[1];
        String methodName = stackTraceElement.getMethodName();
        if (methodName.equals("<init>"))
            methodName = "Constructor ";
        String className = stackTraceElement.getClassName();
        LoggerFactory.getLogger(className).trace("Called: {}", methodName);
    }

    public static void traceCall(String message) {
        StackTraceElement stackTraceElement = new Throwable().getStackTrace()[1];
        String methodName = stackTraceElement.getMethodName();
        if (methodName.equals("<init>"))
            methodName = "Constructor ";
        String className = stackTraceElement.getClassName();
        LoggerFactory.getLogger(className).trace("Called: {} [{}]", methodName, message);
    }


}
