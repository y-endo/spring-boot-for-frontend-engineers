package com.example.lab.ch23;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** private メソッドに付けた @Transactional。 */
@Service
public class PrivateService {

    public String callHidden() {
        return hidden();
    }

    @Transactional
    private String hidden() {
        return TxProbe.state();
    }
}
