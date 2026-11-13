package com.example.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.R

data class HairColor(
    val id: String,
    val nameEn: String,
    @StringRes val nameResId: Int,
    val hex: String,
    val colorInt: Int,
    val composeColor: Color
)

object HairPalette {
    private fun entry(
        id: String,
        nameEn: String,
        @StringRes nameResId: Int,
        hex: String
    ): HairColor {
        val rgb = hex.removePrefix("#").toInt(16)
        val parsed = (0xFF shl 24) or rgb
        return HairColor(
            id = id,
            nameEn = nameEn,
            nameResId = nameResId,
            hex = hex,
            colorInt = parsed,
            composeColor = Color(parsed)
        )
    }

    val allColors: List<HairColor> = listOf(
        entry("jet_black", "Jet Black", R.string.color_jet_black, "#1A1A1A"),
        entry("soft_black", "Soft Black", R.string.color_soft_black, "#2B2B2B"),
        entry("dark_brown", "Dark Brown", R.string.color_dark_brown, "#3B2F2F"),
        entry("chocolate_brown", "Chocolate Brown", R.string.color_chocolate_brown, "#5C3A21"),
        entry("chestnut", "Chestnut", R.string.color_chestnut, "#7B4B2A"),
        entry("light_brown", "Light Brown", R.string.color_light_brown, "#8B5E3C"),
        entry("dark_blonde", "Dark Blonde", R.string.color_dark_blonde, "#A67C52"),
        entry("honey_blonde", "Honey Blonde", R.string.color_honey_blonde, "#C9A227"),
        entry("golden_blonde", "Golden Blonde", R.string.color_golden_blonde, "#D4AF37"),
        entry("ash_blonde", "Ash Blonde", R.string.color_ash_blonde, "#C2B280"),
        entry("platinum_blonde", "Platinum Blonde", R.string.color_platinum_blonde, "#E5E4E2"),
        entry("copper", "Copper", R.string.color_copper, "#B87333"),
        entry("auburn", "Auburn", R.string.color_auburn, "#7A2814"),
        entry("burgundy", "Burgundy", R.string.color_burgundy, "#800020"),
        entry("cherry_red", "Cherry Red", R.string.color_cherry_red, "#D2042D"),
        entry("rose_gold", "Rose Gold", R.string.color_rose_gold, "#B76E79"),
        entry("pastel_pink", "Pastel Pink", R.string.color_pastel_pink, "#FFD1DC"),
        entry("hot_pink", "Hot Pink", R.string.color_hot_pink, "#FF69B4"),
        entry("purple", "Purple", R.string.color_purple, "#800080"),
        entry("lavender", "Lavender", R.string.color_lavender, "#E6E6FA"),
        entry("blue_black", "Blue Black", R.string.color_blue_black, "#0F1B2D"),
        entry("navy_blue", "Navy Blue", R.string.color_navy_blue, "#000080"),
        entry("teal", "Teal", R.string.color_teal, "#008080"),
        entry("emerald", "Emerald", R.string.color_emerald, "#046A38"),
        entry("silver_grey", "Silver Grey", R.string.color_silver_grey, "#C0C0C0"),
        entry("ash_grey", "Ash Grey", R.string.color_ash_grey, "#808080"),
        entry("white_blonde", "White Blonde", R.string.color_white_blonde, "#F5F5F5"),
        entry("caramel", "Caramel", R.string.color_caramel, "#C68E5E"),
        entry("mahogany", "Mahogany", R.string.color_mahogany, "#4E0E0E"),
        entry("fire_orange", "Fire Orange", R.string.color_fire_orange, "#FF5F1F")
    )

    private val byId: Map<String, HairColor> = allColors.associateBy { it.id }

    fun findById(id: String): HairColor = byId[id] ?: allColors.first()

    val defaultSelected: HairColor = findById("copper")
}
