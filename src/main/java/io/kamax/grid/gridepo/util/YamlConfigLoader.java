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

package io.kamax.grid.gridepo.util;

import io.kamax.grid.gridepo.config.GridepoConfig;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;

public class YamlConfigLoader {

    private YamlConfigLoader() {
        // only static
    }

    public static GridepoConfig loadFromFile(String path) throws IOException {
        Representer rep = new Representer();
        rep.getPropertyUtils().setAllowReadOnlyProperties(true);
        rep.getPropertyUtils().setSkipMissingProperties(true);
        Yaml yaml = new Yaml(new Constructor(GridepoConfig.class), rep);
        Object o = yaml.load(new FileInputStream(path));
        return GsonUtil.get().fromJson(GsonUtil.get().toJson(o), GridepoConfig.class);
    }

    public static Optional<GridepoConfig> tryLoadFromFile(String path) {
        try {
            return Optional.of(loadFromFile(path));
        } catch (FileNotFoundException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
