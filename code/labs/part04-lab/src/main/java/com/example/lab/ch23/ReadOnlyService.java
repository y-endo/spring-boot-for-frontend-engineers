package com.example.lab.ch23;

import com.example.lab.LabRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadOnlyService {

    private final LabRepository repo;

    public ReadOnlyService(LabRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public String read() {
        return TxProbe.state() + " participants=" + repo.countRows("participants");
    }

    @Transactional(readOnly = true)
    public void writeInReadOnly(String joinCode) {
        repo.insertPoll("readOnlyで書く", joinCode);
    }
}
