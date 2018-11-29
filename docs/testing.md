# Bisq Testing Guide

This guide describes the testing process performed prior to each release.

## Prerequisites

In order to take part in the testing process, you will need to do the following:

- Build Bisq from source (see [build.md](build.md))
- Setup a development/testing environment (see [dev-setup.md](dev-setup.md))
- Register an account with [TestQuality](https://bisq.testquality.com)

  _**Note:** Once you have registered an account, or if you have trouble registering, contact us in Slack (see below) to be granted permissions to execute test runs._

## Communication Channels

If you would like to discuss and/or contribute to Bisq's testing effort, join us in the [#testing](https://bisq.slack.com/messages/CEBLT79ML) channel within the [Bisq Slack workspace](https://bisq.network/slack-invite).

## Compensation

Testing activities are eligible for [compensation](https://docs.bisq.network/dao/phase-zero.html#how-to-request-compensation). When submitting a compensation request, please include links to artifacts on TestQuality indicating the activities that were performed (e.g. test plan runs that were executed), as well as any bugs that were discovered and entered as a result of testing.

## Testing Process

[TestQuality](https://bisq.testquality.com) is used to manage and track the manual testing process. For specific usage or functionality of TestQuality, please see the guided tour or refer to the help content within TestQuality.

### Definitions

Some definitions within the context of TestQuality:

- **Test Case:** a set of conditions under which a tester will determine whether the system under test satisfies requirements or works correctly.
- **Test Suite:** a collection of test cases that are intended to be used to test the system to show that it has some specified set of behaviours.
- **Test Plan:** defines a particular testing scope with testing activities.
- **Test Plan Run:** an occurrence in which a particular test plan is executed.

### Testing Workflow

Once logged in to TestQuality, select the Bisq project from the dashboard.

#### Executing a Test Plan Run

A new test plan run is created every time a test plan needs to be executed. This allows for tracking the history of test plan’s executions.

To execute a test plan run:

1. Open the test plan run to be executed.

1. Select the "Running" state in order to update the start time (used for time tracking purposes).

1. Navigate the test suites and perform each test case.

  - Specify a status for each step or for the overall test case and optionally enter the time spent on each step. Select from one of the following statuses:

    - **Pass:** the test case or step has passed successfully.
    - **Pending:** the test case or step has yet to be performed.
    - **Fail:** there is an issue (defect) related to the test case or step.
    - **Block:** the test case or step is unable to be performed.
    - **Retest:** the test case or step needs to be retested.
    - **Skip:** the test case or step does not need to be performed.

  - If required, use the `Reason for Status` field to add any comments, notes and actual test results. This is especially beneficial to provide details if a test fails.

  - If applicable, link an existing or create a new issue (defect) if it was found during the test plan run execution.

1. Once all test cases within the test plan run have been executed, select the "Complete" state in order to update the end time.

### Creating Issues

When creating issues, it is important to provide sufficient information describing the problem encountered. In addition to a clear and concise description, this may include attaching screenshots or log files if necessary so the assigned developer can identify and resolve the issue.

### Testing Tips

- **Test from a new users perspective.** In addition to looking for obvious errors, be on the look out for any usability or workflow concerns.

- **Reset the "dont show again" flags.** This will allow you to verify the popup messages are valid and appropriate.
