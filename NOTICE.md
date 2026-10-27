# 数据来源与第三方许可

本项目是**完全离线**的 Android 词典应用，不分发任何用户数据，也不请求网络权限。
下面列出运行时与构建期实际使用的第三方资源，以及对应的许可义务。

## 1. 词典释义数据：ECDICT

- 项目：https://github.com/skywind3000/ECDICT
- 许可：MIT License，Copyright (c) 2017 Wei Lin (skywind3000)
- 用途：`app/src/main/assets/L2.lexd` 中的词条、音标、释义均派生自
  `ecdict.csv`（约 77 万条），经 `tools/build_dict.py` 清洗、分组、重排后打包。
- 本地构建需要自行下载原始数据（仓库不附带，见 `data/raw/fetch.sh`）：

  ```bash
  cd data/raw && bash fetch.sh
  ```

- 义务：二次分发本项目（含 `L2.lexd`）时，保留本条署名与本段许可说明。

ECDICT 原始许可全文：

```
MIT License

Copyright (c) 2017 Wei Lin

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 2. 技术术语表：本项目自建

- 文件：`data/tech/glossary.tsv`
- 许可：随本项目，MIT
- 内容：约 280 条技术文档高频词与缩写（`idempotent`、`cache`、`id`、`api`…），
  中文释义人工撰写，构建时插入对应词条的首位义项。

## 3. OCR：Google ML Kit Text Recognition

- 依赖：`com.google.mlkit:text-recognition:16.0.0`（bundled 版，模型打包进 APK）
- 许可：Google ML Kit Terms of Service（专有许可，非开源）
- 用途：拍照 / 相册取词时的本地拉丁文识别
- 说明：模型与识别过程完全在设备端，不上传图片。ML Kit 的清单会带入
  `INTERNET` / `ACCESS_NETWORK_STATE`，本项目在 `AndroidManifest.xml` 中用
  `tools:node="remove"` 显式移除，实测离线识别正常。

## 4. 未采用的资源（记录决策，避免后续误引入）

| 资源 | 许可 | 为什么不用 |
| --- | --- | --- |
| CC-CEDICT | CC BY-SA 4.0 | 传染性许可，会约束整个词库分发方式 |
| NLLB-200 | CC BY-NC 4.0 | 禁止商用 |
| espeak-ng | GPLv3 | 传染性许可，且用于发音合成 |
| opus-mt (Helsinki-NLP) | CC BY 4.0 | 可商用，但模型 int8 后 82MB，尚未接入（见 README「下一步」） |

## 5. 运行时依赖

| 组件 | 许可 |
| --- | --- |
| AndroidX / Jetpack Compose | Apache 2.0 |
| Kotlin / kotlinx.coroutines | Apache 2.0 |
| kotlinx.serialization | Apache 2.0 |
| junit4 / Robolectric | EPL 1.0 / MIT |
| Python 工具链（标准库 + zstandard） | PSF / BSD |

## 6. 关于"零权限"

`aapt2 dump permissions` 对 release APK 的检查结果为**无任何权限声明**：

```bash
aapt2 dump permissions dist/lexforge-*-release-arm64.apk
```

拍照功能通过 `ACTION_IMAGE_CAPTURE` 调起系统相机完成，因此不需要 `CAMERA`；
图片经由 `FileProvider` 临时授权读取，也不申请存储权限。
