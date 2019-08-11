/*
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

package io.kamax.grid.gridepo.network.grid.http.handler.grid.identity.json;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.core.auth.UIAuthSession;
import io.kamax.grid.gridepo.core.auth.UIAuthStage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UIAuthJson {

    public static UIAuthJson from(UIAuthSession session) {
        UIAuthJson json = new UIAuthJson();
        json.setSession(session.getId());
        json.getStep().setType("g.logic.or");

        session.getFlows().forEach(f -> {
            for (UIAuthStage s : f.getStages()) {
                if (s.isCompleted()) {
                    continue;
                }

                json.getStep().getChoices().add(s.getId());
                session.findParameters(s.getId()).ifPresent(p -> json.getParams().put(s.getId(), p));
            }
        });

        return json;
    }

    public static class Step {

        private String type;
        private List<String> choices = new ArrayList<>();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<String> getChoices() {
            return choices;
        }

        public void setChoices(List<String> choices) {
            this.choices = choices;
        }

    }

    private String session;
    private Step step = new Step();
    private Map<String, JsonObject> params = new HashMap<>();

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public Step getStep() {
        return step;
    }

    public void setStep(Step step) {
        this.step = step;
    }

    public Map<String, JsonObject> getParams() {
        return params;
    }

    public void setParams(Map<String, JsonObject> params) {
        this.params = params;
    }

}
