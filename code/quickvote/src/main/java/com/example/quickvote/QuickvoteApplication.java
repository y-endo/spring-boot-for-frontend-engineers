package com.example.quickvote;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan // QuickVoteProperties を設定クラスとして登録する
@EnableScheduling // 期限切れ削除・SSEハートビート・レート制限の掃除に使う
public class QuickvoteApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickvoteApplication.class, args);
    }

}
