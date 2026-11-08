# Tomcat の connection-timeout（20秒）の観察。リクエストを途中まで送って黙り込むクライアント（遅いクライアント）が、
# 何秒でサーバーに切られるかを測る。使い方: python slow-client.py 18045
import socket, sys, time

port = int(sys.argv[1])
s = socket.create_connection(("127.0.0.1", port))
s.sendall(b"GET /api/polls/x HTTP/1.1\r\nHost: localhost\r\n")  # ヘッダーの終わり（空行）を送らない
t0 = time.time()
s.settimeout(60)
try:
    data = s.recv(1024)
    print(f"{time.time() - t0:.1f} 秒後にサーバーが応答/切断: {data[:40]!r}")
except Exception as e:
    print(f"{time.time() - t0:.1f} 秒後: {e!r}")
