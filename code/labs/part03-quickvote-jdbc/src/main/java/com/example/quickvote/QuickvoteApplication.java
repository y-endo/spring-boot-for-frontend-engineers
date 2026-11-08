package com.example.quickvote;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan // QuickVoteProperties を設定クラスとして登録する
public class QuickvoteApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickvoteApplication.class, args);
    }

}
