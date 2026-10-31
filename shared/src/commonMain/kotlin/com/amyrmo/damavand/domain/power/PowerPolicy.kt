package com.amyrmo.damavand.domain.power

object PowerPolicy {
    data class Cadence(

        val tickMs: Long,

        val notifySeconds: Int,

        val autoPollSeconds: Int,

        /** Minimum seconds between widget refreshes driven by the metrics loop (E1). Connection-state changes refresh the widget immediately, outside this cadence. */
        val widgetSeconds: Int,
    )

    fun cadence(screenOn: Boolean): Cadence = if (screenOn) {
        // The 1 s tick stays: it drives the live speed readout while the
        // app is visible. What changed (E1) is the repaint: the
        // notification used to be rebuilt and the widget re-pushed on
        // every tick (notifySeconds was 1); the notification now repaints
        // every 3 s and the widget at most every 30 s.
        Cadence(tickMs = 1_000L, notifySeconds = 3, autoPollSeconds = 3, widgetSeconds = 30)
    } else {
        Cadence(tickMs = 10_000L, notifySeconds = 30, autoPollSeconds = 45, widgetSeconds = 60)
    }
}
