package io.kamax.grid.gridepo.network.grid.http.handler.matrix.home.client;

import io.kamax.grid.gridepo.Gridepo;
import io.kamax.grid.gridepo.http.handler.Exchange;
import io.kamax.grid.gridepo.network.matrix.http.handler.ClientApiHandler;

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
