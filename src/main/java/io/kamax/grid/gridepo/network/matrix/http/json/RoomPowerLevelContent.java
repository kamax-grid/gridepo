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

package io.kamax.grid.gridepo.network.matrix.http.json;

import java.util.HashMap;
import java.util.Map;

public class RoomPowerLevelContent {

    private Long ban;
    private Long invite;
    private Long kick;
    private Long stateDefault;
    private Long eventsDefault;
    private Long usersDefault;
    private Map<String, Long> users = new HashMap<>();

    public Long getBan() {
        return ban;
    }

    public void setBan(Long ban) {
        this.ban = ban;
    }

    public Long getInvite() {
        return invite;
    }

    public void setInvite(Long invite) {
        this.invite = invite;
    }

    public Long getKick() {
        return kick;
    }

    public void setKick(Long kick) {
        this.kick = kick;
    }

    public Long getStateDefault() {
        return stateDefault;
    }

    public void setStateDefault(Long stateDefault) {
        this.stateDefault = stateDefault;
    }

    public Long getEventsDefault() {
        return eventsDefault;
    }

    public void setEventsDefault(Long eventsDefault) {
        this.eventsDefault = eventsDefault;
    }

    public Long getUsersDefault() {
        return usersDefault;
    }

    public void setUsersDefault(Long usersDefault) {
        this.usersDefault = usersDefault;
    }

    public Map<String, Long> getUsers() {
        return users;
    }

    public void setUsers(Map<String, Long> users) {
        this.users = users;
    }

}
