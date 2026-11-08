#!/bin/sh
# 実験用の表を空にして、採番も最初に戻す（スキーマは残す）。
docker exec part04-lab-pg psql -U lab -d lab -q -c "TRUNCATE polls, visitors, lab_audit, lab_memos RESTART IDENTITY CASCADE"
