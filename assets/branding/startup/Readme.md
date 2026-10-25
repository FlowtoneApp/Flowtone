# 声流 · 启动动画

双击 **preview.html**，即可离线预览。可重播、暂停、慢放和拖动进度条；“启动屏 / 应用图标”切换展示场景。无需安装软件，也不需要联网。

## 文件说明

- `preview.html`：交互预览，0.8 秒单次播放并停在完整标志上。
- `shengliu-startup-preview.gif`：循环展示的 GIF，便于分享；重复播放仅用于演示，安卓启动动画本身只播放一次。
- `shengliu-animated-icon.svg`：带圆角底板的 SVG 动画，浏览器打开可播放一次。
- `shengliu-splash-animation.svg`：透明底启动屏 SVG 动画，已保留系统遮罩所需边距。
- `android/`：安卓矢量资源、动画 XML、启动主题与接入示例。详见该文件夹的中文说明。
- `source-svg/`：原始静态图标、透明标志、单色标志和 PNG 预览。
- `motion-spec.json`：原始路径、配色、时间线和缓动参数，供开发或后续修改使用。

## 动作节奏

| 时间 | 动作 |
|---|---|
| 0–360 ms | 两层薄荷色波浪由左向右汇入 |
| 100–460 ms | 蓝紫色符干向上展开 |
| 320–560 ms | 音符顶部向右舒展 |
| 560–800 ms | 轻微回弹收束，停在静态图标上 |

配色：深藏蓝 `#171D36`，蓝紫 `#AC9BFF`，薄荷青 `#59DFCD`，浅薄荷 `#A4F4D4`。全部为纯色矢量，无字体或外部图片依赖。

HTML、GIF、SVG 与 Android 资源使用同一套形状和时间线。启动屏版本为适应 Android 的圆形安全区域，将标志在矢量画布内缩放至 70%；桌面应用图标版本保持原始比例。

## 验证范围

已核对浏览器预览、关键帧、SVG/XML 格式、路径变形兼容性和资源引用。没有用户的安卓项目，交付物为可接入素材包，尚未在实际安卓设备上编译运行。系统启动屏的实际显示时长取决于应用就绪时间；应用准备就绪时可能提前过渡到首页。不要为完整播放而强制拖慢日常启动。

Android 官方参考：
- https://developer.android.com/develop/ui/views/launch/splash-screen
- https://developer.android.com/reference/android/graphics/drawable/AnimatedVectorDrawable
