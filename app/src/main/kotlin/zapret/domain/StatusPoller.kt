package zapret.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Emits daemon state on a timer. Polls often while the window or menu panel is open;
 * slows down in the background to cut shell/`pgrep` chatter.
 */
class StatusPoller(
    private val service: ZapretService,
    private val activePeriod: Duration = 2.seconds,
    private val idlePeriod: Duration = 15.seconds,
) {
    private val attentive = MutableStateFlow(true)

    fun setAttentive(value: Boolean) {
        attentive.value = value
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun statuses(): Flow<DaemonStatus> = attentive
        .flatMapLatest { on ->
            flow {
                while (true) {
                    emit(service.status())
                    delay(if (on) activePeriod else idlePeriod)
                }
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
}
