package dev.hermeskotlin.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app mark: the white MotherBase hexagonal M emblem over a vertical black-to-graphite gradient (#000000 to
 * #555555). It matches the default Gradient launcher icon (ic_launcher_background.xml under ic_launcher_foreground.xml,
 * shapes in launcher_icons.xml) as the launcher shows it: the 108dp adaptive grid's middle 72, square, for the caller
 * to clip to its shape. Draw it as an image, never tinted.
 */
val HeraldMark: ImageVector by lazy {
    val background = Brush.linearGradient(
        listOf(Color(0xFF000000), Color(0xFF555555)),
        start = Offset(0f, 18f),
        end = Offset(0f, 90f),
    )
    ImageVector.Builder(name = "HeraldMark", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 72f, viewportHeight = 72f)
        .addGroup(translationX = -18f, translationY = -18f)
        .apply {
            addPath(addPathNodes("M0,0h108v108h-108z"), fill = background)
            addPath(
                addPathNodes("M41.88,33.00L53.99,26.00L66.12,33.00L78.25,40.00L78.25,68.01L66.12,75.00L53.99,82.00L41.88,75.00L29.75,68.01L29.75,40.00ZM44.19,37.02L34.39,42.68L34.39,65.32L44.19,70.98L53.99,76.64L63.81,70.98L73.61,65.32L73.61,42.68L63.81,37.02L53.99,31.36Z"),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.White),
            )
            addPath(
                addPathNodes("M53.99,36.07L46.23,40.54L38.47,45.04L38.47,62.98L45.33,66.93L45.33,48.99L50.50,46.00L50.50,69.92L53.99,71.95L57.50,69.92L57.50,46.00L62.67,48.99L62.67,66.93L69.53,62.98L69.53,45.04L61.77,40.54Z"),
                fill = SolidColor(Color.White),
            )
        }
        .clearGroup()
        .build()
}
