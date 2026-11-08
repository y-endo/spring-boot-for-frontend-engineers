**リファレンス実装として採用する前に修正が必要です。** 特に、レート制限の迂回、SSEの購読消失・再送停止、例外ログへの機密出力は優先して直すべきです。

ファイルは変更していません。仕様・実装・関連テストの静的レビューに加え、既存のビルド済みクラスを使ったメモリ内の補助検証を行いました。`mvn verify`と実DBを使った再現テストは今回実行していません。以下のパス表記は`code/quickvote/`基準です。

1. **【高】URLのエンコードで参加コードのレート制限を迂回できる**

   対象：[JoinRateLimitFilter.java:39](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/security/JoinRateLimitFilter.java:39)、[VisitorAuthenticationFilter.java:91](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/security/VisitorAuthenticationFilter.java:91)

   `getRequestURI()`と`"/api/join"`を直接比較しています。一方、Spring MVCはパスの各セグメントをデコードして照合します。

   **再現：** 有効な訪問者CookieとCSRFヘッダーを付け、`POST /api/%6aoin`へ繰り返し送信する。MVCでは`/api/join`として処理されますが、制限フィルタは素通りします。補助検証では、通常URLは101回中1回拒否、エンコードURLは101回すべて通過しました。既定の`StrictHttpFirewall`もこの表記を許可します。

   **修正案：** `PathPatternRequestMatcher`でHTTPメソッドとパスを判定する。訪問者発行の対象判定も同じ方式に統一し、エンコードURLとコンテキストパス付きのテストを追加する。[公式API](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/servlet/util/matcher/PathPatternRequestMatcher.html)

2. **【高】例外オブジェクトをそのままログに渡しており、DBエラーの入力値が漏れる**

   対象：[ProblemDetailAdvice.java:50](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/common/ProblemDetailAdvice.java:50)、[ExpiredDataCleaner.java:87](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/poll/ExpiredDataCleaner.java:87)

   `log.error(..., ex)`は原因例外のメッセージも出力します。「入力値をメッセージに連結しなければ安全」というコメントは誤りです。PostgreSQLのCHECK違反には、違反行の内容が含まれます。

   **再現：** テストで投票作成後に`Clock`を`created_at`より前へ戻して手動締切する。`closes_at >= created_at`制約に違反し、原因例外の`Failing row contains ...`からタイトル・参加コードがログへ出ます。時計がずれた複数インスタンスでも発生し得ます。

   **修正案：** 例外種別・SQLSTATE・相関IDなどを許可リストで記録し、原因例外を含むメッセージは出さない。スタックが必要ならメッセージを除いたフレームを記録する。現在の`LoggingIT`には、入力値を含むDB制約違反の検証がありません。

3. **【高】無効な作成・招待受諾リクエストでも、制限なく訪問者行を増やせる**

   対象：[VisitorAuthenticationFilter.java:72](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/security/VisitorAuthenticationFilter.java:72)、[JoinRateLimitFilter.java:39](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/security/JoinRateLimitFilter.java:39)

   訪問者の作成は入力検証・招待検証より前に独立して確定します。IP制限があるのは`/api/join`だけです。

   **再現：** 正しいCSRF Cookie・ヘッダーを保持し、訪問者Cookieを毎回捨てて`POST /api/invites/invalid/accept`を連打する。毎回404でも`visitors`は増え続け、60日間残ります。`POST /api/polls`への不正な本文でも同様です。

   **修正案：** 訪問者を発行する全入口に、発行前のIP制限を設ける。作成・招待発行には主体単位の制限も設ける。CSRFは自動化クライアントによる資源消費対策にはなりません。

4. **【中】SSEの購読登録と最後の購読解除が競合すると、新しい購読が消える**

   対象：[PollEventHub.java:80](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/event/PollEventHub.java:80)、[PollEventHub.java:105](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/event/PollEventHub.java:105)

   `computeIfAbsent(...).add(subscription)`全体は原子的ではありません。

   **再現：** 新規接続Bが既存のSetを取得した直後、接続Aの解除が最後の要素を削除してMapからSetを除去する。その後Bが古いSetへ追加されると、Bは名簿から参照されません。補助検証では、Bの登録完了後も`subscriberCount()`が0になりました。初回再送後の通知・ハートビートが届きません。

   **修正案：** Setの取得・作成・追加を`subscriptions.compute(...)`の中で完結させ、解除側と同じキー単位の排他制御に載せる。

5. **【中】SSEの未送信イベントが500件を超えると、再送が途中で止まる**

   対象：[PollEventService.java:19](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/event/PollEventService.java:19)、[PollEventHub.java:130](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/event/PollEventHub.java:130)

   履歴取得は500件までですが、続きの取得を次のNOTIFYに依存しています。ハートビートでは履歴を読みません。

   **再現：** 切断中に501件のイベントを確定させ、更新を止めて`Last-Event-ID: 0`で再接続する。補助検証でも500件しか送られませんでした。501件目は次の通知や再接続まで届きません。1人でも選択肢を交互に投票すれば、この件数に達します。

   **修正案：** 接続時の最新seqまでページ単位で追いつかせる。処理量を制限するなら、明示的に続きをスケジュールするか`RESYNC`へ切り替える。新しい業務更新の発生を再送の条件にしない。

6. **【中】IP制限で拒否した後も、偽Cookieごとのバケットを作成する**

   対象：[JoinRateLimiter.java:39](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/security/JoinRateLimiter.java:39)

   IP側が拒否しても`perVisitor.tryAcquire(visitorKey)`を実行します。

   **再現：** 同じIPからCookie値だけ変えて大量送信する。HTTPは429でも、新しいCookie値ごとにメモリ上のバケットが増えます。補助検証では、同一IPの1000回の試行で訪問者バケットが1000個作られました。次の掃除までの増加量に上限がありません。

   **修正案：** IP側が拒否したら直ちに返す。Cookieの形式・長さもバケット作成前に検証する。

7. **【中】バケットの掃除と消費が競合すると、消費済みバケットを捨てて制限をリセットする**

   対象：[TokenBucketLimiter.java:29](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/security/TokenBucketLimiter.java:29)、[TokenBucketLimiter.java:39](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/security/TokenBucketLimiter.java:39)

   `isIdle()`の判定とMapからの削除の間、またMapからの取得と`tryConsume()`の間に隙間があります。バケット内部の`synchronized`だけでは守れません。

   **再現：** 掃除が満タンと判定→リクエストがトークンを消費→掃除が同じオブジェクトを削除→次のリクエストが満タンの新しいバケットを作る。

   **修正案：** 消費と削除の双方をMapの`compute`系操作でキー単位に直列化する。判定後・削除前で停止させる競合テストを追加する。

8. **【中】冪等性記録が期限切れで削除されると、再送処理が500になる**

   対象：[IdempotencyService.java:58](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/common/IdempotencyService.java:58)、[IdempotencyRepository.java:67](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/common/IdempotencyRepository.java:67)

   `ON CONFLICT DO NOTHING`で0件だったことは、その後のSELECTまで既存行が残る保証になりません。

   **再現：** 保持期限直前に再送Aが`tryInsert()`で0件を得る→期限を越えて清掃Bが記録を削除・コミット→Aの`find()`が空になり`IllegalStateException`。別リクエストによる期限切れキーの再利用でも同じ競合があります。

   **修正案：** 既存記録をロックして読む方式にし、取得前に消滅していた場合は先取りから再試行する。清掃と再送を交差させるテストが必要です。READ COMMITTEDではSQL文ごとに見える状態が変わります。[PostgreSQL公式説明](https://www.postgresql.org/docs/current/transaction-iso.html)

9. **【中】遅いSSE接続が、他の通知・定期清掃・アプリ停止まで待たせる**

   対象：[PollEventHub.java:125](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/event/PollEventHub.java:125)、[PollEventHub.java:147](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/event/PollEventHub.java:147)、[PollEventListener.java:57](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/event/PollEventListener.java:57)

   購読のモニタを保持したまま同期送信します。ハートビートも同じモニタを取得し、`stop()`は`thread.join(5000)`より前に`closeAll()`でそのモニタを待ちます。

   **再現：** SSEクライアントが読み取りを止め、送信バッファが埋まるまでイベントを発生させる。リスナーの配信が詰まり、ハートビートも待機します。既定の単一スケジューラスレッドが塞がるため、期限切れ削除とバケット掃除も進まなくなります。

   **修正案：** 配信を制限付きの実行基盤へ分離し、遅い接続を打ち切る。ハートビート・清掃を同じ実行スレッドに依存させず、停止処理にも待機上限を設ける。MVCのレスポンス書き込みはブロッキングです。[Spring MVC公式説明](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)、[Bootのスケジューラ既定値](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)

10. **【中】詳細取得の応答順が逆転すると、古い状態や別の投票を描画する**

    対象：[app.js:152](/D:/Workspace/web/java-study/code/quickvote/src/main/resources/static/app.js:152)、[app.js:193](/D:/Workspace/web/java-study/code/quickvote/src/main/resources/static/app.js:193)

    SSEイベントごとに並行してGETを開始し、返ってきた順に無条件で描画します。

    **再現：** seq=10のGET応答を遅延させ、seq=11を先に描画した後に10を返すと表示が巻き戻ります。投票Aの取得中にBへ移動した場合、遅れたAの応答をBの画面へ描画できます。`currentPollId`はBなので、表示と操作対象が食い違います。

    **修正案：** リクエスト開始時の投票IDと画面世代を保持し、現在と一致する応答だけ描画する。同じ投票では`lastEventSeq`を使って古い応答を捨て、更新通知をまとめて処理する。

11. **【中】ReadinessはDB疎通を確認していないのに、確認するものとして説明されている**

    対象：[compose.app.yaml:33](/D:/Workspace/web/java-study/code/quickvote/compose.app.yaml:33)、[application.yaml:37](/D:/Workspace/web/java-study/code/quickvote/src/main/resources/application.yaml:37)

    `probes.enabled=true`だけではreadinessグループに`db`は入りません。

    **再現：** 起動完了後にDBを停止し、Cookieなしで`/actuator/health/readiness`を呼ぶ。APIがDBアクセスに失敗していても、readinessはUPのままになり得ます。

    **修正案：** この説明どおりの挙動が必要なら`management.endpoint.health.group.readiness.include=readinessState,db`を設定する。DB停止時のテストも追加する。[Boot公式説明](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html#actuator.endpoints.kubernetes-probes.external-state)

12. **【中】本番プロファイルでもmetricsが無認証で公開される**

    対象：[SecurityConfig.java:69](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/security/SecurityConfig.java:69)、[application.yaml:36](/D:/Workspace/web/java-study/code/quickvote/src/main/resources/application.yaml:36)

    **再現：** `prod`で起動し、Cookieなしで`/actuator/metrics/jvm.memory.used`や`/actuator/metrics/hikaricp.connections.active`を取得する。稼働状況・資源使用量を外部から観測できます。

    実装メモに既知の制限として記載されていますが、配布コードには内部ネットワークへ閉じる設定がありません。

    **修正案：** 本番の既定公開をhealthだけにするか、管理ポートを内部インターフェースへ束縛して公開範囲を制限する。単なる`.authenticated()`では、誰でも訪問者Cookieを取得できるため管理者認可になりません。

13. **【低】Security 7では独自CSRF Cookieフィルタが不要で、教材の説明が誤っている**

    対象：[CsrfCookieFilter.java:13](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/security/CsrfCookieFilter.java:13)、[SecurityConfig.java:74](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/security/SecurityConfig.java:74)、[IMPLEMENTATION_NOTES.md:324](/D:/Workspace/web/java-study/code/quickvote/IMPLEMENTATION_NOTES.md:324)

    Security 7.1.2の`csrf.spa()`は、内部ハンドラで`setCsrfRequestAttributeName(null)`を設定しており、トークンを遅延のままにしません。「このフィルタがないと最初のGETでCookieが出ない」は、この構成には当てはまりません。[公式ソース](https://raw.githubusercontent.com/spring-projects/spring-security/7.1.2/config/src/main/java/org/springframework/security/config/annotation/web/configurers/CsrfConfigurer.java)

    **修正案：** フィルタと登録を削除し、レート制限フィルタの配置基準を`CsrfFilter`へ変更する。独自フィルタなしで初回GETのCookie発行を実HTTPテストする。

14. **【低】画面で指定できる「5分後」が、通信時間の分だけ短くなって拒否される**

    対象：[app.js:231](/D:/Workspace/web/java-study/code/quickvote/src/main/resources/static/app.js:231)、[PollService.java:70](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/poll/PollService.java:70)

    **再現：** 時計が一致したブラウザで5分を選ぶ。ブラウザは`送信前時刻＋5分`を送り、サーバーは後の時刻で`現在＋5分`と比較するため422になります。

    **修正案：** 相対時間入力なら期間を送り、サーバーで締切を計算する設計に揃える。絶対時刻APIを維持する場合は、画面の最小値に通信余裕を設け、サーバー検証との違いを明記する。

15. **【低】コードポイント数による100文字制限と、HTMLの`maxlength`が一致しない**

    対象：[index.html:24](/D:/Workspace/web/java-study/code/quickvote/src/main/resources/static/index.html:24)、[index.html:81](/D:/Workspace/web/java-study/code/quickvote/src/main/resources/static/index.html:81)、[BoundedText.java:21](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/common/BoundedText.java:21)

    **再現：** 絵文字を51個入力する。APIは100コードポイントまで許しますが、HTML側の`maxlength="100"`はUTF-16単位で制限するため入力できません。

    **修正案：** クライアントもコードポイント数で検証する。また「人間が数える文字＝コードポイント」という説明は、結合文字やZWJ絵文字では成立しません。「この仕様ではコードポイント数を文字数とする」と明記する。

16. **【低】期間設定の検証がなく、「不正値は起動時に落とす」という説明を満たしていない**

    対象：[QuickVoteProperties.java:38](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/common/QuickVoteProperties.java:38)

    `Duration`の各項目には正数制約がありません。

    **再現：** `quickvote.idempotency.retention=0s`で起動する。同じキーを後から再送すると記録が期限切れとして消され、選択肢追加が再実行されます。SSEのtimeout=0も、想定した30分とは異なる寿命になります。

    **修正案：** recordのコンストラクタなどで各期間が正であることを検証し、許容範囲を明示する。

17. **【低】締切後の招待受諾について、実装・テストと実装メモが食い違う**

    対象：[InviteService.java:80](/D:/Workspace/web/java-study/code/quickvote/src/main/java/com/example/quickvote/invite/InviteService.java:80)、[DeadlineIT.java:110](/D:/Workspace/web/java-study/code/quickvote/src/test/java/com/example/quickvote/DeadlineIT.java:110)、[IMPLEMENTATION_NOTES.md:427](/D:/Workspace/web/java-study/code/quickvote/IMPLEMENTATION_NOTES.md:427)

    メモは「締切後の新規参加は409」としていますが、招待受諾には投票の締切判定がありません。既存テストも、10分で閉じる投票への約24時間後の招待参加を成功させています。

    **修正案：** 招待による共同主催者追加を締切後も許すなら、その例外を仕様とメモへ明記する。禁止するなら、親行ロック後に締切を確認し、拒否時に招待が未消費であることを検証する。現状は教材から意図を一意に読み取れません。

レビュー範囲で、以下は**問題なし**と判断しました。

| 領域 | 判断 |
|---|---|
| 投票ごとの認可・IDOR | `require`／`requireHost`による確認、非参加・不存在の404統一、他投票の選択肢ID拒否は問題なし |
| 参加者50人・選択肢10件 | 親行ロック後の再カウントにより、通常の更新経路で上限を守る設計は問題なし |
| 1人1票 | UNIQUE・UPSERT・複合外部キーの組み合わせは問題なし |
| 招待の一度限り | 条件付きUPDATEと、上限超過時の消費を含むロールバックは問題なし |
| SSEの採番・保存 | 親行更新による採番、業務更新とイベントの同一トランザクション、コミット時NOTIFYは問題なし。配信側は上記指摘あり |
| 締切のAPI判定 | 投票・選択肢追加でロック取得後に時刻を読み、締切時刻ちょうどから拒否する処理は問題なし |
| Cookie・トークン | 本番Secure、HttpOnly、SameSite、32バイト乱数、DBへのハッシュ保存は問題なし |
| Boot 4／Jackson 3の基本形 | 機能別スターター、`tools.jackson`、注入した`JsonMapper`、ラムダDSL、`@MockitoBean`等の採用は問題なし。上記CSRF重複以外に、主要な旧世代APIの持ち込みは見当たりません |