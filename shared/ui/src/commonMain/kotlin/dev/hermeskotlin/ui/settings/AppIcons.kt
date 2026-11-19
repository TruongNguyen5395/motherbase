package dev.hermeskotlin.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import dev.hermeskotlin.ui.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * The launcher icons MotherBase comes with. [Gradient] is the brand icon every install starts with;
 * [MonoDark] and [MonoLight] offer flat monochrome alternatives.
 */
enum class AppIcon(val labelRes: StringResource) {
    Gradient(Res.string.app_icon_gradient),
    MonoDark(Res.string.app_icon_mono_dark),
    MonoLight(Res.string.app_icon_mono_light),
}

/** Which launcher icon Herald shows, and switching it. */
interface AppIcons {
    val current: AppIcon

    /** [icon] as the launcher draws it, [sizePx] square, in the phone's own icon shape; null if it can't be drawn. */
    suspend fun picture(icon: AppIcon, sizePx: Int): ImageBitmap?

    /** Shows [icon] in the launcher instead of [current], and opens Herald again from it. */
    fun apply(icon: AppIcon)
}

/** Reads the icon again after [AppIcons.apply], so [AppIcons.current] follows. */
@Composable
expect fun rememberAppIcons(): AppIcons
