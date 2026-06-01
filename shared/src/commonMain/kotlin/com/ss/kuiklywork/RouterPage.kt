package com.ss.kuiklywork

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.views.*
import com.ss.kuiklywork.base.BasePager

@Page("router", supportInLocal = true)
internal class RouterPage : BasePager() {

    private data class ListItem(val title: String, val pageName: String)

    private val items = listOf(
        ListItem("彼岸图网", "detail"),
        ListItem("图片浏览器", "detail"),
        ListItem("壁纸精选", "detail"),
        ListItem("风景壁纸", "detail"),
        ListItem("动漫壁纸", "detail"),
        ListItem("游戏壁纸", "detail")
    )

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color(0xFFF2F2F7))
            }
            RouterNavBar {
                attr {
                    title = TITLE
                    backDisable = true
                }
            }
            List {
                attr {
                    flex(1f)
                }
                ctx.items.forEach { item ->
                    View {
                        attr {
                            backgroundColor(Color.WHITE)
                            padding(16f)
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        View {
                            attr {
                                flex(1f)
                            }
                            Text {
                                attr {
                                    text(item.title)
                                    fontSize(17f)
                                    color(Color(0xFF333333))
                                }
                            }
                        }
                        Text {
                            attr {
                                text(">")
                                fontSize(17f)
                                color(Color(0xFFC7C7CC))
                            }
                        }
                        event {
                            click {
                                val pageData = JSONObject()
                                pageData.put("title", item.title)
                                ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
                                    .openPage(item.pageName, pageData)
                            }
                        }
                    }
                    View {
                        attr {
                            height(0.5f)
                            backgroundColor(Color(0xFFE5E5EA))
                            marginLeft(16f)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val TITLE = "首页"
    }
}

internal class RouterNavigationBar : ComposeView<RouterNavigationBarAttr, ComposeEvent>() {
    override fun createEvent(): ComposeEvent {
        return ComposeEvent()
    }

    override fun createAttr(): RouterNavigationBarAttr {
        return RouterNavigationBarAttr()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    paddingTop(ctx.pagerData.statusBarHeight)
                    backgroundColor(Color.WHITE)
                }
                View {
                    attr {
                        height(44f)
                        allCenter()
                    }
                    Text {
                        attr {
                            text(ctx.attr.title)
                            fontSize(17f)
                            fontWeightSemisolid()
                            color(Color.BLACK)
                        }
                    }
                }
                vif({ !ctx.attr.backDisable }) {
                    Image {
                        attr {
                            absolutePosition(
                                top = 12f + getPager().pageData.statusBarHeight,
                                left = 12f,
                                bottom = 12f,
                                right = 12f
                            )
                            size(10f, 17f)
                            src("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAsAAAASBAMAAAB/WzlGAAAAElBMVEUAAAAAAAAAAAAAAAAAAAAAAADgKxmiAAAABXRSTlMAIN/PELVZAGcAAAAkSURBVAjXYwABQTDJqCQAooSCHUAcVROCHBiFECTMhVoEtRYA6UMHzQlOjQIAAAAASUVORK5CYII=")
                        }
                        event {
                            click {
                                getPager().acquireModule<RouterModule>(RouterModule.MODULE_NAME)
                                    .closePage()
                            }
                        }
                    }
                }
                View {
                    attr {
                        height(0.5f)
                        backgroundColor(Color(0xFFE5E5EA))
                    }
                }
            }
        }
    }
}

internal class RouterNavigationBarAttr : ComposeAttr() {
    var title: String by observable("")
    var backDisable = false
}

internal fun ViewContainer<*, *>.RouterNavBar(init: RouterNavigationBar.() -> Unit) {
    addChild(RouterNavigationBar(), init)
}
