# Bisq

[![Build Status](https://travis-ci.org/bisq-network/bisq.svg?branch=master)](https://travis-ci.org/bisq-network/bisq)


## What is Bisq?

Bisq is a safe, private and decentralized way to exchange bitcoin for national currencies and other digital assets. Bisq uses peer-to-peer networking and multi-signature escrow to facilitate trading without a third party. Bisq is non-custodial and incorporates a human arbitration system to resolve disputes.

To learn more, see the doc and video at https://bisq.network/intro.


## Get started using Bisq

Follow the step-by-step instructions at https://bisq.network/get-started.


## Contribute to Bisq

Bisq currently requires JDK 10 . See the scripts directory for scripts that can be used to install and configure the JDK automatically.

TIP: If you are on MacOS, run the script with this command . scripts/install_java.sh.

If you prefer not to run scripts or change your default java, you can use Adoptopenjdk https://adoptopenjdk.net/archive.html. Just untar it where you like, and set java home when running gradle. for example: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-10.0.2+13/Contents/Home ./gradlew clean build



See [CONTRIBUTING.md](CONTRIBUTING.md) and the [developer docs](docs#readme).
