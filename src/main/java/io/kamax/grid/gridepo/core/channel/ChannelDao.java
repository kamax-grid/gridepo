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

package io.kamax.grid.gridepo.core.channel;

import io.kamax.grid.gridepo.core.ChannelID;

public class ChannelDao {

    private long sid;
    private ChannelID id;

    public ChannelDao() {
    }

    public ChannelDao(ChannelID id) {
        setId(id);
    }

    public ChannelDao(long sid, ChannelID id) {
        setSid(sid);
        setId(id);
    }

    public long getSid() {
        return sid;
    }

    public void setSid(long sid) {
        this.sid = sid;
    }

    public ChannelID getId() {
        return id;
    }

    public void setId(ChannelID id) {
        this.id = id;
    }

}
