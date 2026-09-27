# FindYouDevice-old

> 面向老设备的精简版：最低支持 **Android 5.0（API 21）**，Windows Phone 10（Metro）纯平面 UI。
> 适用于 2GB 内存级别老机型（如华为畅享 5 / MT6735 实测验证）。

## 与主仓库的关系

- 主仓库：[FindYouDevice](https://github.com/bcdidit67/FindYouDevice)（Miuix / MD3 双风格，minSdk 26）
- 本仓库为**独立精简实现**：纯 XML + SharedPreferences 存储，无 Compose / Miuix / Room，代码基线基于主仓库对应版本改写，**两仓库完全独立演进（互不同步，避免合并故障）**。

### 基于版本记录（每次发行必填）

| 本仓库版本 | 基于主仓库版本 | 说明 |
| --- | --- | --- |
| 1.00（开发中） | v1.1.1 | 首个 _old 版本：Metro UI + Android 5.0 兼容 |

## 设计规范（Metro / WP10）

- **绝对平面**：禁止 elevation 阴影、禁止圆角（0dp），全部直角
- **纯色块**：主背景纯黑 `#000000`；磁贴亮蓝 `#0078D7`、强调橙 `#FF8C00`、深灰 `#1C1C1C`
- **字体**：粗体无衬线；标题 28sp 粗体；副标题 13sp `#AAAAAA`
- **磁贴布局**：RecyclerView + GridLayoutManager；纯色块内白色图标 + 文字（左下角对齐）
- **交互极简**：仅背景色切换（亮蓝 → 深蓝），无缩放动画
- **布局扁平**：ConstraintLayout 优先，层级 ≤ 3（针对 2GB 老机优化）

## 功能

- 局域网扫描 / 查找报警 / Web 服务 / 别名与密码记忆 / 自动控制（与主仓库一致的核心能力，按 API21 降级适配）

## 构建

```
gradle :app:assembleDebug
```

工具链：AGP 8.2.2 / Gradle 8.2 / Kotlin 1.9.22 / compileSdk 34 / minSdk 21

## 📮 联系作者

- QQ：3891605032（点击复制）
- 邮箱：fxxkhw676767@outlook.com

## ⚖️ License

MIT License（见 [LICENSE](LICENSE)）
