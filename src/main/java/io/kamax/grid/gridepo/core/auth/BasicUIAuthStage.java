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

package io.kamax.grid.gridepo.core.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class BasicUIAuthStage implements UIAuthStage {

    private String id;
    private Instant completedAt;
    private String uid;

    public BasicUIAuthStage(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isCompleted() {
        return Objects.nonNull(completedAt);
    }

    @Override
    public Instant completedAt() throws IllegalStateException {
        if (!isCompleted()) throw new IllegalStateException("Stage " + getId() + " has not been completed yet");
        return completedAt;
    }

    @Override
    public void completeWith(AuthResult result) {
        completedAt = Instant.now();
        uid = result.getUid();
    }

    @Override
    public Optional<String> getUid() {
        return Optional.ofNullable(uid);
    }

}
