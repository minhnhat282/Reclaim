package com.minhnhat.reclaim.ui

import android.view.View

/** Một tab của màn hình chính. View được giữ lại khi chuyển tab nên trạng thái cuộn không mất. */
interface Screen {
    val view: View

    /** Tab đang hiện hoặc Activity vừa quay lại: làm mới dữ liệu/trạng thái quyền. */
    fun onShow() {}

    fun onHide() {}
}
