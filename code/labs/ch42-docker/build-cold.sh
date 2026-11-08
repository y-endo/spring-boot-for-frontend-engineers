#!/bin/sh
# キャッシュなしでビルドして、所要時間とイメージサイズを測る。
set -e
cd "$(dirname "$0")/../../quickvote"
start=$(date +%s)
docker build --no-cache -t qv42:cold .
end=$(date +%s)
echo "cold build: $((end - start)) s"
docker images qv42:cold --format 'image size: {{.Size}} (content: {{.Size}})'
