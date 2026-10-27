# 词锋 LexForge —— 构建入口
#
# 需要 Python 3.10+（构建词库）与 JDK 17 + Android SDK（编译 APK）。
# Windows 上 make 里如果找不到 python，指定一次即可：
#   make dict PY="C:/path/to/python.exe"

PY     ?= python
GRADLE ?= ./gradlew

.PHONY: help fetch-dict dict inspect test apk release install clean all

help:
	@echo "make fetch-dict  下载原始词库 ecdict.csv（约 66MB，带断点续传）"
	@echo "make dict        生成词库包 app/src/main/assets/L2.lexd"
	@echo "make inspect     自检词库包结构（段表/块数/查词基准）"
	@echo "make test        跑检索内核单测（纯 JVM，无需设备）"
	@echo "make apk         编译 debug APK（arm64 + x86_64，可装模拟器）"
	@echo "make release     编译 release APK（仅 arm64，R8 后约 19MB）"
	@echo "make install     编译并安装 debug 包到已连接设备"
	@echo "make clean       清理构建产物"

fetch-dict:
	cd data/raw && bash fetch.sh

dict:
	$(PY) tools/build_dict.py --ecdict data/raw/ecdict.csv \
		--glossary data/tech/glossary.tsv \
		--out app/src/main/assets/L2.lexd --limit 13000

inspect:
	$(PY) tools/inspect_pack.py app/src/main/assets/L2.lexd

test:
	$(GRADLE) :core:morph:test :core:dict:test :core:search:test --console=plain

apk:
	$(GRADLE) :app:assembleDebug --console=plain

release:
	$(GRADLE) :app:assembleRelease --console=plain

install: apk
	$(GRADLE) :app:installDebug --console=plain

clean:
	$(GRADLE) clean --console=plain
