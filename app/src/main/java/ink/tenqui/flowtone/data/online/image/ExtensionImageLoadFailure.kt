package ink.tenqui.flowtone.data.online.image

import ink.tenqui.flowtone.core.online.ExtensionImage

/** 扩展图片请求或解码失败，供应用错误队列展示。 */
internal data class ExtensionImageLoadFailure(
    val image: ExtensionImage,
    val error: Throwable
)
