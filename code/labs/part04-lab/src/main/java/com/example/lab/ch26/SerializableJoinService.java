package com.example.lab.ch26;

import com.example.lab.LabRepository;
import com.example.lab.ch25.JoinServices;
import com.example.lab.ch25.PollFullException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 親行ロックを使わず、分離レベル SERIALIZABLE で「数える → 挿入する」を守る別解。
 * DB が「同時に動かしたことで、直列に動かした結果と食い違う」トランザクションを検出して、片方を失敗させる。
 * 失敗した側は、トランザクションごとやり直す必要がある（Retries）。
 */
@Service
public class SerializableJoinService {

    private final LabRepository repo;

    public SerializableJoinService(LabRepository repo) {
        this.repo = repo;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void join(long pollId, long visitorId) {
        int current = repo.countParticipants(pollId);
        if (current >= JoinServices.MAX_PARTICIPANTS) {
            throw new PollFullException(pollId);
        }
        repo.insertParticipant(pollId, visitorId, "MEMBER");
    }
}
