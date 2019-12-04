package com.apps.potok.exchange.core;

import com.apps.potok.exchange.eventhandlers.BalanceNotifierServer;
import com.apps.potok.exchange.eventhandlers.PositionNotifierServer;
import com.apps.potok.exchange.mkdata.Route;
import com.apps.potok.soketio.model.execution.Execution;
import com.apps.potok.soketio.model.order.NewOrder;
import com.apps.potok.exchange.account.Account;
import com.apps.potok.exchange.account.AccountManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.apps.potok.exchange.mkdata.Route.BUY;
import static com.apps.potok.exchange.mkdata.Route.SELL;

@Service
public class OrderManager {

    private final int RISK_FACTOR = 10;

    private final ScheduledExecutorService executorService;
    private final ConcurrentHashMap<UUID, Order> orderPool;
    private final AskContainer askContainer;
    private final BidContainer bidContainer;

    @Autowired
    private SymbolContainer symbolContainer;

    @Autowired
    private AccountManager accountManager;

    @Autowired
    private BalanceNotifierServer balanceNotifier;

    @Autowired
    private PositionNotifierServer positionNotifier;

    public OrderManager(BidContainer bidContainer, AskContainer askContainer) {
        this.askContainer = askContainer;
        this.bidContainer = bidContainer;
        this.orderPool = new ConcurrentHashMap();
        this.executorService = Executors.newScheduledThreadPool(1);
    }

    public Order manageNew(NewOrder newOrder, Account account) {
        Route route = getRoute(newOrder);
        if (SELL.equals(route)) {
            return newSellOrderBalanceProcessor(newOrder, account, route);
        } else {
            return newBuyShortOrderBalanceProcessor(newOrder, account, route);
        }
    }

    private Order newBuyShortOrderBalanceProcessor(NewOrder newOrder, Account account, Route route) {
        long predictedAmount = newOrder.getVolume() * symbolContainer.getQuote(newOrder.getSymbol());
        long balanceRisk = predictedAmount + predictedAmount / RISK_FACTOR;
        long balanceChange = newOrder.getVolume() * newOrder.getVal();
        boolean success = account.doNegativeOrderBalance(balanceRisk, balanceChange);
        if (success) {
            balanceNotifier.pushBalance(account);
            return createOrder(newOrder, account, route);
        } else {
            return null;
        }
    }

    //todo it is not thread safe for account with many client connections, sell orders could change, needs to be fixed, could
    private Order newSellOrderBalanceProcessor(NewOrder newOrder, Account account, Route route) {
        long existingSellOrder = account.getExistingSellOrderVolume(newOrder.getSymbol());
        long existingPositivePosition = account.getExistingPositivePositionVolume(newOrder.getSymbol());
        long existingPositionDiff = existingPositivePosition - existingSellOrder;
        long newPositionDiff = existingPositionDiff - newOrder.getVolume();
        if (newPositionDiff < 0) {
            return null; //disallow short on sell.
        } else { // closing existing position
            return createOrder(newOrder, account, route);
        }
    }

    public Order addOrder(Order order) {
        Account account = accountManager.getAccount(order.getAccount());
        account.addOrder(order);
        orderPool.put(order.getUuid(), order);
        return order;
    }

    // returns removed order, returns null if order is already executed or not found.
    // BUY/SHORT balance on cancel should be returned assuming that it could be executed by other threads so be careful.
    public Order cancelOrder(UUID uuid, String accountId) {
        Account account = accountManager.getAccount(accountId);
        Order orderToRemove = account.getOrder(uuid);
        if (orderToRemove != null && orderToRemove.getAccount().equals(accountId)) {
            orderToRemove.cancel();
            if (BUY.equals(orderToRemove.getRoute())) {
                if (askContainer.removeAsk(orderToRemove)) {
                    return orderToRemove;
                }
            } else {
                if (bidContainer.removeBid(orderToRemove)) {
                    return orderToRemove;
                }
            }
        }
        executorService.schedule(createCancelOrderBalanceReturnTask(orderToRemove, account), 10, TimeUnit.SECONDS);
        return null;
    }

    public Order manageExecution(Execution execution) {
        Account account = accountManager.getAccount(execution.getAccountId());
        Order order = account.getOrder(execution.getOrderUuid());
        if (SELL.equals(order.getRoute())) {
            sellExecutionBalanceProcessor(execution, account);
        } else {
            buyShortExecutionBalanceProcessor(execution, order, account);
        }
        Position position = account.doExecution(execution);
        positionNotifier.pushPositionNotification(position.getAccountId(), position.getSymbol(), position.getRoute());
        // should be done for down stream processing persistance, accounting, transaction journalization and balance update.
        return order;
    }

    // Buy Short Execution could happen only at same or better price, the abs difference we return to account balance.
    // Example of buying 1 share:
    // |route	| order	| execution	| return absolute	|
    // |buy 	| 40	| 30		| 10				|
    // |short	| 20	| 40		| 20				|

    private void buyShortExecutionBalanceProcessor(Execution execution, Order order, Account account) {
        if (order.getVal() != execution.getFillPrice()) {
            long returnAmount = Math.abs(order.getVal() - execution.getFillPrice()) * execution.getQuantity();
            account.doPositiveOrderBalance(returnAmount);
            balanceNotifier.pushBalance(account);
        }
    }

    private void sellExecutionBalanceProcessor(Execution execution, Account account) {
        long returnAmount = execution.getFillPrice() * execution.getQuantity();
        account.doPositiveOrderBalance(returnAmount);
        balanceNotifier.pushBalance(account);
    }

    //this must be reworked when account balance calculation in shutdown hook is done.
    public long getCancelled(Route route) {
        AtomicLong res = new AtomicLong(0l);
        for (Order order : orderPool.values()) {
            if (!order.isActive() && order.getRoute().equals(route)) {
                res.getAndAdd(order.getVolume());
            }
        }
        return res.get();
    }

    private Order createOrder(NewOrder newOrder, Account account, Route route) {
        return new Order(newOrder.getSymbol(), account.getAccountId(), route, newOrder.getVal(), newOrder.getVolume());
    }

    private CancelOrderBalanceReturnTask createCancelOrderBalanceReturnTask(Order order, Account account) {
        return new CancelOrderBalanceReturnTask(order, account, this.balanceNotifier);
    }

    private Route getRoute(NewOrder newOrder) {
        return Route.valueOf(newOrder.getRoute());
    }
}
