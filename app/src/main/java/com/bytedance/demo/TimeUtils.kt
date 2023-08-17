@file:JvmName("TimeUtils")

package com.bytedance.demo

import java.text.SimpleDateFormat
import java.util.Date

/**

 * Author：ShengDe·Cen on 2022/6/29 17:22

 * explain：时间格式化工具

 */

//时间格式化工具
val sdf = SimpleDateFormat("mm:ss")
val date = Date()
fun Long.format(patter: String): String {
    val date = Date(this)
    //时间格式化工具
    val sdf = SimpleDateFormat(patter)
    return sdf.format(date)
}

fun Long.formatMMss(): String {
    date.time = this
    return sdf.format(date)
}

