@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 3

android {
    namespace = "com.hdfull"
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "TMDB_API", "\"${properties.getProperty("TMDB_API")}\"")
    }
}

cloudstream {
    // All of these properties are optional, you can safely remove them
    description = "HDFull - (El Real) Tu lugar para ver peliculas y series"
    language = "es"
    authors = listOf("redblacker8", "Freitez93")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie", "TvSeries")

    iconUrl = "https://i.postimg.cc/HskwLYrk/hdfull.png"
    requiresResources = true
}

dependencies {
    // FIXME remove this when crossplatform is fully supported
    val cloudstream by configurations
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.leanback:leanback:1.2.0")
    cloudstream("com.lagradost:cloudstream3:pre-release")
}