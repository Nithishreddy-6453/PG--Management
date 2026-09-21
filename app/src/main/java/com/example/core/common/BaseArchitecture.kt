package com.example.core.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Empty presentation UI State contract.
 */
interface UiState

/**
 * User interactions or view events triggering state mutations.
 */
interface UiEvent

/**
 * Short-lived, side-effect actions (e.g. snackbars, dialogs, navigation routes).
 */
interface UiEffect

/**
 * Abstract core model standardizing UDF cycles using safe flows and coroutines.
 */
abstract class BaseViewModel<State : UiState, Event : UiEvent, Effect : UiEffect>(
    initialState: State
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<Event>()
    val uiEvent: SharedFlow<Event> = _uiEvent.asSharedFlow()

    private val _uiEffect = Channel<Effect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    protected val currentState: State
        get() = _uiState.value

    init {
        viewModelScope.launch {
            _uiEvent.collect { event ->
                handleEvent(event)
            }
        }
    }

    /**
     * Dispatches user action [event] into the processing channel.
     */
    fun onEvent(event: Event) {
        viewModelScope.launch {
            _uiEvent.emit(event)
        }
    }

    /**
     * Dispatches visual side [effect] asynchronously to the UI observer.
     */
    protected fun emitEffect(effect: Effect) {
        viewModelScope.launch {
            _uiEffect.send(effect)
        }
    }

    /**
     * Update state mutating transaction.
     */
    protected fun updateState(reduce: State.() -> State) {
        _uiState.value = _uiState.value.reduce()
    }

    /**
     * Event listener abstract method to handle user-facing interactions.
     */
    abstract suspend fun handleEvent(event: Event)
}
