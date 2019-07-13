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

package io.kamax.grid.gridepo.config;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UIAuthConfig {

    public class Flow {

        private List<String> stages = new ArrayList<>();

        public List<String> getStages() {
            return stages;
        }

        public void setStages(List<String> stages) {
            this.stages = stages;
        }

        public void addStage(String stageId) {
            getStages().add(stageId);
        }

    }

    private List<Flow> flows = new ArrayList<>();
    private Map<String, JsonObject> parameters = new HashMap<>();

    public List<Flow> getFlows() {
        return flows;
    }

    public void setFlows(List<Flow> flows) {
        this.flows = flows;
    }

    public Flow addFlow() {
        Flow f = new Flow();
        getFlows().add(f);
        return f;
    }

    public Map<String, JsonObject> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, JsonObject> parameters) {
        this.parameters = parameters;
    }

}
