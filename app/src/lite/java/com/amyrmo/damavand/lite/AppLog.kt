package com.amyrmo.damavand.lite

import android.util.Log

object AppLog {
    private const val TAG = "Khazar"

    fun i(tag: String, msg: String) = Log.i(TAG, "$tag: $msg")
    fun w(tag: String, msg: String) = Log.w(TAG, "$tag: $msg")
    fun e(tag: String, msg: String, e: Throwable? = null) = Log.e(TAG, "$tag: $msg", e)
}
