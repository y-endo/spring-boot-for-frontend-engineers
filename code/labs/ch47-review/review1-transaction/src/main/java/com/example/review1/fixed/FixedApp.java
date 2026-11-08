package com.example.review1.fixed;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 修正版を動かすための設定。 */
@SpringBootApplication(scanBasePackages = { "com.example.review1.fixed", "com.example.review1.infra" })
public class FixedApp {
}
