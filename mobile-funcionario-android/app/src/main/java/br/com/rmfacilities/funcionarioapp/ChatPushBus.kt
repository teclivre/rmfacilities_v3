package br.com.rmfacilities.funcionarioapp

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bus em memoria para sinalizar a MensagensActivity quando uma notificacao push
 * de chat chegar. Substitui o polling agressivo (5s) por refresh sob demanda.
 * O polling continua existindo como fallback, mas em frequencia muito menor.
 */
object ChatPushBus {
    private val _events = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyNewMessage() {
        _events.tryEmit(Unit)
    }
}
