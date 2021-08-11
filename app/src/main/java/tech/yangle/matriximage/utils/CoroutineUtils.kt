package tech.yangle.matriximage.utils

import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * 协程工具
 * <p>
 * Created by yangle on 2021/8/10.
 * Website：http://www.yangle.tech
 */

/**
 * 当前线程挂起一段时间，执行后续逻辑，不阻塞当前线程
 *
 * @param context CoroutineContext
 * @param timeMillis 挂起时间|毫秒
 * @param exec 执行方法
 * @return Job
 * @sample
 * coroutineDelay(coroutineContext, 100) {
 *      // 执行逻辑
 * }
 */
internal fun coroutineDelay(
    context: CoroutineContext, timeMillis: Long,
    exec: () -> Unit
): Job {
    val scope = CoroutineScope(context)
    return scope.launch(getExceptionHandler("coroutineDelay")) {
        delay(timeMillis)
        exec()
    }
}

/**
 * 当前线程挂起一段时间，执行后续逻辑，不阻塞当前线程
 * 在Activity、Fragment中使用此方法，与页面生命周期绑定
 *
 * @param scope LifecycleCoroutineScope
 * @param timeMillis 挂起时间|毫秒
 * @param exec 执行方法
 * @return Job
 * @sample
 * coroutineDelay(lifecycleScope, 100) {
 *      // 执行逻辑
 * }
 */
internal fun coroutineDelay(
    scope: LifecycleCoroutineScope, timeMillis: Long,
    exec: () -> Unit
): Job = scope.launch(getExceptionHandler("coroutineDelay-Lifecycle")) {
    delay(timeMillis)
    exec()
}

/**
 * 当前线程周期运行任务
 *
 * @param context CoroutineContext
 * @param timeMillis 运行周期|毫秒
 * @param exec 执行方法
 * @return Job
 * @sample
 * val job = coroutineInterval(coroutineContext, 1000) {
 *      // 执行逻辑
 * }
 * job.cancel()
 */
internal fun coroutineInterval(
    context: CoroutineContext, timeMillis: Long,
    exec: (time: Int) -> Unit
): Job {
    val scope = CoroutineScope(context)
    return scope.launch(getExceptionHandler("coroutineInterval")) {
        repeat(Int.MAX_VALUE) {
            exec(it)
            delay(timeMillis)
        }
    }
}

/**
 * 当前线程周期运行任务
 * 在Activity、Fragment中使用此方法，与页面生命周期绑定
 *
 * @param scope LifecycleCoroutineScope
 * @param timeMillis 运行周期|毫秒
 * @param exec 执行方法
 * @return Job
 * @sample
 * val job = coroutineInterval(lifecycleScope, 1000) {
 *      // 执行逻辑
 * }
 * job.cancel()
 */
internal fun coroutineInterval(
    scope: LifecycleCoroutineScope, timeMillis: Long,
    exec: (time: Int) -> Unit
): Job = scope.launch(getExceptionHandler("coroutineInterval-Lifecycle")) {
    repeat(Int.MAX_VALUE) {
        exec(it)
        delay(timeMillis)
    }
}

/**
 * 在子线程协程中执行任务，在主线程协程中处理结果，不阻塞当前线程
 *
 * @param context CoroutineContext
 * @param worker 子线程协程
 * @param main 主线程协程
 * @sample
 * coroutineSchedule(coroutineContext, worker = {
 *      // 耗时逻辑
 * }, main = {
 *      // 处理结果
 *  })
 */
internal fun coroutineSchedule(
    context: CoroutineContext, worker: () -> Unit,
    main: () -> Unit
) {
    val scope = CoroutineScope(context)
    scope.launch(Dispatchers.IO + getExceptionHandler("coroutineSchedule")) {
        worker()
        withContext(Dispatchers.Main) {
            main()
        }
    }
}

/**
 * 在子线程协程中执行任务，在主线程协程中处理结果，不阻塞当前线程
 * 在Activity、Fragment中使用此方法，与页面生命周期绑定
 *
 * @param scope LifecycleCoroutineScope
 * @param worker 子线程协程
 * @param main 主线程协程
 * @sample
 * coroutineSchedule(lifecycleScope, worker = {
 *      // 耗时逻辑
 * }, main = {
 *      // 处理结果
 *  })
 */
internal fun coroutineSchedule(
    scope: LifecycleCoroutineScope, worker: () -> Unit,
    main: () -> Unit
) = scope.launch(Dispatchers.IO + getExceptionHandler("coroutineSchedule-Lifecycle")) {
    worker()
    withContext(Dispatchers.Main) {
        main()
    }
}

/**
 * 协程异常处理
 *
 * @param method 方法名
 */
internal fun getExceptionHandler(method: String): CoroutineExceptionHandler {
    return CoroutineExceptionHandler { _, exception ->
        Log.e("CoroutineUtils", "[$method] Exception:${exception}")
    }
}