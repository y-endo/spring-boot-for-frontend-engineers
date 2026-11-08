package com.example.review1.bad;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 欠陥入りの実装を動かすための設定。このクラスにレビューすべき点はない。 */
@SpringBootApplication(scanBasePackages = { "com.example.review1.bad", "com.example.review1.infra" })
public class BadApp {
}
