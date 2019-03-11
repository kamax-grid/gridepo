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

package io.kamax.grid.gridepo.core.channel.event;

import org.apache.commons.lang3.StringUtils;

public enum ChannelEventType {

    Address("g.c.address"),
    Alias("g.c.alias"),
    Create("g.c.create"),
    JoinRules("g.c.join_rules"),
    Member("g.c.member"),
    Message("g.c.message"),
    Name("g.c.name"),
    Power("g.c.power"),
    Topic("g.c.topic");

    private String id;

    ChannelEventType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public boolean match(String id) {
        return StringUtils.equals(this.id, id);
    }

}
