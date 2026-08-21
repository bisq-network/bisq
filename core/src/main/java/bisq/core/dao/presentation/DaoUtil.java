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

package bisq.core.dao.presentation;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.model.governance.DaoPhase;
import bisq.core.locale.Res;
import bisq.core.util.FormattingUtils;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.Locale;

/**
 * Util class for shared presentation code.
 */
public class DaoUtil {

    public static String getNextPhaseDuration(int height, DaoPhase.Phase phase, DaoFacade daoFacade) {
        final int currentCycleDuration = daoFacade.getCurrentCycleDuration();
        long start = daoFacade.getFirstBlockOfPhaseForDisplay(height, phase) + currentCycleDuration;
        long end = daoFacade.getLastBlockOfPhaseForDisplay(height, phase) + currentCycleDuration;

        long now = new Date().getTime();
        SimpleDateFormat dateFormatter = new SimpleDateFormat("dd MMM", Locale.getDefault());
        SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String startDateTime = FormattingUtils.formatDateTime(new Date(now + (start - height) * 10 * 60 * 1000L), dateFormatter, timeFormatter);
        String endDateTime = FormattingUtils.formatDateTime(new Date(now + (end - height) * 10 * 60 * 1000L), dateFormatter, timeFormatter);

        return Res.get("dao.cycle.phaseDurationWithoutBlocks", start, end, startDateTime, endDateTime);
    }

    public static String getPhaseDuration(int height, DaoPhase.Phase phase, DaoFacade daoFacade) {
        long start = daoFacade.getFirstBlockOfPhaseForDisplay(height, phase);
        long end = daoFacade.getLastBlockOfPhaseForDisplay(height, phase);
        long duration = daoFacade.getDurationForPhaseForDisplay(phase);
        long now = new Date().getTime();
        SimpleDateFormat dateFormatter = new SimpleDateFormat("dd MMM", Locale.getDefault());
        SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String startDateTime = FormattingUtils.formatDateTime(new Date(now + (start - height) * 10 * 60 * 1000L), dateFormatter, timeFormatter);
        String endDateTime = FormattingUtils.formatDateTime(new Date(now + (end - height) * 10 * 60 * 1000L), dateFormatter, timeFormatter);
        String durationTime = FormattingUtils.formatDurationAsWords(duration * 10 * 60 * 1000, false, false);
        return Res.get("dao.cycle.phaseDuration", duration, durationTime, start, end, startDateTime, endDateTime);
    }
}
