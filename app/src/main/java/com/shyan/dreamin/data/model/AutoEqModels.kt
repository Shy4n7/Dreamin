package com.shyan.dreamin.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class HeadphoneProfile(
    val id: String,
    val brand: String,
    val model: String,
    val target: String,
    val aliases: List<String>,
    /**
     * Map of frequency in Hz to gain adjustment in millibels (e.g. 60 to 250 = +2.5 dB, 3600 to -150 = -1.5 dB)
     */
    val frequencyGainsMb: Map<Int, Int>
) {
    val displayName: String get() = "$brand $model"
}

object AutoEqCatalog {
    val PROFILES: List<HeadphoneProfile> = listOf(
        // --- Apple ---
        HeadphoneProfile(
            id = "apple_airpods_pro_2",
            brand = "Apple",
            model = "AirPods Pro 2",
            target = "Harman In-Ear 2019",
            aliases = listOf("airpods pro", "airpods pro 2", "airpodspro"),
            frequencyGainsMb = mapOf(
                60 to 180, 125 to 100, 250 to -60, 500 to -40, 1000 to 0,
                2000 to 150, 4000 to -120, 8000 to 200, 16000 to -80
            )
        ),
        HeadphoneProfile(
            id = "apple_airpods_pro",
            brand = "Apple",
            model = "AirPods Pro",
            target = "Harman In-Ear 2019",
            aliases = listOf("airpods pro 1", "airpodspro1"),
            frequencyGainsMb = mapOf(
                60 to 220, 125 to 140, 250 to -80, 500 to 20, 1000 to 60,
                2000 to 180, 4000 to -160, 8000 to 180, 16000 to -100
            )
        ),
        HeadphoneProfile(
            id = "apple_airpods_3",
            brand = "Apple",
            model = "AirPods 3",
            target = "Harman In-Ear 2019",
            aliases = listOf("airpods 3", "airpods (3rd generation)"),
            frequencyGainsMb = mapOf(
                60 to 350, 125 to 200, 250 to 80, 500 to -60, 1000 to 40,
                2000 to 120, 4000 to -80, 8000 to 140, 16000 to 50
            )
        ),
        HeadphoneProfile(
            id = "apple_airpods_max",
            brand = "Apple",
            model = "AirPods Max",
            target = "Harman Over-Ear 2018",
            aliases = listOf("airpods max", "airpodsmax"),
            frequencyGainsMb = mapOf(
                60 to 80, 125 to 20, 250 to -140, 500 to -80, 1000 to 20,
                2000 to 100, 4000 to 220, 8000 to -180, 16000 to 120
            )
        ),

        // --- Sony ---
        HeadphoneProfile(
            id = "sony_wh_1000xm5",
            brand = "Sony",
            model = "WH-1000XM5",
            target = "Harman Over-Ear 2018",
            aliases = listOf("wh-1000xm5", "1000xm5", "sony wh-1000xm5"),
            frequencyGainsMb = mapOf(
                60 to -180, 125 to -240, 250 to -180, 500 to 40, 1000 to 120,
                2000 to 160, 4000 to 80, 8000 to 140, 16000 to -60
            )
        ),
        HeadphoneProfile(
            id = "sony_wh_1000xm4",
            brand = "Sony",
            model = "WH-1000XM4",
            target = "Harman Over-Ear 2018",
            aliases = listOf("wh-1000xm4", "1000xm4", "sony wh-1000xm4"),
            frequencyGainsMb = mapOf(
                60 to -240, 125 to -320, 250 to -200, 500 to 60, 1000 to 140,
                2000 to 180, 4000 to 120, 8000 to 160, 16000 to -100
            )
        ),
        HeadphoneProfile(
            id = "sony_wf_1000xm5",
            brand = "Sony",
            model = "WF-1000XM5",
            target = "Harman In-Ear 2019",
            aliases = listOf("wf-1000xm5", "wf1000xm5"),
            frequencyGainsMb = mapOf(
                60 to 40, 125 to -80, 250 to -120, 500 to 20, 1000 to 80,
                2000 to 120, 4000 to 180, 8000 to -60, 16000 to 40
            )
        ),
        HeadphoneProfile(
            id = "sony_wf_1000xm4",
            brand = "Sony",
            model = "WF-1000XM4",
            target = "Harman In-Ear 2019",
            aliases = listOf("wf-1000xm4", "wf1000xm4"),
            frequencyGainsMb = mapOf(
                60 to -80, 125 to -160, 250 to -140, 500 to 40, 1000 to 120,
                2000 to 160, 4000 to 200, 8000 to -80, 16000 to 60
            )
        ),

        // --- Samsung ---
        HeadphoneProfile(
            id = "samsung_galaxy_buds2_pro",
            brand = "Samsung",
            model = "Galaxy Buds2 Pro",
            target = "Harman In-Ear 2019",
            aliases = listOf("galaxy buds2 pro", "buds2 pro", "buds 2 pro"),
            frequencyGainsMb = mapOf(
                60 to 40, 125 to -20, 250 to -40, 500 to 0, 1000 to 20,
                2000 to 40, 4000 to -60, 8000 to 80, 16000 to -40
            )
        ),
        HeadphoneProfile(
            id = "samsung_galaxy_buds_fe",
            brand = "Samsung",
            model = "Galaxy Buds FE",
            target = "Harman In-Ear 2019",
            aliases = listOf("galaxy buds fe", "buds fe"),
            frequencyGainsMb = mapOf(
                60 to -60, 125 to -80, 250 to -40, 500 to 20, 1000 to 40,
                2000 to 60, 4000 to -80, 8000 to 120, 16000 to -20
            )
        ),

        // --- Sennheiser ---
        HeadphoneProfile(
            id = "sennheiser_hd_650",
            brand = "Sennheiser",
            model = "HD 650",
            target = "Harman Over-Ear 2018",
            aliases = listOf("hd 650", "hd650", "hd 6xx"),
            frequencyGainsMb = mapOf(
                60 to 320, 125 to 180, 250 to 40, 500 to -20, 1000 to 0,
                2000 to -60, 4000 to 140, 8000 to 220, 16000 to 180
            )
        ),
        HeadphoneProfile(
            id = "sennheiser_hd_560s",
            brand = "Sennheiser",
            model = "HD 560S",
            target = "Harman Over-Ear 2018",
            aliases = listOf("hd 560s", "hd560s"),
            frequencyGainsMb = mapOf(
                60 to 240, 125 to 100, 250 to -20, 500 to 0, 1000 to 0,
                2000 to 20, 4000 to -80, 8000 to -140, 16000 to 80
            )
        ),
        HeadphoneProfile(
            id = "sennheiser_momentum_4",
            brand = "Sennheiser",
            model = "Momentum 4",
            target = "Harman Over-Ear 2018",
            aliases = listOf("momentum 4", "momentum4 wireless"),
            frequencyGainsMb = mapOf(
                60 to -160, 125 to -220, 250 to -140, 500 to 40, 1000 to 80,
                2000 to 120, 4000 to 60, 8000 to 100, 16000 to -40
            )
        ),

        // --- Nothing & OnePlus ---
        HeadphoneProfile(
            id = "nothing_ear_2",
            brand = "Nothing",
            model = "Ear (2)",
            target = "Harman In-Ear 2019",
            aliases = listOf("nothing ear (2)", "nothing ear 2", "ear (2)"),
            frequencyGainsMb = mapOf(
                60 to -80, 125 to -100, 250 to -40, 500 to 40, 1000 to 60,
                2000 to 100, 4000 to -140, 8000 to 160, 16000 to 80
            )
        ),
        HeadphoneProfile(
            id = "oneplus_bullets_wireless_z2",
            brand = "OnePlus",
            model = "Bullets Wireless Z2",
            target = "Harman In-Ear 2019",
            aliases = listOf("oneplus bullets", "bullets wireless z2", "bullets wireless"),
            frequencyGainsMb = mapOf(
                60 to -340, 125 to -260, 250 to -180, 500 to 80, 1000 to 140,
                2000 to 180, 4000 to 120, 8000 to -100, 16000 to 60
            )
        ),

        // --- Audiophile IEMs ---
        HeadphoneProfile(
            id = "moondrop_chu_2",
            brand = "Moondrop",
            model = "Chu II",
            target = "VDSF Target",
            aliases = listOf("moondrop chu 2", "chu ii", "chu 2"),
            frequencyGainsMb = mapOf(
                60 to -60, 125 to -40, 250 to 20, 500 to 0, 1000 to 0,
                2000 to 40, 4000 to -80, 8000 to 60, 16000 to 40
            )
        ),
        HeadphoneProfile(
            id = "moondrop_aria",
            brand = "Moondrop",
            model = "Aria",
            target = "Harman In-Ear 2019",
            aliases = listOf("moondrop aria", "aria"),
            frequencyGainsMb = mapOf(
                60 to 40, 125 to 20, 250 to -40, 500 to 0, 1000 to 20,
                2000 to 60, 4000 to -40, 8000 to 80, 16000 to 20
            )
        ),
        HeadphoneProfile(
            id = "tangzu_waner",
            brand = "Tangzu",
            model = "Wan'er S.G",
            target = "Harman In-Ear 2019",
            aliases = listOf("tangzu wan'er", "waner", "wan'er"),
            frequencyGainsMb = mapOf(
                60 to 60, 125 to 40, 250 to -20, 500 to 0, 1000 to 20,
                2000 to 40, 4000 to -60, 8000 to 100, 16000 to -20
            )
        ),
        HeadphoneProfile(
            id = "7hz_zero",
            brand = "7Hz",
            model = "Salnotes Zero",
            target = "Harman In-Ear 2019",
            aliases = listOf("7hz zero", "salnotes zero", "zero 2"),
            frequencyGainsMb = mapOf(
                60 to 120, 125 to 60, 250 to -20, 500 to 0, 1000 to 20,
                2000 to 40, 4000 to -60, 8000 to 80, 16000 to -40
            )
        ),

        // --- Bose & Beats ---
        HeadphoneProfile(
            id = "bose_qc_45",
            brand = "Bose",
            model = "QuietComfort 45",
            target = "Harman Over-Ear 2018",
            aliases = listOf("qc45", "quietcomfort 45", "bose qc45"),
            frequencyGainsMb = mapOf(
                60 to 120, 125 to 40, 250 to -80, 500 to 20, 1000 to 60,
                2000 to 140, 4000 to -180, 8000 to -240, 16000 to 100
            )
        ),
        HeadphoneProfile(
            id = "beats_studio_buds",
            brand = "Beats",
            model = "Studio Buds",
            target = "Harman In-Ear 2019",
            aliases = listOf("beats studio buds", "studio buds"),
            frequencyGainsMb = mapOf(
                60 to -140, 125 to -180, 250 to -100, 500 to 40, 1000 to 80,
                2000 to 120, 4000 to -60, 8000 to 140, 16000 to -40
            )
        )
    )

    fun findMatchingProfile(deviceName: String): HeadphoneProfile? {
        if (deviceName.isBlank()) return null
        val cleanName = deviceName.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")
        return PROFILES.firstOrNull { profile ->
            profile.aliases.any { alias ->
                val cleanAlias = alias.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")
                cleanName.contains(cleanAlias)
            } || cleanName.contains(profile.model.lowercase())
        }
    }
}
