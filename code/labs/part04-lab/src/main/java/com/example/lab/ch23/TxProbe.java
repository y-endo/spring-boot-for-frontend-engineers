package com.example.lab.ch23;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 「いま、このコードはトランザクションの中か」を調べる小道具。 */
public final class TxProbe {

    private TxProbe() {
    }

    public static String state() {
        boolean active = TransactionSynchronizationManager.isActualTransactionActive();
        String name = TransactionSynchronizationManager.getCurrentTransactionName();
        boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        return "tx=" + active + (active ? " name=" + name + " readOnly=" + readOnly : "");
    }
}
