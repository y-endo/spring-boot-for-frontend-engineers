package com.example.lab.ch24;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lab/ch24")
public class PoolController {

    private final PoolDemoService service;

    public PoolController(PoolDemoService service) {
        this.service = service;
    }

    @GetMapping("/slow")
    public String slow(@RequestParam(defaultValue = "3") double sec) {
        return service.slowQuery(sec) + " on " + Thread.currentThread();
    }

    @GetMapping("/fast")
    public String fast() {
        return service.fastQuery();
    }

    @GetMapping("/external-in-tx")
    public String externalInTx(@RequestParam(defaultValue = "3000") long millis) throws InterruptedException {
        return service.externalCallInsideTransaction(millis);
    }

    @GetMapping("/external-outside-tx")
    public String externalOutsideTx(@RequestParam(defaultValue = "3000") long millis) throws InterruptedException {
        return service.externalCallOutsideTransaction(millis);
    }

    @GetMapping("/needs-second-connection")
    public String needsSecondConnection(@RequestParam(defaultValue = "0") long holdMillis)
            throws InterruptedException {
        return service.needsSecondConnection(holdMillis);
    }
}
