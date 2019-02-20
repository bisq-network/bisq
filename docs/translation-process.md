# Translation Process

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
