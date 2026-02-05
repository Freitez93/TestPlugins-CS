// use an integer for version numbers
version = 2

cloudstream {
    // All of these properties are optional, you can safely remove them
    authors     = listOf("Freitez93")
    language    = "ja"
    description = "Best Japan AV, free forever, high speed, no lag, over 100,000 videos, daily update."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of available types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf("NSFW")
    iconUrl = "https://missav.ws/missav/logo-square.png"
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.13.0")
}