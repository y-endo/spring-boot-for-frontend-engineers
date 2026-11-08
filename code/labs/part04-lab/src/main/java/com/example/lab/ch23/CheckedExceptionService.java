package com.example.lab.ch23;

import java.io.IOException;

import com.example.lab.LabRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** checked 例外は、既定ではロールバックされない（RuntimeException と Error だけがロールバック）。 */
@Service
public class CheckedExceptionService {

    private final LabRepository repo;

    public CheckedExceptionService(LabRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void failWithChecked(String joinCode) throws IOException {
        repo.insertPoll("checked例外", joinCode);
        throw new IOException("外部との通信に失敗した（checked例外）");
    }

    @Transactional(rollbackFor = Exception.class)
    public void failWithCheckedRollbackFor(String joinCode) throws IOException {
        repo.insertPoll("checked例外+rollbackFor", joinCode);
        throw new IOException("外部との通信に失敗した（checked例外）");
    }
}
