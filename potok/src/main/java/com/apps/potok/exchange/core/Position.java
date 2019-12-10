package com.apps.potok.exchange.core;

import com.apps.potok.exchange.mkdata.Route;
import com.apps.potok.soketio.model.execution.CloseShortPosition;
import com.apps.potok.soketio.model.execution.Execution;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.apps.potok.exchange.mkdata.Route.BUY;

//Non blocking position
public class Position {

    private final UUID uuid;
    private final Date createdTimestamp;
    private final String symbol;
    private final String accountId;
    private final AtomicInteger volume;
    private final Route route;
    private final Integer blockedPrice;
    private final ConcurrentHashMap<Integer, AtomicInteger> buyPriceValueAggregation;
    private final ConcurrentHashMap<UUID, Execution> buyExecutions;
    private final ConcurrentHashMap<Integer, AtomicInteger> sellPriceValueAggregation;
    private final ConcurrentHashMap<UUID, Execution> sellExecutions;
    private final ConcurrentHashMap<UUID, CloseShortPosition> closeShort;

    public Position(Execution execution) {
        this.uuid = UUID.randomUUID();
        this.createdTimestamp = new Date();
        this.symbol = execution.getSymbol();
        this.accountId = execution.getAccountId();
        this.route = execution.getRoute();
        this.buyPriceValueAggregation = new ConcurrentHashMap<>();
        this.buyExecutions = new ConcurrentHashMap<>();
        this.sellPriceValueAggregation = new ConcurrentHashMap<>();
        this.sellExecutions = new ConcurrentHashMap<>();
        this.closeShort = new ConcurrentHashMap<>();
        this.blockedPrice = execution.getBlockedPrice();
        this.volume = new AtomicInteger(0);
        applyExecution(execution);
    }

    public void applyExecution (Execution execution) {
        if(!buyExecutions.containsKey(execution) && !sellExecutions.containsKey(execution)) {
            if(BUY.equals(execution.getRoute())) {
                applyBuyExecution(execution);
            } else {
                applySellShortExecution(execution);
            }
        }
    }

    private void applyBuyExecution (Execution execution) {
        AtomicInteger newVolume = new AtomicInteger(execution.getQuantity());
        AtomicInteger existingVolume = buyPriceValueAggregation.putIfAbsent(execution.getFillPrice(), newVolume);
        if(existingVolume != null) {
            existingVolume.addAndGet(newVolume.get());
        }
        buyExecutions.put(execution.getExecutionUuid(), execution);
        volume.getAndAdd(execution.getQuantity());
    }


    private void applySellShortExecution(Execution execution) {
        AtomicInteger newVolume = new AtomicInteger(execution.getQuantity());
        AtomicInteger existingVolume = sellPriceValueAggregation.putIfAbsent(execution.getFillPrice(), newVolume);
        if(existingVolume != null) {
            existingVolume.addAndGet(newVolume.get());
        }
        sellExecutions.put(execution.getExecutionUuid(), execution);
        volume.getAndAdd(-execution.getQuantity());
    }

    public void closeShort(CloseShortPosition closeShortPosition, Position positivePosition){
        this.closeShort.put(closeShortPosition.getUuid(), closeShortPosition);
        positivePosition.closeShort.put(closeShortPosition.getUuid(), closeShortPosition);
        this.volume.getAndAdd(closeShortPosition.getAmount());
        positivePosition.volume.getAndAdd(-closeShortPosition.getAmount());
        closeShortPosition.setPositivePosition(positivePosition.getUuid());
        closeShortPosition.setShortPosition(this.uuid);
    }

    public Double calculateWeightedAveragePrice() {
        double weight = 0d, volume = 0d;
        for(Map.Entry<Integer, AtomicInteger> entry : buyPriceValueAggregation.entrySet()) {
            long tierPrice = entry.getKey();
            long tierVolume = entry.getValue().get();
            weight += tierPrice * tierVolume;
            volume += tierVolume;
        }
        if(volume == 0){
            return 0d;
        } else {
            return weight/volume;
        }
    }

    public Double calculateAveragePerformance() {
        //todo think, implement
        return 0d;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Date getCreatedTimestamp() {
        return createdTimestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getAccountId() {
        return accountId;
    }

    public Route getRoute() {
        return route;
    }

    public Integer getBlockedPrice() {
        return blockedPrice;
    }

    public Integer getVolume() {
        return volume.get();
    }

    public ConcurrentHashMap<UUID, Execution> getBuyExecutions() {
        return buyExecutions;
    }

    public ConcurrentHashMap<UUID, Execution> getSellExecutions() {
        return sellExecutions;
    }

    public ConcurrentHashMap<Integer, AtomicInteger> getBuyPriceValueAggregation() {
        return buyPriceValueAggregation;
    }

    public ConcurrentHashMap<Integer, AtomicInteger> getSellPriceValueAggregation() {
        return sellPriceValueAggregation;
    }

}