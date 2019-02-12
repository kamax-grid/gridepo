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

package io.kamax.grid.gridepo;

import io.kamax.grid.gridepo.config.GridepoConfig;
import io.kamax.grid.gridepo.http.MonolithHttpGridepo;
import io.kamax.grid.gridepo.util.YamlConfigLoader;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

public class MonolithHttpGridepoApplication {

    public static void main(String[] args) {
        System.out.println("------------- Gridepo starting -------------");

        try {
            GridepoConfig cfg = null;

            Iterator<String> argsIt = Arrays.asList(args).iterator();
            while (argsIt.hasNext()) {
                String arg = argsIt.next();
                if (StringUtils.isBlank(arg)) {
                    continue;
                }

                if (StringUtils.equals("-c", arg)) {
                    String cfgFile = argsIt.next();
                    cfg = YamlConfigLoader.loadFromFile(cfgFile);
                    System.out.println("Loaded configuration from " + cfgFile);
                } else {
                    System.out.println("Invalid argument: " + arg);
                    System.exit(1);
                }
            }

            if (Objects.isNull(cfg)) {
                String cfgFile = StringUtils.defaultIfBlank(System.getenv("GRIDEPO_CONFIG_FILE"), "gridepo.yaml");
                cfg = YamlConfigLoader.tryLoadFromFile(cfgFile).orElseGet(GridepoConfig::new);
            }

            MonolithHttpGridepo g = new MonolithHttpGridepo(cfg);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("------------- Gridepo stopping -------------");
                g.stop();
                System.out.println("------------- Gridepo stopped -------------");
            }));

            g.start();
            System.out.println("------------- Gridepo started -------------");
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

}
