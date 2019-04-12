#!/usr/bin/env bash

cd $(dirname $0)
tx pull -l de,el_GR,es,pt,ru,zh_CN,vi,th_TH,fa,fr

translations="translations/bisq-desktop.displaystringsproperties"
i18n="src/main/resources/i18n"

mv "$translations/de.properties" "$i18n/displayStrings_de.properties"
mv "$translations/el_GR.properties" "$i18n/displayStrings_el.properties"
mv "$translations/es.properties" "$i18n/displayStrings_es.properties"
mv "$translations/pt.properties" "$i18n/displayStrings_pt.properties"
mv "$translations/ru.properties" "$i18n/displayStrings_ru.properties"
mv "$translations/zh_CN.properties" "$i18n/displayStrings_zh.properties"
mv "$translations/vi.properties" "$i18n/displayStrings_vi.properties"
mv "$translations/th_TH.properties" "$i18n/displayStrings_th.properties"
mv "$translations/fa.properties" "$i18n/displayStrings_fa.properties"
mv "$translations/fr.properties" "$i18n/displayStrings_fr.properties"

rm -rf $translations
