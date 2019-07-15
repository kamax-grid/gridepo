package io.kamax.grid.gridepo.network.grid;

import com.google.gson.JsonObject;
import io.kamax.grid.gridepo.core.auth.UIAuthSession;
import io.kamax.grid.gridepo.core.auth.UIAuthStage;
import io.kamax.grid.gridepo.network.matrix.http.json.UIAuthJson;
import io.kamax.grid.gridepo.util.GsonUtil;

import java.util.Set;
import java.util.stream.Collectors;

public class ProtocolMapper {

    public static JsonObject m2gCredentials(JsonObject matrix) {
        JsonObject grid = matrix.deepCopy();
        grid.add("session", matrix.get("session"));
        grid.addProperty("type", ProtocolTypeMapper.asMatrix().mapAuth(matrix.get("type").getAsString()));
        GsonUtil.findObj(matrix, "identifier").ifPresent(id -> {
            id.addProperty("type", ProtocolTypeMapper.asMatrix().mapId(id.get("type").getAsString()));
            GsonUtil.findString(id, "user").ifPresent(u -> id.addProperty("value", u));
            grid.add("identifier", id);
        });
        return grid;
    }

    public static UIAuthJson g2m(UIAuthSession session) {
        UIAuthJson body = new UIAuthJson();

        body.setSession(session.getId());
        session.getFlows().forEach(flowG -> {
            Set<String> stagesG = flowG.getStages().stream().map(UIAuthStage::getId).collect(Collectors.toSet());
            UIAuthJson.Flow flowM = body.addFlow();
            for (String stageG : stagesG) {
                flowM.addStage(ProtocolTypeMapper.asGrid().mapAuth(stageG));
            }
        });

        return body;
    }

}
