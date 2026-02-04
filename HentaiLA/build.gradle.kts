// use an integer for version numbers
version = 3

cloudstream {
    // All of these properties are optional, you can safely remove them
    authors     = listOf("Freitez93")
    language    = "es"
    description = "HentaiLa, Disfruta del mejor hentai en español, sin censura y organizado por categorías"

    /** 
     * Status int as the following: 
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status      = 1

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of available types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes     = listOf("NSFW")
    iconUrl     = "https://i.postimg.cc/RFw58jKv/hentaila.png"
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.13.0")
}