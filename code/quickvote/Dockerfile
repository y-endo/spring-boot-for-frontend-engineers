# QuickVote のコンテナイメージ（マルチステージ）。
#   ビルド:  docker build -t quickvote .
#   起動:    docker compose -f compose.app.yaml up --build
#
# ステージ1（build）: JDK で jar を作り、layered jar（層ごとのディレクトリ）に展開する。
# ステージ2（runtime）: JRE だけの小さなイメージに、層をコピーして非rootユーザーで動かす。
#
# 層を分ける理由: 依存ライブラリ（dependencies）は滅多に変わらず、アプリのコード（application）はよく変わる。
# 変わらない層はキャッシュされるので、コードだけ変えたときの再ビルド・再配布が速くなる。

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 依存の取得だけを先に行う。pom.xml が変わらない限り、この層はキャッシュされる。
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# テストは CI（./mvnw verify）で実行済みの前提。イメージのビルドでは省く。
RUN ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/quickvote.jar extract --layers --destination target/extracted

FROM eclipse-temurin:25-jre AS runtime
# curl は compose のヘルスチェック（readiness の確認）に使う。JRE のイメージには入っていない。
RUN apt-get update && apt-get install -y --no-install-recommends curl  && rm -rf /var/lib/apt/lists/*
# root で動かさない。コンテナから抜け出されたときの被害を小さくする。
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app

# 変わりにくい順にコピーする（上の層ほどキャッシュが効く）
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER app
# 利用者向けのポートだけを EXPOSE する。管理ポート（9090: health と metrics）は内部ネットワーク専用なので書かない。
EXPOSE 8080

# prod プロファイル: Cookie の Secure 属性を有効にし、compose 連携を切る。接続先などは環境変数で渡す。
ENV SPRING_PROFILES_ACTIVE=prod
# コンテナのメモリ制限に合わせて、ヒープ上限を「制限の75%」にする（既定は25%と小さい）。
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-jar", "/app/quickvote.jar"]
