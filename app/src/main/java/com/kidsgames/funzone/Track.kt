package com.kidsgames.funzone

import android.graphics.Color

enum class Scenery { TREE, PINE, BUSH, BUILDING, HOUSE, LAMP, HAY, SAND, BARRIER, SNOWMAN }

enum class ObstacleKind(val radiusM: Float, val hazard: Boolean) {
    CONE(0.6f, true), PUDDLE(1.2f, true), OIL(1.1f, true), ROCK(0.9f, true),
    MUD(1.4f, true), ICE(1.4f, true), BOOST(1.1f, false), COIN(0.6f, false),
}

/** A race course: how the ground looks, what grows beside it and what lies on the road. */
enum class Track(
    val title: String,
    val emoji: String,
    val roadColor: Int,
    val sideColor: Int,
    val shoulderColor: Int,
    /** Transparent for dirt tracks with no painted lines. */
    val laneLineColor: Int,
    val curbs: Boolean,
    val dirt: Boolean,
    /** 1 = sticky tarmac, lower = slidey. */
    val grip: Float,
    val scenery: List<Scenery>,
    val hazards: List<ObstacleKind>,
    val themeColor: Int,
) {
    CITY(
        "City Streets", "🏙️", Color.parseColor("#546E7A"), Color.parseColor("#B0BEC5"),
        Color.parseColor("#78909C"), Color.WHITE, true, false, 1f,
        listOf(Scenery.BUILDING, Scenery.BUILDING, Scenery.LAMP, Scenery.TREE),
        listOf(ObstacleKind.CONE, ObstacleKind.PUDDLE), Color.parseColor("#1E88E5"),
    ),
    HIGHWAY(
        "Highway", "🛣️", Color.parseColor("#424242"), Color.parseColor("#66BB6A"),
        Color.parseColor("#9E9E9E"), Color.WHITE, false, false, 1f,
        listOf(Scenery.TREE, Scenery.TREE, Scenery.BUSH),
        listOf(ObstacleKind.CONE, ObstacleKind.OIL), Color.parseColor("#455A64"),
    ),
    FARM(
        "Farm Road", "🌾", Color.parseColor("#A1887F"), Color.parseColor("#9CCC65"),
        Color.parseColor("#8D6E63"), Color.TRANSPARENT, false, true, 0.85f,
        listOf(Scenery.HAY, Scenery.TREE, Scenery.HOUSE, Scenery.BUSH),
        listOf(ObstacleKind.MUD, ObstacleKind.PUDDLE, ObstacleKind.ROCK), Color.parseColor("#7CB342"),
    ),
    BUILDING_SITE(
        "Building Site", "🏗️", Color.parseColor("#C8A165"), Color.parseColor("#E0B97A"),
        Color.parseColor("#A1887F"), Color.TRANSPARENT, false, true, 0.8f,
        listOf(Scenery.SAND, Scenery.BARRIER, Scenery.SAND, Scenery.BUILDING),
        listOf(ObstacleKind.CONE, ObstacleKind.ROCK, ObstacleKind.MUD), Color.parseColor("#FB8C00"),
    ),
    SNOW(
        "Snowy Mountain", "🏔️", Color.parseColor("#B0BEC5"), Color.parseColor("#FAFAFA"),
        Color.parseColor("#90A4AE"), Color.parseColor("#E3F2FD"), false, true, 0.45f,
        listOf(Scenery.PINE, Scenery.PINE, Scenery.SNOWMAN),
        listOf(ObstacleKind.ICE, ObstacleKind.ROCK, ObstacleKind.CONE), Color.parseColor("#4FC3F7"),
    ),
}
