# 標準入力のJSON行を整形して表示する（jq の代わり）。使い方: docker logs ... | python pretty.py [フィールドの絞り込み]
import json, sys
for line in sys.stdin:
    line = line.strip()
    if not line.startswith('{'):
        continue
    try:
        d = json.loads(line)
    except ValueError:
        continue
    print(json.dumps(d, ensure_ascii=False, indent=2))
