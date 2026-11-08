package com.example.review3.fixed;

import java.util.Map;

import com.example.review3.infra.VoteRecorder;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VoteController {

    private final VoteRecorder recorder;

    public VoteController(VoteRecorder recorder) {
        this.recorder = recorder;
    }

    @PostMapping("/api/votes")
    public Map<String, String> vote(@RequestParam String visitor, @RequestParam long optionId) {
        recorder.record(optionId);
        return Map.of("message", visitor + " の投票を受け付けました");   // リクエスト固有の値はローカルだけに置く
    }
}
