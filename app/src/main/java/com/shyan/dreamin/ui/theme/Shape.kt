package com.shyan.dreamin.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object DreaminShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(14.dp)
    val large = RoundedCornerShape(18.dp)
    val xl = RoundedCornerShape(26.dp)
    val sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

    val material = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = small,
        medium = medium,
        large = large,
        extraLarge = xl
    )
}
