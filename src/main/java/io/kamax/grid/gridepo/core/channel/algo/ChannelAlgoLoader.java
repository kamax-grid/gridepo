package io.kamax.grid.gridepo.core.channel.algo;

import java.util.Optional;
import java.util.function.Function;

public interface ChannelAlgoLoader extends Function<String, Optional<ChannelAlgo>> {

}
