# Changelog

## [Unreleased]

### Changed
- 项目根目录按 `apps / docs / scripts / data / storage / tools` 职责重新整理。
- `erp/` 迁移至 `apps/erp/`，`ledger/` 迁移至 `apps/ledger/`。
- `.tools/`、`.data/`、`.tmp/`、`.screenshots/`、`backup/` 分别更名为 `tools/`、`data/`、`tmp/`、`screenshots/`、`backups/`。
- 校宝数据导入脚本统一迁移至 `scripts/import/`。
- 后端上传目录调整为 `storage/uploads`（`app.upload-dir`）。
- 同步更新 README、Maven/MySQL 配置、导入脚本与调试脚本中的路径引用。

### Added
- 新增 `.gitignore`，排除本地运行环境、构建产物、备份与抓取数据。
