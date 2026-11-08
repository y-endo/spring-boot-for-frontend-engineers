#!/usr/bin/env bash
# 実験用: ./psql.sh -c "SELECT ..." または ./psql.sh < sql/xxx.sql
export PATH="$PATH:/c/Program Files/Docker/Docker/resources/bin"
exec docker exec -i ch20-pg psql -U quickvote -d quickvote "$@"
