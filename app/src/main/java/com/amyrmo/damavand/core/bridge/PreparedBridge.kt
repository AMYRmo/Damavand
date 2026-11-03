package com.amyrmo.damavand.core.bridge

/**
 * A prepared sing-box bridge artefact (architecture §2.4 rule 2 — the
 * phantom-10808 rule).
 *
 * The artefact carries its own effective port: [json] is the bridge config
 * actually handed to libbox, and [localPort] is the local (mixed inbound)
 * port that this exact JSON opens. The port is never re-derived from a
 * different, unstarted config variant — the build-#74 bug, where the app
 * advertised the prepared config's 10808 while the started bridge had no
 * such listener, is unrepresentable with this type.
 *
 * SingBoxAdapter's bridge role consumes ONLY this artefact: its
 * `prepareBridge` takes a PreparedBridge, and its start reads the
 * readiness probe port and the advertised `HopHandle.socks` from
 * [localPort] alone.
 */
data class PreparedBridge(val json: String, val localPort: Int)
