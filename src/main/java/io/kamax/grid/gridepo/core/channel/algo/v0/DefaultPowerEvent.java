/*
 * Gridepo - Grid Data Server
 * Copyright (C) 2019 Kamax Sarl
 *
 * https://www.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.grid.gridepo.core.channel.algo.v0;

import io.kamax.grid.gridepo.core.channel.event.BarePowerEvent;

import java.util.Objects;

public class DefaultPowerEvent extends BarePowerEvent {

    public static BarePowerEvent.Content applyDefaults(BarePowerEvent.Content c) {
        if (Objects.isNull(c.getDef().getEvent())) {
            c.getDef().setEvent(0L);
        }

        if (Objects.isNull(c.getDef().getState())) {
            c.getDef().setState(Long.MAX_VALUE);
        }

        if (Objects.isNull(c.getDef().getUser())) {
            c.getDef().setUser(Long.MIN_VALUE);
        }

        if (Objects.isNull(c.getMembership().getBan())) {
            c.getMembership().setBan(Long.MAX_VALUE);
        }

        if (Objects.isNull(c.getMembership().getInvite())) {
            c.getMembership().setInvite(Long.MAX_VALUE);
        }

        if (Objects.isNull(c.getMembership().getKick())) {
            c.getMembership().setKick(Long.MAX_VALUE);
        }

        return c;
    }

    public DefaultPowerEvent() {
        applyDefaults(this.getContent());
    }

}
