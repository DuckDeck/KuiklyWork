package com.ss.kuiklywork

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import kotlin.math.PI

/** Read a reactive getter in drawing/attribute blocks, never capture a stale snapshot. */
internal fun ViewContainer<*, *>.CosDownloadProgress(
    snapshot: () -> CosDownloadSnapshot?,
    bottom: () -> Float,
    onClick: () -> Unit
) {
    View {
        attr { size(80f, if (snapshot()?.status == "interrupted") 102f else 80f); absolutePosition(right = 16f, bottom = bottom()) }
        event { click { onClick() } }
        View {
            attr { size(80f, 80f); borderRadius(40f); backgroundColor(Color.WHITE) }
            Canvas({ attr { size(80f, 80f) } }) { context, _, _ ->
                val task = snapshot()
                context.lineWidth(4f)
                context.beginPath()
                context.arc(40f, 40f, 37f, 0f, 2f * PI.toFloat(), false)
                context.strokeStyle(Color(0xFFE0E0E0)); context.stroke()
                val progress = task?.percent ?: 0
                if (progress > 0) {
                    context.beginPath()
                    context.arc(40f, 40f, 37f, -PI.toFloat() / 2, -PI.toFloat() / 2 + 2f * PI.toFloat() * progress / 100f, false)
                    context.strokeStyle(if (task?.status == "interrupted") Color(0xFFE88B20) else Color(0xFF1E6BFF))
                    context.lineCapRound(); context.stroke()
                }
            }
            View {
                attr { size(80f, 80f); absolutePosition(top = 0f, left = 0f); allCenter() }
                Text {
                    attr { text("${snapshot()?.percent ?: 0}%"); height(24f); fontSize(16f); color(Color(0xFF1E6BFF)); fontWeightBold() }
                }
                Text {
                    attr {
                        text("${snapshot()?.completed ?: 0}/${snapshot()?.total ?: 0}")
                        height(18f); fontSize(12f); textAlignCenter(); color(Color(0xFF666666))
                    }
                }
            }
        }
        vif({ snapshot()?.status == "interrupted" }) {
            Text {
                attr {
                    text("已暂停")
                    height(22f); fontSize(11f); textAlignCenter(); color(Color(0xFF666666))
                }
            }
        }
    }
}
