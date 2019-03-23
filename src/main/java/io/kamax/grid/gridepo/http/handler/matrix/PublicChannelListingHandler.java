package io.kamax.grid.gridepo.http.handler.matrix;

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.http.handler.Exchange;

public class PublicChannelListingHandler extends ClientApiHandler {

    private final Gridepo g;

    public PublicChannelListingHandler(Gridepo g) {
        this.g = g;
    }

    @Override
    protected void handle(Exchange exchange) {
        exchange.respondJson("{\"chunk\":[],\"next_batch\":\"\",\"prev_batch\":\"\",\"total_room_count_estimate\":0}");
    }

}
