#!/usr/bin/env bash

cd $(dirname $0)
tx pull -l de,el_GR,es,hu_HU,pt_BR,ro,ru,sr,zh_CN,vi,th_TH,fa,fr

translations="translations/bisq-desktop.displaystringsproperties"
i18n="src/main/resources/i18n"

mv "$translations/de.properties" "$i18n/displayStrings_de.properties"
mv "$translations/el_GR.properties" "$i18n/displayStrings_el.properties"
mv "$translations/es.properties" "$i18n/displayStrings_es.properties"
mv "$translations/hu_HU.properties" "$i18n/displayStrings_hu.properties"
mv "$translations/pt_BR.properties" "$i18n/displayStrings_pt.properties"
mv "$translations/ro.properties" "$i18n/displayStrings_ro.properties"
mv "$translations/ru.properties" "$i18n/displayStrings_ru.properties"
mv "$translations/sr.properties" "$i18n/displayStrings_sr.properties"
mv "$translations/zh_CN.properties" "$i18n/displayStrings_zh.properties"
mv "$translations/vi.properties" "$i18n/displayStrings_vi.properties"
mv "$translations/th_TH.properties" "$i18n/displayStrings_th.properties"
mv "$translations/fa.properties" "$i18n/displayStrings_fa.properties"
mv "$translations/fr.properties" "$i18n/displayStrings_fr.properties"

rm -rf $translations
