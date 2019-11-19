# Translation Process

### Overview

Bisq is offered in multiple languages. Translations are managed with Transifex online software.

User facing texts **must not** be hardcoded in the source code files.

English is the base language for translations.

### Adding or changing English texts

Being a base language English is managed directly in the project.

Edit `core/src/main/resources/i18n/displayStrings.properties` to add or modify English texts.
Mind the chapters. Fit your entry properly.

Once your changes get merged the Transifex will sync itself with GitHub.

However, **do not** edit files for other languages. They are synced from Transifex.

### Adding or changing texts in other languages (non-English)

All translations are managed with [Transifex](https://www.transifex.com/).
Sign up, find `bisq-desktop` project, and join the team.
Once accepted, you will be able to edit translations.

The entry must exist in English to be available for translation. 

To make translations effective in the project you need to install the Transifex client and run the script to pull the updates.
This is described below.

### Installing the Transifex client command-line tool

You'll find a detailed guide [how to install the Transifex client](https://docs.transifex.com/client/installing-the-client) on the Transifex page. 

The Transifex Bisq project config file is included as part of the repository. It can be found at `core/.tx/config`.

### Synchronising translations

We've prepared a script to update the translation files with the Transifex client command-line tool.

 * Run [update_translations.sh](https://github.com/bisq-network/bisq/blob/master/core/update_translations.sh)
 
Synchronization output expected:
 
 * All translation files in [i18n directory](https://github.com/bisq-network/bisq/blob/master/core/src/main/resources/i18n) have been updated.

Go over the changes if there are any obvious issues (Transifex had once a problem which caused a rewrite of certain keys)
and commit and push them to master.
