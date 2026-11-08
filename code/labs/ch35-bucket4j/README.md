# 第35章のラボ：Bucket4j

自作の `TokenBucket`（容量10・毎分10個補充）と同じものを、Bucket4j（`com.bucket4j:bucket4j_jdk17-core:8.20.0`）で書いた最小のテストです。Boot の BOM の管理外なので、バージョンは自分で書きます。

```bash
./mvnw test        # Windows は .\mvnw.cmd
```
