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

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.exception.ObjectNotFoundException;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UIAuthSession {

    String getId();

    Instant createdAt();

    Set<String> getCompletedStages();

    List<UIAuthFlow> getFlows();

    Optional<JsonObject> findParameters(String stage);

    default JsonObject getParameters(String stage) throws ObjectNotFoundException {
        return findParameters(stage).orElseThrow((() -> new ObjectNotFoundException("No parameters for stage " + stage)));
    }

    default boolean complete(JsonObject document) {
        return complete(GsonUtil.getStringOrThrow(document, "type"), document);
    }

    boolean complete(String stage, JsonObject data);

    UIAuthStage getStage(String id);

    boolean isAuthenticated();

}
