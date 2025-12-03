// use an integer for version numbers
version = 3


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "(Español Latino) Anime/AnimeMovie/OVA en español latino y subtitulado"
    language    = "mx"
    authors = listOf("Freitez93")

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
    tvTypes = listOf("AnimeMovie","Anime","OVA")
    iconUrl = "https://www.google.com/s2/favicons?domain=animeflv.one&sz=%size%"

    isCrossPlatform = true
}