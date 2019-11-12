package com.apps.potok.exchange.core;

import com.apps.potok.exchange.mkdata.Route;

import java.util.UUID;

import static java.util.UUID.randomUUID;

public class Order {

    private final UUID uuid;
    private final String symbol;
    private final String account;
    private final Route route;
    private final Integer val;
    private final Integer volume;

    public Order(String symbol, String account, Route route, Integer val, Integer volume) {
        this.uuid = randomUUID();
        this.symbol = symbol;
        this.account = account;
        this.route = route;
        this.val = val;
        this.volume = volume;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getAccount() {
        return account;
    }

    public Route getRoute() {
        return route;
    }

    public Integer getVal() {
        return val;
    }

    public Integer getVolume() {
        return volume;
    }


}
