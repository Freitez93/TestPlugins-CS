// use an integer for version numbers
version = 2

cloudstream {
    // All of these properties are optional, you can safely remove them
    description = "AnimeAV1 - Ver Anime en HD y Sub-Dub Español"
    language    = "mx"
    authors     = listOf("Freitez93")

    /** 
     * Status int as the following: 
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of available types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf("AnimeMovie", "Anime", "OVA")
    iconUrl = "https://i.postimg.cc/WzQZFDPC/Anime-AV1.png"
    isCrossPlatform = true
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.13.0")
}