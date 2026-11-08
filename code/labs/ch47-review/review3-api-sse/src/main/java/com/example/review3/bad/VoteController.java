package com.example.review3.bad;

import java.util.Map;

import com.example.review3.infra.VoteRecorder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VoteController {

    @Autowired
    private VoteRecorder recorder;

    private String currentVisitor;

    @RequestMapping(value = "/api/votes", method = RequestMethod.POST)
    public Map<String, String> vote(@RequestParam String visitor, @RequestParam long optionId) {
        this.currentVisitor = visitor;
        recorder.record(optionId);
        return Map.of("message", currentVisitor + " の投票を受け付けました");
    }
}
