package com.example.lab.ch23;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 自己呼び出し: 同じクラスの中から this.inner() を呼ぶと、プロキシを通らない。 */
@Service
public class SelfCallService {

    /** @Transactional なし。中から this.inner() を呼ぶ。 */
    public String outer() {
        return inner();                       // this.inner() と同じ。プロキシ（秘書）を通らない
    }

    @Transactional
    public String inner() {
        return TxProbe.state();
    }
}
