# XB工具 更新源配置说明

本目录的 `update.json` 就是应用「更新检测」页读取的清单文件。把它放到你自己的 GitHub / Gitee 仓库即可。

## 一、创建仓库

1. 登录 GitHub，新建仓库，例如 `XB-Tool`，可见性选 **Public**（应用需要匿名读取 raw 文件）。
2. 仓库根目录上传 `update.json`（本文件）。
3. 如果你还要发布 APK，再上传 APK 到 Release，拿到 `https://github.com/用户/仓库/releases/download/v2.7.1/XB工具-v2.7.1.apk` 这样的直链。

## 二、可用的清单地址

| 方式 | 地址格式 | 说明 |
| --- | --- | --- |
| GitHub raw | `https://raw.githubusercontent.com/你的用户名/XB-Tool/main/update.json` | 国内经常被墙或解析到 127.0.0.1 |
| jsDelivr（推荐） | `https://fastly.jsdelivr.net/gh/你的用户名/XB-Tool@main/update.json` | 国内通常可用，建议填这个 |
| Gitee raw | `https://gitee.com/你的用户名/XB-Tool/raw/main/update.json` | 国内速度较好，需要仓库公开 |

把上面任一地址填到应用「更新检测 → 更新源地址」，或直接改 `Prefs.kt` 里的 `DEFAULT_UPDATE_URL` 后重新打包。

## 三、update.json 字段

```json
{
  "app": {
    "versionCode": 271,
    "versionName": "2.7.1",
    "url": "APK 直链",
    "sha256": "APK 的 SHA-256",
    "notes": "更新说明"
  },
  "virusDb": {
    "version": "2026.10.25",
    "url": "主特征库地址（JSON 或 SHA-256 哈希列表）",
    "sha256": "可选，填了就校验",
    "notes": "说明",
    "mirrors": [
      { "name": "备用源", "url": "备用地址", "format": "json" }
    ]
  },
  "announcements": [ ... ],
  "changelog": [ ... ]
}
```

- `app.versionCode` 大于当前安装版本才会提示更新。
- `virusDb.version` 按点号分段比较，大于本地版本才提示。
- `virusDb.url` 和 `mirrors` 会按顺序尝试，前一个失败自动换下一个。
- 病毒库支持两种格式：
  - **JSON**：`virus_db.json` 的结构（hashes / patterns / names）
  - **纯哈希列表**：每行一个 SHA-256，可直接用公开恶意样本哈希库

## 四、内置离线特征

应用 APK 已内置 `virus_hashes_builtin.txt`（约 4,500 条公开恶意样本 SHA-256）。即使更新源不可用，安装后也自带这批特征，不再是只有 5 条。

## 五、本地校验

上传前可以用：

```powershell
Get-FileHash .\update.json -Algorithm SHA256
```

应用不做清单签名校验，所以请确保仓库权限为公开只读，避免他人篡改。
