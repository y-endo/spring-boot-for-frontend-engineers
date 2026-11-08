plugins {
	java
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "QuickVote: 会員登録不要の匿名投票サービス（学習教材のリファレンス実装）"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")

	// pom.xml の <scope>runtime</scope> + <optional>true</optional> に当たる
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	// PGConnection を直接使うので compile スコープ（Initializr の既定 runtimeOnly から変更）
	implementation("org.postgresql:postgresql")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// ./gradlew test は速いテスト(*Test)だけ。実PostgreSQLを使う *IT は integrationTest に分ける
// （Maven では surefire と failsafe の役割分担）。
tasks.test {
	exclude("**/*IT.class")
}

val integrationTest = tasks.register<Test>("integrationTest") {
	description = "Testcontainers を使う *IT を実行する（Docker が必要）"
	group = "verification"
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	include("**/*IT.class")
	shouldRunAfter(tasks.test)
}

tasks.check {
	dependsOn(integrationTest)
}

// jar 名を quickvote.jar に固定する（pom.xml の <finalName> に当たる。Dockerfile から参照するため）
tasks.bootJar {
	archiveFileName = "quickvote.jar"
}
