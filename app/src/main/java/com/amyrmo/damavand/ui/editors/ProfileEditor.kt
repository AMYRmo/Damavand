package com.amyrmo.damavand.ui.editors

import androidx.compose.runtime.Composable
import com.amyrmo.damavand.model.AetherProfile
import com.amyrmo.damavand.model.AutoSelectProfile
import com.amyrmo.damavand.model.ChainProfile
import com.amyrmo.damavand.model.MihomoProfile
import com.amyrmo.damavand.model.Profile
import com.amyrmo.damavand.model.PsiphonProfile
import com.amyrmo.damavand.model.SingBoxProfile
import com.amyrmo.damavand.model.WhitednsProfile
import com.amyrmo.damavand.model.XrayProfile

/**
 * Routes a profile to its core sheet (§5.2: one EditorSheet scaffold,
 * per-core content). Chains edit in the Chains destination's builder and
 * AutoSelect has no fields of its own, so neither appears here.
 */
@Composable
fun ProfileEditorSheet(
    profile: Profile,
    isNew: Boolean,
    onSave: (Profile) -> Unit,
    onDismiss: () -> Unit,
) {
    when (profile) {
        is XrayProfile -> XrayEditorSheet(profile, onSave, onDismiss)
        is SingBoxProfile -> SingBoxEditorSheet(profile, onSave, onDismiss)
        is AetherProfile -> AetherEditorSheet(profile, isNew, onSave, onDismiss)
        is MihomoProfile -> MihomoEditorSheet(profile, onSave, onDismiss)
        is PsiphonProfile -> PsiphonEditorSheet(profile, onSave, onDismiss)
        is WhitednsProfile -> WhitednsEditorSheet(profile, onSave, onDismiss)
        is ChainProfile, is AutoSelectProfile -> Unit
    }
}
