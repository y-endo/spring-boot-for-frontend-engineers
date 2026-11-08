# /actuator/metrics/<name> の応答を1行にまとめる。使い方: curl ... | python metric.py
import sys, json
d = json.load(sys.stdin)
ms = ", ".join(f'{x["statistic"]}={x["value"]:.6g}' for x in d["measurements"])
print(f'{d["name"]} [{d.get("baseUnit","-")}] {ms}')
