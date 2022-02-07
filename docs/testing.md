# Bisq Testing Guide

This guide describes the testing process performed prior to each release.

## Prerequisites

In order to take part in the testing process, you will need to do the following:

- Build Bisq from source (see [build.md](build.md))
- Setup a development/testing environment (see [Makefile](../Makefile) or [dev-setup.md](dev-setup.md))
- Request access to [TestPad](https://bisq.ontestpad.com) (our test management tool)

## Communication Channels

If you would like to discuss and/or contribute to Bisq's testing effort, join us in the #testing channel within the [Bisq Matrix Space](https://bisq.chat).
Here you could also request access to TestPad (https://bisq.ontestpad.com).

## Compensation

Testing activities are eligible for [compensation](https://bisq.wiki/Making_a_compensation_request).
When submitting a compensation request, please include links to artifacts on TestPad (results/reports) indicating the activities that were performed (e.g. tests that were executed), as well as any bugs that were discovered and entered as a result of testing.

## Testing Process

[TestPad](https://bisq.ontestpad.com) is used to manage and track the manual testing process.
For specific usage or functionality of TestPad, please see the flash card introduction within TestPad.

### Definitions

Some definitions within the context of TestPad and how they apply to our specific testing process:

- **Project:** Defines a particular testing scope with relevant tests.
- **Script:** Each script is a collection of related tests that are intended to be used to test a particular component.
- **Folder:** Defines a group of scripts for each release.

### Test Structure

Tests are written using Behaviour-Driven Development (BDD) style syntax (given/when/then).
- **Given:** This states the preconditions that are assumed for the test. It is not a test step (one that requires a result to be recorded), but instead you must ensure the preconditions are satisfied in order to perform the test.
- **When:** This states the actions to be performed for the test. This also does not require a result to be recorded.
- **Then:** This states the expected results of the test. This requires a result to be recorded.

### Testing Workflow

Once logged in to TestPad, select the `Desktop Client` project from the left navigation menu.

Each upcoming release will have a new folder created with applicable scripts that need to be executed.

#### Executing a Script

Test runs allow for tracking the results of test execution. Each script may have several test runs created in order to perform the tests on different environments (e.g. operating systems) and assigned to different people. An overview of all test runs for the release can be observed from the main project view, which allows you to quickly find test runs assigned to yourself.

To execute a test run:

1. Open the script to be executed.

1. Hover over the applicable test run column and select the play button to start executing the test run.

1. Follow the script and perform each test.

  - Select a status for each test. Select from one of the following statuses:

    - **Pass:** the test has passed successfully.
    - **Fail:** there is an issue (defect) related to the test.
    - **Blocked:** the test cannot be performed for a particular reason.
    - **Query:** you are unsure about the test and require further information.
    - **Exclude:** the test does not need to be performed for a particular reason.

  - If necessary, use the `Comments` field to add any comments, notes and actual test results. This is especially beneficial to provide details if a test did not pass.

  - If applicable, link an existing or create a new issue (defect) if it was found during the test run execution.

### Creating Issues

When creating issues, it is important to provide sufficient information describing the problem encountered. In addition to a clear and concise description, this may include attaching screenshots or log files if necessary so the assigned developer can identify and resolve the issue.

### Testing Tips

- **Test from a new users perspective.** In addition to looking for obvious errors, be on the lookout for any usability or workflow concerns.

- **Reset the "don't show again" flags.** This will allow you to verify the popup messages are valid and appropriate.
