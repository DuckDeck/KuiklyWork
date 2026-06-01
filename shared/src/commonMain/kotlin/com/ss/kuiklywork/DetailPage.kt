package com.ss.kuiklywork

import com.ss.kuiklywork.base.BasePager
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

@Page("detail")
internal class DetailPage : BasePager() {

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color.WHITE)
            }
            RouterNavBar {
                attr {
                    title = ctx.pagerData.params.optString("title", "详情")
                    backDisable = false
                }
            }
            View {
                attr {
                    flex(1f)
                    allCenter()
                }
                Text {
                    attr {
                        text("页面建设中...")
                        fontSize(18f)
                        color(Color(0xFF999999))
                    }
                }
            }
        }
    }
}
