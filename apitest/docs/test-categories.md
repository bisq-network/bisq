# API Test Categories

This guide describes the categorization of tests.

## Method Tests

A `method` test is the `apitest` analog of a unit test.  It tests a single API method such as `getbalance`, but is not
considered a unit test because the code execution path traverses so many layers:  from `gRPC` client -> `gRPC` server
side service -> one or more Bisq `core` services, and back to the client.

Method tests have direct access to `gRPC` client stubs, and test asserts are made directly on `gRPC` return values --
Java Objects.

All `method` tests are part of the `bisq.apitest.method` package.

## Scenario Tests

A `scenario` test is a narrow or broad functional test case covering a simple use case such as funding a wallet to a
complex series of trades.  Generally, a scenario test case requires multiple `gRPC` method calls.

Scenario tests have direct access to `gRPC` client stubs, and test asserts are made directly on `gRPC` return values --
Java Objects.

All `scenario` tests are part of the `bisq.apitest.scenario` package.

## End to End Tests

An end to end (`e2e`) test can cover a narrow or broad use case, and all client calls go through the `CLI` shell script
`bisq-cli`.  End to end tests do not have access to `gRPC` client stubs, and test asserts are made on what the end
user sees on the console -- what`gRPC CLI` prints to `STDOUT`.

As test coverage grows, stable scenario test cases should be migrated to `e2e` test cases.

All `e2e` tests are part of the `bisq.apitest.e2e` package.

