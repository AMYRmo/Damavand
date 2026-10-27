package com.amyrmo.damavand.domain.model

enum class ConnectionState {
    Idle,
    Connecting,
    Connected,
    Reconnecting,
    Disconnecting,
    Error;

    val isActive: Boolean
        get() = this == Connecting || this == Connected || this == Reconnecting

    val isTransitioning: Boolean
        get() = this == Connecting || this == Reconnecting || this == Disconnecting
}
