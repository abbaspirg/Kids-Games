package com.kidsgames.funzone

import android.graphics.Color

/**
 * Every vehicle a kid can race. Sizes are real-world metres so a bus really is
 * much longer than a motorbike; speeds are metres per second.
 */
enum class VehicleType(
    val title: String,
    val color: Int,
    val lengthM: Float,
    val widthM: Float,
    val topSpeed: Float,
    val accel: Float,
    val steer: Float,
    /** Heavy vehicles lose less speed when they bump into things. */
    val heavy: Boolean,
    /** Off-road vehicles drive straight over mud and rocks. */
    val offroad: Boolean,
    val enginePitch: Float,
    val hasSiren: Boolean,
    val cheer: String,
) {
    RACE_CAR("Race Car", Color.parseColor("#E53935"), 4.6f, 2.0f, 42f, 9f, 9f, false, false, 1.5f, false, "Vroom vroom!"),
    CAR("Car", Color.parseColor("#1E88E5"), 4.4f, 1.9f, 34f, 7f, 8f, false, false, 1.2f, false, "Beep beep!"),
    POLICE("Police Car", Color.WHITE, 4.7f, 2.0f, 36f, 7.5f, 8f, false, false, 1.2f, true, "Nee naw nee naw!"),
    MOTORBIKE("Motorbike", Color.parseColor("#212121"), 2.2f, 0.9f, 38f, 9f, 10f, false, false, 1.7f, false, "Brrrrm!"),
    BUS("Bus", Color.parseColor("#FFB300"), 11f, 2.6f, 25f, 4f, 5.5f, true, false, 0.7f, false, "All aboard!"),
    TRUCK("Truck", Color.parseColor("#43A047"), 10f, 2.5f, 26f, 4f, 5.5f, true, false, 0.65f, false, "Honk honk!"),
    FIRE_TRUCK("Fire Truck", Color.parseColor("#D32F2F"), 9f, 2.5f, 30f, 5f, 6f, true, false, 0.8f, true, "To the rescue!"),
    JCB("JCB Digger", Color.parseColor("#FFC107"), 7f, 2.4f, 22f, 4f, 5f, true, true, 0.6f, false, "Dig dig dig!"),
    TRACTOR("Tractor", Color.parseColor("#2E7D32"), 4.5f, 2.2f, 20f, 4f, 6f, false, true, 0.55f, false, "Chug chug chug!"),
    MONSTER_TRUCK("Monster Truck", Color.parseColor("#7E57C2"), 5.5f, 2.8f, 32f, 6f, 7f, true, true, 0.75f, false, "Smash and crash!");

    /** 1–5 stars for the selection screen. */
    val speedStars: Int get() = ((topSpeed - 14f) / 6f).toInt().coerceIn(1, 5)
}
