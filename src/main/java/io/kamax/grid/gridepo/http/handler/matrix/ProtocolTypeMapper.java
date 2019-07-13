package io.kamax.grid.gridepo.http.handler.matrix;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class ProtocolTypeMapper {

    private static BiMap<String, String> authTypes = HashBiMap.create();
    private static BiMap<String, String> idTypes = HashBiMap.create();

    static {
        authTypes.put("g.auth.password", "m.login.password");
        idTypes.put("g.id.username", "m.id.user");
    }

    public static class GridToMatrix {

        public String mapAuth(String gridType) {
            return authTypes.get(gridType);
        }

    }

    public static class MatrixToGrid {

        public String mapAuth(String mxType) {
            return authTypes.inverse().get(mxType);
        }

        public String mapId(String mxType) {
            return idTypes.inverse().get(mxType);
        }
    }

    private static GridToMatrix g2m = new GridToMatrix();
    private static MatrixToGrid m2g = new MatrixToGrid();

    public static GridToMatrix asGrid() {
        return g2m;
    }

    public static MatrixToGrid asMatrix() {
        return m2g;
    }

}
