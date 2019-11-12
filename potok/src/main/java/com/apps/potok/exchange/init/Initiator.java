package com.apps.potok.exchange.init;


import com.apps.potok.exchange.core.AskComparator;
import com.apps.potok.exchange.core.Order;
import com.apps.potok.exchange.core.SymbolContainer;
import com.apps.potok.exchange.mkdata.Route;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import static com.apps.potok.exchange.mkdata.Route.BUY;

@Service
public class Initiator {

    private SymbolContainer symbolContainer;

    public Initiator(SymbolContainer symbolContainer) {
        this.symbolContainer = symbolContainer;
    }

    public void initiateContainer (int size, ConcurrentHashMap<String, ConcurrentSkipListMap<Integer, ConcurrentLinkedDeque<String>>> orderContainer, Route route){
        for(Order order : getOrders(size, route)){
            insertOrder(orderContainer, order, route);
        }
    }

    private void insertOrder(ConcurrentHashMap<String, ConcurrentSkipListMap<Integer, ConcurrentLinkedDeque<String>>> allOrderContainer, Order order, Route route) {
        ConcurrentSkipListMap<Integer, ConcurrentLinkedDeque<String>> symbolOrderContainer = allOrderContainer.get(order.getSymbol());

        if(symbolOrderContainer == null){
            if(BUY.equals(route)){
                symbolOrderContainer = new ConcurrentSkipListMap(new AskComparator());
            } else {
                symbolOrderContainer = new ConcurrentSkipListMap();
            }
            allOrderContainer.put(order.getSymbol(), symbolOrderContainer);
        }

        insertPrice(symbolOrderContainer, order);
    }

    private void insertPrice(ConcurrentSkipListMap<Integer, ConcurrentLinkedDeque<String>> symbolOrderContainer, Order order) {
        ConcurrentLinkedDeque<String> customerContainer = symbolOrderContainer.get(order.getVal());

        if(customerContainer == null){
            customerContainer = new ConcurrentLinkedDeque();
            symbolOrderContainer.put(order.getVal(), customerContainer);
        }

        customerContainer.add(order.getAccount());

    }

    private List<Order> getOrders(int size, Route route) {
        List<String> symbols = symbolContainer.getSymbols();
        List<String> customers = getCustomers(size);
        List<Order> orders = new ArrayList<>();
        for(int i = 0 ; i < size; i++){
            String customer = customers.get(RandomUtils.nextInt(0, customers.size()));
            String symbol = symbols.get(RandomUtils.nextInt(0, symbols.size()));
            Integer val = getVal(symbol, route);
            Integer volume = RandomUtils.nextInt(0, 100) * 10;
            orders.add(new Order(symbol, customer, route, val, volume));
        }
        return orders;
    }

    private List<String> getCustomers(int size) {
        List<String> customers = new ArrayList<>();

        for(int i = 0 ; i < size/5 ; i++){
            customers.add(RandomStringUtils.randomAlphabetic(3) + " " + RandomStringUtils.randomAlphabetic(5));
        }

        return customers;
    }

    private Integer getVal(String symbol, Route route) {
        Integer val = symbolContainer.getQuote(symbol);
        if(BUY.equals(route)){
            return RandomUtils.nextInt(1, val);
        } else {
            return RandomUtils.nextInt(val - 1, 100);
        }
    }
}
