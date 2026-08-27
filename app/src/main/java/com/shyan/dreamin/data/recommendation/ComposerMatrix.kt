package com.shyan.dreamin.data.recommendation

object ComposerMatrix {

    // Universe 1: Vintage & 90s Golden Era Legends
    private val VINTAGE_CLASSIC_CLUSTER = listOf(
        "S. P. Balasubrahmanyam",
        "K. S. Chithra",
        "Ilaiyaraaja",
        "A. R. Rahman",
        "Hariharan",
        "Swarnalatha",
        "Unni Menon",
        "Mano",
        "K. J. Yesudas",
        "S. Janaki",
        "Deva",
        "Vidyasagar",
        "S. A. Rajkumar",
        "Sujatha"
    )

    // Universe 2: Melodic 2000s Titans
    private val MELODIC_2000S_CLUSTER = listOf(
        "Harris Jayaraj",
        "Yuvan Shankar Raja",
        "A. R. Rahman",
        "Karthik",
        "Haricharan",
        "Naresh Iyer",
        "D. Imman",
        "Shreya Ghoshal",
        "Chinmayi",
        "Vijay Prakash",
        "Srinivas",
        "Unnikrishnan"
    )

    // Universe 3: Modern Indie / Romantic Pop Creators
    private val MODERN_INDIE_CLUSTER = listOf(
        "Sai Abhyankkar",
        "Pradeep Kumar",
        "Sid Sriram",
        "Sean Roldan",
        "Leon James",
        "Justin Prabhakaran",
        "Govind Vasantha",
        "Dhibu Ninan Thomas",
        "Stephen Zechariah"
    )

    // Universe 4: High-Energy / Kuthu Creators
    private val PARTY_KUTHU_CLUSTER = listOf(
        "Anirudh Ravichander",
        "Hiphop Tamizha",
        "Santhosh Narayanan",
        "G. V. Prakash Kumar",
        "Anthony Daasan",
        "Gana Bala",
        "Arivu",
        "Asal Kolaar"
    )

    fun getSynergyArtists(primaryArtist: String, vibe: SongVibe): List<String> {
        val clean = primaryArtist.lowercase().trim()

        return when (vibe) {
            SongVibe.VINTAGE_90S_80S_CLASSIC -> {
                (VINTAGE_CLASSIC_CLUSTER.filterNot { clean.contains(it.lowercase().split(" ").first()) }).shuffled().take(3)
            }
            SongVibe.MELODIC_2000S_GOLDEN -> {
                (MELODIC_2000S_CLUSTER.filterNot { clean.contains(it.lowercase().split(" ").first()) }).shuffled().take(3)
            }
            SongVibe.HIGH_ENERGY_PARTY_KUTHU -> {
                (PARTY_KUTHU_CLUSTER.filterNot { clean.contains(it.lowercase().split(" ").first()) }).shuffled().take(3)
            }
            SongVibe.LATE_NIGHT_CHILL, SongVibe.MODERN_INDIE_ROMANTIC -> {
                (MODERN_INDIE_CLUSTER.filterNot { clean.contains(it.lowercase().split(" ").first()) }).shuffled().take(3)
            }
        }
    }
}
