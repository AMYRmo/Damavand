package com.amyrmo.damavand.ui.chains

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.amyrmo.damavand.R
import com.amyrmo.damavand.logic.ChainError
import com.amyrmo.damavand.logic.ChainValidator
import com.amyrmo.damavand.logic.ChainWarning

/** Localized text for a [ChainError]; [nameOf] resolves a profile id to its display name. */
@Composable
fun chainErrorText(error: ChainError, nameOf: (String) -> String): String = when (error) {
    is ChainError.MissingMember ->
        stringResource(R.string.chain_error_missing, error.position + 1)

    is ChainError.TooShort ->
        stringResource(R.string.chain_error_short)

    is ChainError.TooLong ->
        stringResource(R.string.chain_error_long, ChainValidator.MAX_HOPS)

    is ChainError.DuplicateMember ->
        stringResource(R.string.chain_error_duplicate, nameOf(error.profileId))

    is ChainError.NestedChain ->
        stringResource(R.string.chain_error_nested, nameOf(error.profileId))

    is ChainError.PositionNotAllowed ->
        stringResource(R.string.chain_error_position, nameOf(error.profileId), error.reason)

    is ChainError.SpliceImpossible ->
        stringResource(R.string.chain_error_splice, nameOf(error.profileId))

    is ChainError.UdpNotCarried -> stringResource(
        R.string.chain_error_udp,
        nameOf(error.needingProfileId),
        nameOf(error.carrierProfileId),
    )

    is ChainError.WhitednsInvalid -> stringResource(
        R.string.chain_error_whitedns,
        nameOf(error.profileId),
        error.reasons.joinToString("; "),
    )

    is ChainError.CustomCommandUpstream ->
        stringResource(R.string.chain_error_custom, nameOf(error.profileId))

    is ChainError.SealedPayload ->
        stringResource(R.string.chain_error_sealed, nameOf(error.profileId))

    is ChainError.DynamicUpstreamUnsupported ->
        stringResource(R.string.chain_error_dynamic, nameOf(error.profileId))

    is ChainError.UnplannableTarget -> error.reason
}

/** Localized text for a [ChainWarning]. */
@Composable
fun chainWarningText(warning: ChainWarning, nameOf: (String) -> String): String = when (warning) {
    ChainWarning.DnsTunnelSpeed -> stringResource(R.string.chain_warn_dns)
    ChainWarning.LatencyLength -> stringResource(R.string.chain_warn_latency)
    is ChainWarning.TcpOnlySegment ->
        stringResource(R.string.chain_warn_tcp, nameOf(warning.profileId))
}
