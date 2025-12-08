@file:Suppress("UnstableApiUsage")
import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 2

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        val properties = Properties()
        // Cargar la API Key desde local.properties, variable de entorno o propiedad de Gradle
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        // Exponer la API key como BuildConfig.TMDB_API
        buildConfigField("String", "TMDB_API", "\"${properties.getProperty("TMDB_API")}\"")
    }
}

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "(Español Latino) Peliculas/Series/Doramas/Anime en PelisPlusHD.bz"
    language = "mx"
    authors = listOf("Freitez93")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "AsianDrama",
        "Anime",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=pelisplushd.bz&sz=%size%"
}