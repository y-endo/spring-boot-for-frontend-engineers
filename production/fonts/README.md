# 動画の字幕フォント

動画の字幕とBlenderの文字には Noto Sans JP の 400 / 700 / 900 を使います。
フォントファイルはリポジトリに含めていないので、動画を作り直すときは次の手順で用意してください。

1. [Google Fonts の Noto Sans JP](https://fonts.google.com/noto/specimen/Noto+Sans+JP) からフォント一式をダウンロードする。
2. 中にある可変フォント `NotoSansJP-VariableFont_wght.ttf` を指定して、次を実行する（`fonttools` が必要）。

```bash
python production/fonts/make_fonts.py path/to/NotoSansJP-VariableFont_wght.ttf
```

このフォルダに `NotoSansJP-400.ttf`・`NotoSansJP-700.ttf`・`NotoSansJP-900.ttf` ができます。

Noto Sans JP は [SIL Open Font License 1.1](https://openfontlicense.org/) で配布されています。
