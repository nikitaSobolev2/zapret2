package zapret.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Emits daemon state on a timer so the window and the menu bar always agree with reality. */
class StatusPoller(
    private val service: ZapretService,
    private val period: Duration = 2.seconds,
) {
    fun statuses(): Flow<DaemonStatus> = flow {
        while (true) {
            emit(service.status())
            delay(period)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)
}
