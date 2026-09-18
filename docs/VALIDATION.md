## Material Design 3 界面迁移（2026-09-18）

- 接入 Material Components 1.13.0 和 Material3 DayNight 主题。四个主页面统一使用主题色、工具栏、圆角卡片、填充 / 色调按钮、浮动标签输入框、Material 复选框和图标底部导航；所有应用确认 / 表单弹窗改为 Material 对话框。
- 保留原有数据库、整数分金额、事务、套餐和促销规则。浅色与深色模式跟随系统。
- `assembleDebug`、`testDebugUnitTest`、`lintDebug` 通过；35 项 JVM / Robolectric 测试通过，Lint 0 错误、8 项提示。
- 专用 Android 15 / API 35 模拟器：通过 `adb shell am instrument` 运行设备冒烟测试，1 项通过，覆盖条码录入与四个页面切换。
- 检查四页浅色截图、深色收银与结账弹窗。修复 TextInputLayout 标签与旧 hint 重叠、底部导航重复应用系统边距的问题。截图位于 `screenshots/material3/`。
- 相机识码、实体扫码枪、系统文件选择器及其他屏幕尺寸未在本次设备验证中覆盖。

# 验证记录

## 1.2.0 固定套餐组合售价（2026-09-17）

- `assembleDebug testDebugUnitTest lintDebug`：成功，35 项测试全部通过。
- 新增 7 项测试，覆盖套餐创建与购物车恢复、混合收银、共享组成商品库存合并校验、套餐修改校验、实收不足、历史快照与 CSV、删除套餐后退货、促销叠加、零元套餐、非法配置回滚、极大数量及 v2 数据库升级。
- 安装包：`dist/ZeroPay-1.2.0-debug.apk`，校验值见同名 `.sha256` 文件。
- 本次未连接设备，未进行实体设备或模拟器验证。

## 自定义价格（2026-09-17）

- `assembleDebug testDebugUnitTest lintDebug`：成功，28 项测试全部通过。
- 覆盖自定义金额边界、零元、原价上限、订单保存与 CSV 导出、实收不足、退货恢复库存，以及界面输入、重建恢复和结账后重置。
- 安装包：`app/build/outputs/apk/debug/app-debug.apk`。
- 本次未进行实体设备验证。

## 1.1.0 促销功能（2026-09-17）

- `assembleDebug testDebugUnitTest lintDebug`：成功，25 项测试全部通过。
- 新增 8 项测试，覆盖折扣精度和极值、满减门槛和仅减一次、非法规则、优惠订单保存与 CSV 导出、优惠结账价格/库存/实收校验、满额全免、v1 数据库升级保留订单并退货、界面选择促销及重建恢复和结账后重置。
- 安装包：`dist/ZeroPay-1.1.0-debug.apk`，SHA-256 见同名 `.sha256` 文件。
- 本次无连接设备，未新增模拟器或实体设备验证；下方截图和设备测试记录属于 1.0.0。

## 1.0.0 初始版本

2026-09-17，Windows / JDK 25 / Gradle 9.2.1。

- `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`：构建成功。
- JVM/Robolectric：17 项测试通过（6 项 CSV/金额、9 项数据库事务、2 项界面/收银流程）。
- Android 15 API 35 模拟器：安装成功，设备测试 1 项通过，覆盖条码录入及四个页面切换。
- 四个页面截图已人工检查，保存在 `screenshots/`。收款操作固定在屏幕底部。
- APK 的 v2 签名验证通过；交付的是调试签名安装包。
- Lint：0 错误、7 提示（5 项依赖版本更新、1 项新版 Android 备份规则建议、1 项中文文案国际化建议）。

尚未进行真实相机识码、USB/蓝牙扫码枪、不同品牌系统文件选择器或实体手机兼容性测试。支付方式为离线记账，不接入实际支付服务。模拟器测试商品不会随 APK 安装。

Gradle UTP 设备测试入口曾因 Maven TLS 下载中断失败；使用同一测试 APK 通过 `adb shell am instrument` 直接运行成功。

## 库存添加、删除与竖屏扫描（2026-09-18）

- `assembleDebug testDebugUnitTest lintDebug` 通过，40 项 JVM / Robolectric 测试全部通过。
- 新增 5 项回归测试，覆盖库存表单添加、删除确认与取消、删除后历史保留及退货、同条码恢复、套餐引用限制、CSV 恢复与失败回滚、v3 数据库升级；已有 v1/v2 升级测试继续通过。
- 检查合并后的 Manifest：实际扫描入口 `PortraitCaptureActivity` 配置为 `portrait`，收银、库存查询、新建商品均使用此入口。
- 安装包：`app/build/outputs/apk/debug/app-debug.apk`。
- 本次无连接设备，未生成新设备截图，未进行真实相机识码、旋转屏幕或实体扫码枪验证。
