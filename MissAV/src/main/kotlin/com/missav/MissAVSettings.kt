package com.missav

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

object MissAVSettings {
    // =================================================================================================
    // Constants & Configuration
    // =================================================================================================
    private const val PREFS_PREFIX = "MissAV_"

    // Preference Keys
    private const val KEY_ALL_CATEGORIES_ORDER = "${PREFS_PREFIX}ALL_order"
    private const val KEY_CUSTOM_CATEGORIES = "${PREFS_PREFIX}CUSTOM_categories"
    private const val KEY_DELETED_CATEGORIES = "${PREFS_PREFIX}DELETED_categories"
    private const val KEY_USE_ITEM_BACKGROUND = "${PREFS_PREFIX}USE_ITEM_BACKGROUND"
    private const val KEY_LANG_IN_PAGE = "${PREFS_PREFIX}LANG_IN_PAGE"
    private const val KEY_TITLE_IN_CODE = "${PREFS_PREFIX}TITLE_IN_CODE"
    private const val KEY_SHOW_TRAILER = "${PREFS_PREFIX}SHOW_TRAILER"

    // Interface Colors
    private const val COLOR_BACKGROUND = "#15111B"
    private const val COLOR_PRIMARY = "#FF7396"
    private const val COLOR_FOCUS = "#60d5fa"
    private const val COLOR_BUTTON = "#404562"
    private const val COLOR_DELETE = "#f87171"
    private const val COLOR_SAVE = "#4ade80"
    private const val COLOR_DARK_GRAY = "#423a46"

    // Default Category List
    private val defaultCategories = listOf(
        // Predeterminados
        "/new" to "Recent Update",
        "/release" to "New Releases",
        "/uncensored-leak" to "Uncensored Leak",
        "/monthly-hot" to "Most Viewed - Month",
        "/weekly-hot" to "Most Viewed - Week",

        // Amateur
        "/siro" to "[Amateur] SIRO",
        "/luxu" to "[Amateur] LUXU",
        "/gana" to "[Amateur] GANA",
        "/maan" to "[Amateur] PRESTIGE PREMIUM",
        "/scute" to "[Amateur] S-CUTE",

        // Asia AV
        "/madou" to "[AsiaAV] Madou",
        "/klive" to "[AsiaAV] Korean Live",
        "/clive" to "[AsiaAV] Chinese Live",

        // Géneros
        "/genres/Uncensored%20Leak" to "Uncensored Leak",
        "/genres/Exclusive" to "Exclusive",
        "/genres/Creampie" to "Creampie",
        "/genres/Big%20Breasts" to "Big Breasts",
        "/genres/Individual" to "Individual",
        "/genres/Wife" to "Wife",
        "/genres/Mature%20Woman" to "Mature Woman",
        "/genres/Ordinary%20Person" to "Ordinary Person",
        "/genres/Pretty%20Girl" to "Pretty Girl",
        "/genres/Ride" to "Ride",
        "/genres/Oral%20Sex" to "Oral Sex",
        "/genres/Orgy" to "Orgy",
        "/genres/Slim%20Pixelated" to "Slim Pixelated",
        "/genres/4%20Hours%20Or%20More" to "4 Hours Or More",
        "/genres/Slut" to "Slut",
        "/genres/Collection" to "Collection",
        "/genres/High%20School%20Girl" to "High School Girl",
        "/genres/Squirting" to "Squirting",
        "/genres/Fetish" to "Fetish",
        "/genres/Selfie" to "Selfie",
        "/genres/Tit%20Job" to "Tit Job",
        "/genres/Planning" to "Planning",
        "/genres/Hit%20On%20Girls" to "Hit On Girls",
        "/genres/Sneak%20Shots" to "Sneak Shots",
        "/genres/Slim" to "Slim",
        "/genres/Bukkake" to "Bukkake",
        "/genres/Beautiful%20Breasts" to "Beautiful Breasts",
        "/genres/Masturbate" to "Masturbate",
        "/genres/Masturbation" to "Masturbation",
        "/genres/Restraint" to "Restraint",
        "/genres/Promiscuous" to "Promiscuous",
        "/genres/Lesbian" to "Lesbian",
        "/genres/Ntr" to "Ntr",
        "/genres/Sister" to "Sister",
        "/genres/Plot" to "Plot",
        "/genres/Cosplay" to "Cosplay",
        "/genres/Humiliation" to "Humiliation",
        "/genres/Documentary" to "Documentary",
        "/genres/Hot%20Girl" to "Hot Girl",
        "/genres/Ol" to "Ol",
        "/genres/Uniform" to "Uniform",
        "/genres/Fingering" to "Fingering",
        "/genres/Vibrator" to "Vibrator",
        "/genres/Adultery" to "Adultery",
        "/genres/Cunnilingus" to "Cunnilingus",
        "/genres/Delusion" to "Delusion",
        "/genres/Female%20College%20Student" to "Female College Student",
        "/genres/Sm" to "Sm",
        "/genres/Shame" to "Shame",
        "/genres/Anus" to "Anus",
        "/genres/Petite" to "Petite",
        "/genres/Shaving" to "Shaving",
        "/genres/Subjective%20Perspective" to "Subjective Perspective",
        "/genres/Prostitute" to "Prostitute",
        "/genres/Various%20Occupations" to "Various Occupations",
        "/genres/Mother" to "Mother",
        "/genres/Toy" to "Toy",
        "/genres/Promiscuity" to "Promiscuity",
        "/genres/Outdoor%20Exposure" to "Outdoor Exposure",
        "/genres/Butt%20Fetish" to "Butt Fetish",
        "/genres/Pantyhose" to "Pantyhose",
        "/genres/Debut" to "Debut",
        "/genres/Urinate" to "Urinate",
        "/genres/Dirty%20Talk" to "Dirty Talk",
        "/genres/Massage" to "Massage",
        "/genres/Underwear" to "Underwear",
        "/genres/Big%20Ass" to "Big Ass",
        "/genres/Forced%20Blowjob" to "Forced Blowjob",
        "/genres/Sailor%20Suit" to "Sailor Suit",
        "/genres/Swimsuit" to "Swimsuit",
        "/genres/Delivery%20Only" to "Delivery Only",
        "/genres/Female%20Teacher" to "Female Teacher",
        "/genres/Kimono" to "Kimono",
        "/genres/Swallow%20Sperm" to "Swallow Sperm",
        "/genres/Small%20Breasts" to "Small Breasts",
        "/genres/Elder%20Sister" to "Elder Sister",
        "/genres/Young%20Wife" to "Young Wife",
        "/genres/Nurse" to "Nurse",
        "/genres/Massage%20Oil" to "Massage Oil",
        "/genres/Group%20Bukkake" to "Group Bukkake",
        "/genres/Tied%20Up" to "Tied Up",
        "/genres/Fat%20Girl" to "Fat Girl",
        "/genres/Rejuvenation%20Massage" to "Rejuvenation Massage",
        "/genres/Short%20Skirt" to "Short Skirt",
        "/genres/Ultra%20Slim%20Pixelated" to "Ultra Slim Pixelated",
        "/genres/Contribution" to "Contribution",
        "/genres/Nice%20Ass" to "Nice Ass",
        "/genres/Foot%20Fetish" to "Foot Fetish",
        "/genres/Full%20Hd%20(Fhd)" to "Full Hd (Fhd)",
        "/genres/Glasses%20Girl" to "Glasses Girl",
        "/genres/Kiss" to "Kiss",
        "/genres/4K" to "4K",
        "/genres/Close%20Up" to "Close Up",
        "/genres/Big%20Breast%20Fetish" to "Big Breast Fetish",
        "/genres/Sportswear" to "Sportswear",
        "/genres/Virgin" to "Virgin",
        "/genres/Vibrating%20Egg" to "Vibrating Egg",
        "/genres/Aphrodisiac" to "Aphrodisiac",
        "/genres/Lesbian%20Kiss" to "Lesbian Kiss",
        "/genres/Mini%20Skirt" to "Mini Skirt",
        "/genres/White%20Skin" to "White Skin",
        "/genres/M%20Male" to "M Male",
        "/genres/Couple" to "Couple",
        "/genres/Hot%20Spring" to "Hot Spring",
        "/genres/Maid" to "Maid",
        "/genres/Face%20Ride" to "Face Ride",
        "/genres/Imprisonment" to "Imprisonment",
        "/genres/Footjob" to "Footjob",
        "/genres/Fighting" to "Fighting",
        "/genres/Tall%20Lady" to "Tall Lady",
        "/genres/Female%20Warrior" to "Female Warrior",
        "/genres/Artist" to "Artist",
        "/genres/Science%20Fiction" to "Science Fiction",
        "/genres/Mischief" to "Mischief",
        "/genres/Actress%20Collection" to "Actress Collection",
        "/genres/Married%20Woman" to "Married Woman",
        "/genres/Sweating" to "Sweating",
        "/genres/Stepmother" to "Stepmother",
        "/genres/Beautiful%20Legs" to "Beautiful Legs",
        "/genres/Private%20Teacher" to "Private Teacher",
        "/genres/Big%20Pennis" to "Big Pennis",
        "/genres/Super%20Breasts" to "Super Breasts",
        "/genres/Advertising%20Idol" to "Advertising Idol",
        "/genres/Torture" to "Torture",
        "/genres/Anal%20Sex" to "Anal Sex",
        "/genres/Black%20Hair" to "Black Hair",
        "/genres/Erotic%20Photo" to "Erotic Photo",
        "/genres/Widow" to "Widow",
        "/genres/Gym%20Suit" to "Gym Suit",
        "/genres/Cruel" to "Cruel",
        "/genres/Sexy" to "Sexy",
        "/genres/Car%20Sex" to "Car Sex",
        "/genres/Multiple%20Stories" to "Multiple Stories",
        "/genres/Campus%20Story" to "Campus Story",
        "/genres/3P,%204P" to "Threesome & Foursome",
        "/genres/Transgender" to "Transgender",
        "/genres/Female%20Doctor" to "Female Doctor",
        "/genres/In%20Love" to "In Love",
        "/genres/Fighter" to "Fighter",
        "/genres/Fantasy" to "Fantasy",
        "/genres/Pure" to "Pure",
        "/genres/Instant%20Sex" to "Instant Sex",
        "/genres/Missy" to "Missy",
        "/genres/Enema" to "Enema",
        "/genres/Dance" to "Dance",
        "/genres/Best,%20Omnibus" to "Best, Omnibus",
        "/genres/Whites" to "Whites",
        "/genres/Flight%20Attendant" to "Flight Attendant",
        "/genres/Harem" to "Harem",
        "/genres/Foreign%20Actress" to "Foreign Actress",
        "/genres/Physical%20Education" to "Physical Education",
        "/genres/Bronze" to "Bronze",
        "/genres/Female%20Investigator" to "Female Investigator",
        "/genres/Transsexuals" to "Transsexuals",
        "/genres/Model" to "Model",
        "/genres/Baby%20Face" to "Baby Face",
        "/genres/Doggy%20Style" to "Doggy Style",
        "/genres/Bitch" to "Bitch",
        "/genres/Bloomers" to "Bloomers",
        "/genres/One%20Piece%20Dress" to "One Piece Dress",
        "/genres/Knee%20Socks" to "Knee Socks",
        "/genres/Thanks%20Offering" to "Thanks Offering",
        "/genres/Cute%20Little%20Boy" to "Cute Little Boy",
        "/genres/Delivery-Only%20Amateur" to "Delivery-Only Amateur",
        "/genres/Other" to "Other",
        "/genres/Bubble%20Bath" to "Bubble Bath",
        "/genres/Tickle" to "Tickle",
        "/genres/Extreme%20Orgasm" to "Extreme Orgasm",
        "/genres/Breast%20Milk" to "Breast Milk",
        "/genres/M%20Female" to "M Female",
        "/genres/Pregnant%20Woman" to "Pregnant Woman",
        "/genres/Indie" to "Indie",
        "/genres/Drink%20Urine" to "Drink Urine",
        "/genres/Femdom%20Slave" to "Femdom Slave",
        "/genres/Heaven%20Tv" to "Heaven Tv",
        "/genres/Secretary" to "Secretary",
        "/genres/Insult" to "Insult",
        "/genres/Rape" to "Rape",
        "/genres/Thirty" to "Thirty",
        "/genres/Lolita" to "Lolita",
        "/genres/Female%20Boss" to "Female Boss",
        "/genres/Foreign%20Object%20Penetration" to "Foreign Object Penetration",
        "/genres/Hit%20On%20Boys" to "Hit On Boys",
        "/genres/Stool" to "Stool",
        "/genres/Hysteroscope" to "Hysteroscope",
        "/genres/Gang%20Rape" to "Gang Rape",
        "/genres/Anchorwoman" to "Anchorwoman",
        "/genres/High%20Quality%20Vr" to "High Quality Vr",
        "/genres/Similar" to "Similar",
        "/genres/Catwoman" to "Catwoman",
        "/genres/Bathtub" to "Bathtub",
        "/genres/Dildo" to "Dildo",
        "/genres/Limited%20Time" to "Limited Time",
        "/genres/Fist" to "Fist",
        "/genres/Dating" to "Dating",
        "/genres/Cuckold" to "Cuckold",
        "/genres/Original" to "Original",
        "/genres/Lecturer" to "Lecturer",
        "/genres/Esthetic%20Massage" to "Esthetic Massage",
        "/genres/Childhood" to "Childhood",
        "/genres/Uterus" to "Uterus",
        "/genres/Pregnant" to "Pregnant",
        "/genres/Entertainer" to "Entertainer",
        "/genres/Long%20Hair" to "Long Hair",
        "/genres/First%20Shot" to "First Shot",
        "/genres/Muscle" to "Muscle",
        "/genres/Outdoors" to "Outdoors",
        "/genres/Naked%20Apron" to "Naked Apron",
        "/genres/Male%20Squirting" to "Male Squirting",
        "/genres/Hotel%20Owner" to "Hotel Owner",
        "/genres/Molester" to "Molester",
        "/genres/Bunny%20Girl" to "Bunny Girl",
        "/genres/Travel" to "Travel",
        "/genres/Asian%20Actress" to "Asian Actress",
        "/genres/Tentacle" to "Tentacle",
        "/genres/Proud%20Pussy" to "Proud Pussy",
        "/genres/Subordinate%20Or%20Colleague" to "Subordinate Or Colleague",
        "/genres/With%20Bonus%20Video%20Only%20For%20Mgs" to "With Bonus Video Only For Mgs",
        "/genres/Business%20Clothing" to "Business Clothing",
        "/genres/Premature%20Ejaculation" to "Premature Ejaculation",
        "/genres/Friend" to "Friend",
        "/genres/Shame%20And%20Humiliation" to "Shame And Humiliation",
        "/genres/Short%20Hair" to "Short Hair",
        "/genres/Waitress" to "Waitress",
        "/genres/Clinic" to "Clinic",
        "/genres/Exposure" to "Exposure",
        "/genres/Kimono%20/%20Yukata" to "Kimono / Yukata",
        "/genres/Lewd%20Nasty%20Lady" to "Lewd Nasty Lady",
        "/genres/Bubble%20Socks" to "Bubble Socks",
        "/genres/Idol" to "Idol",
        "/genres/Time%20Stops" to "Time Stops"
    )

    private val defaultEnabledNames = setOf(
        "Recent Update",
        "New Releases",
        "Uncensored Leak",
        "Most Viewed - Month",
        "Most Viewed - Week"
    )

    // =================================================================================================
    // Global Settings Methods
    // =================================================================================================
    fun showSettingsDialog(activity: AppCompatActivity, onSave: () -> Unit) {
        SettingsManager(activity, onSave).show()
    }

    // --- Category Management ---
    fun getOrderedAndEnabledCategories(): List<Pair<String, String>> {
        val allCats = getAllCategories()
        val allCategoryNames = allCats.map { it.second }
        // Use default ordering logic
        val orderedNames = getOrderedCategories(KEY_ALL_CATEGORIES_ORDER, allCategoryNames)
        
        // Filter enabled and map back to Pair(URL, Name)
        return orderedNames.filter { isCategoryEnabled(it) }.mapNotNull { 
            name -> allCats.find { it.second == name } 
        }
    }

    // --- Obtener todas las categorías (incluyendo personalizadas) ---
    fun getAllCategories(): List<Pair<String, String>> {
        val deletedCategories = getDeletedCategories()
        val filteredDefaults = defaultCategories.filter { it.second !in deletedCategories }
        val customCategories = getCustomCategories()
        return filteredDefaults + customCategories
    }

    private fun getCustomCategories(): List<Pair<String, String>> {
        val json = getKey<String>(KEY_CUSTOM_CATEGORIES) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                obj.getString("url") to obj.getString("name")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun addCustomCategory(name: String, url: String) {
        var finalUrl = url.trim()
        if (!finalUrl.startsWith("http") && !finalUrl.startsWith("/")) {
            finalUrl = "/$finalUrl"
        }
        
        val currentCategories = getCustomCategories().toMutableList()
        currentCategories.add(finalUrl to name)
        
        val jsonArray = JSONArray()
        currentCategories.forEach { (catUrl, catName) ->
            jsonArray.put(JSONObject().apply {
                put("url", catUrl)
                put("name", catName)
            })
        }
        setKey(KEY_CUSTOM_CATEGORIES, jsonArray.toString())
    }

    private fun addCategoryToDeletedList(categoryName: String) {
        val deletedSet = getDeletedCategories().toMutableSet()
        deletedSet.add(categoryName)
        
        val jsonArray = JSONArray()
        deletedSet.forEach { jsonArray.put(it) }
        setKey(KEY_DELETED_CATEGORIES, jsonArray.toString())
    }

    fun deleteCategory(name: String) {
        // 1. Remove from custom categories if present (handles overrides an pure customs)
        val currentCustomCategories = getCustomCategories().toMutableList()
        val modified = currentCustomCategories.removeAll { it.second == name }
        
        if (modified) {
            if (currentCustomCategories.isEmpty()) {
                setKey(KEY_CUSTOM_CATEGORIES, null)
            } else {
                val jsonArray = JSONArray()
                currentCustomCategories.forEach { (url, catName) ->
                    jsonArray.put(JSONObject().apply {
                        put("url", url)
                        put("name", catName)
                    })
                }
                setKey(KEY_CUSTOM_CATEGORIES, jsonArray.toString())
            }
        }

        // 2. If it is also a default category, ensure it's in the deleted list (blocklist)
        if (defaultCategories.any { it.second == name }) {
            addCategoryToDeletedList(name)
        }
    }

    // --- Preferences Accessors ---
    fun useItemBackground(): Boolean = getKey<String>(KEY_USE_ITEM_BACKGROUND) == "true"
    fun setUseItemBackground(use: Boolean) = setKey(KEY_USE_ITEM_BACKGROUND, use.toString())

    fun getLangInPage(): String = getKey<String>(KEY_LANG_IN_PAGE) ?: "en"
    fun setLangInPage(lang: String) = setKey(KEY_LANG_IN_PAGE, lang)

    fun getTitleInCode(): Boolean = getKey<String>(KEY_TITLE_IN_CODE) == "true"
    fun setTitleInCode(enabled: Boolean) = setKey(KEY_TITLE_IN_CODE, enabled.toString())

    fun getShowTrailer(): Boolean = getKey<String>(KEY_SHOW_TRAILER) == "true"
    fun setShowTrailer(enabled: Boolean) = setKey(KEY_SHOW_TRAILER, enabled.toString())

    fun isCategoryEnabled(categoryName: String): Boolean {
        val key = "${PREFS_PREFIX}${categoryName}_enabled"
        return when (getKey<String>(key)) {
            "true" -> true
            "false" -> false
            else -> categoryName in defaultEnabledNames
        }
    }

    fun setCategoryEnabled(categoryName: String, enabled: Boolean) {
        setKey("${PREFS_PREFIX}${categoryName}_enabled", enabled.toString())
    }

    // --- Reset Methods ---

    fun resetAppearanceSettings() {
        setKey(KEY_USE_ITEM_BACKGROUND, null)
        setKey(KEY_LANG_IN_PAGE, null)
        setKey(KEY_TITLE_IN_CODE, null)
        setKey(KEY_SHOW_TRAILER, null)
    }

    fun resetCategoriesOnly() {
        setKey(KEY_ALL_CATEGORIES_ORDER, null)
        setKey(KEY_DELETED_CATEGORIES, null)
        setKey(KEY_CUSTOM_CATEGORIES, null)
        // Reset individual enabled states
        getAllCategories().forEach { (_, name) -> 
            setKey("${PREFS_PREFIX}${name}_enabled", null) 
        }
    }

    fun resetAllSettings() {
        resetCategoriesOnly()
        resetAppearanceSettings()
    }

    // --- Helper Methods ---

    private fun getDeletedCategories(): Set<String> {
        val json = getKey<String>(KEY_DELETED_CATEGORIES) ?: return emptySet()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    // Generic list ordering based on a preference key
    fun getOrderedCategories(key: String, defaultList: List<String>): List<String> {
        val savedOrderJson = getKey<String>(key) ?: return defaultList
        
        return try {
            val jsonArray = JSONArray(savedOrderJson)
            val savedList = (0 until jsonArray.length()).map { jsonArray.getString(it) }
            val defaultSet = defaultList.toSet()
            
            // Keep saved items that are still valid (exist in defaultList)
            val validSavedList = savedList.filter { it in defaultSet }
            // Add any new items that weren't in the saved list
            val newItems = defaultList.filter { it !in validSavedList }
            
            validSavedList + newItems
        } catch (e: Exception) {
            defaultList
        }
    }

    fun setOrderedCategories(key: String, list: List<String>) {
        val jsonArray = JSONArray().apply { 
            list.forEach { put(it) } 
        }
        setKey(key, jsonArray.toString())
    }

    // =================================================================================================
    // Settings UI Manager
    // =================================================================================================
    private class SettingsManager(val context: AppCompatActivity, val onSave: () -> Unit) {
        
        // IDs for view navigation
        private val ID_BTN_CATEGORIES = 1001
        private val ID_BTN_LAYOUT = 1002
        private val ID_RECYCLER_VIEW = 1003

        private val mainLayout: LinearLayout
        private val categoriesView: View
        private val layoutView: View
        
        private lateinit var btnCategories: Button
        private lateinit var btnLayout: Button
        private lateinit var recyclerView: RecyclerView
        private lateinit var adapter: CategoryAdapter

        init {
            // Initialize main container
            mainLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor(COLOR_BACKGROUND))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            // Create sub-views (tabs)
            categoriesView = createCategoriesView()
            layoutView = createLayoutView()
        }

        fun show() {
            val dialog = AlertDialog.Builder(context)
                .setView(createRootView())
                .setCancelable(true)
                .create()

            // Dialog Window Styling
            dialog.window?.apply {
                setBackgroundDrawable(GradientDrawable().apply {
                    setColor(Color.parseColor(COLOR_BACKGROUND))
                    cornerRadius = 16f
                })
                
                // Set explicit size (90% width, 80% height)
                // Set explicit width (90%), but allow height to wrap content
                val displayMetrics = context.resources.displayMetrics
                val width = (displayMetrics.widthPixels * 0.90).toInt()
                setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            
            dialog.show()

            // Setup Save button internal logic
            mainLayout.findViewWithTag<Button>("SAVE_BTN")?.apply {
                nextFocusUpId = ID_RECYCLER_VIEW
                setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle("Reiniciar Aplicación")
                        .setMessage("¿Deseas reiniciar la aplicación para aplicar los cambios?")
                        .setPositiveButton("Sí") { _, _ ->
                            onSave()
                            dialog.dismiss()
                            restartApp()
                        }
                        .setNegativeButton("No") { _, _ ->
                            onSave()
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }

        private fun restartApp() {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            val componentName = intent?.component
            if (componentName != null) {
                val restartIntent = Intent.makeRestartActivityTask(componentName)
                context.startActivity(restartIntent)
                Runtime.getRuntime().exit(0)
            }
        }

        private fun createRootView(): View {
            // Clear any previous adds just in case
            mainLayout.removeAllViews() 

            // 1. Header (Tabs)
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(10, 10, 10, 10)
            }

            btnCategories = createTabButton("Categorías", ID_BTN_CATEGORIES) { switchTab(0) }
            btnLayout = createTabButton("Ajustes", ID_BTN_LAYOUT) { switchTab(1) }
            
            header.addView(btnCategories)
            header.addView(btnLayout)
            mainLayout.addView(header)

            // 2. Container for Content
            val container = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            container.addView(categoriesView)
            container.addView(layoutView)
            mainLayout.addView(container)

            // 3. Footer (Save Button)
            val footer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 10, 20, 20)
                gravity = Gravity.CENTER
            }
            
            val btnSave = createStyledButton("Guardar Y Salir", COLOR_SAVE).apply {
                tag = "SAVE_BTN"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            footer.addView(btnSave)
            mainLayout.addView(footer)

            // Default State
            switchTab(0)
            return mainLayout
        }

        private fun switchTab(index: Int) {
            val isCat = (index == 0)
            categoriesView.visibility = if (isCat) View.VISIBLE else View.GONE
            layoutView.visibility = if (!isCat) View.VISIBLE else View.GONE
            
            val activeColor = Color.parseColor(COLOR_PRIMARY)
            val inactiveColor = Color.TRANSPARENT
            
            btnCategories.background = createButtonDrawable(if (isCat) activeColor else inactiveColor)
            btnLayout.background = createButtonDrawable(if (!isCat) activeColor else inactiveColor)
        }

        // --- Categories Tab UI ---
        private fun createCategoriesView(): View {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 20, 20, 20)
                gravity = Gravity.CENTER
            }

            // RecyclerView Setup
            adapter = CategoryAdapter(context) { name, enabled -> setCategoryEnabled(name, enabled) }
            
            // Custom RecyclerView with Max Height constraint (e.g. 60% of screen)
            recyclerView = object : RecyclerView(context) {
                override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                    val maxH = (context.resources.displayMetrics.heightPixels * 0.60).toInt()
                    val newHeightSpec = MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST)
                    super.onMeasure(widthSpec, newHeightSpec)
                }
            }.apply {
                id = ID_RECYCLER_VIEW
                layoutManager = LinearLayoutManager(context)
                this.adapter = this@SettingsManager.adapter
                setBackgroundColor(Color.parseColor(COLOR_BACKGROUND))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                isFocusable = false
                isFocusableInTouchMode = false
            }

            // Add Category Button
            val addCategoryButton = createStyledButton("Agregar categoría", COLOR_BUTTON).apply {
                tag = "ADD_BTN"
                (layoutParams as LinearLayout.LayoutParams).topMargin = 20
                isFocusable = false
                isFocusableInTouchMode = false
                setOnClickListener { showAddCategoryDialog() }
            }

            refreshCategoryList()
            
            layout.addView(recyclerView)
            layout.addView(addCategoryButton)
            return layout
        }

        private fun refreshCategoryList() {
            val allCategories = getAllCategories()
            val allCategoryNames = allCategories.map { it.second }
            val orderedList = getOrderedCategories(KEY_ALL_CATEGORIES_ORDER, allCategoryNames)
            adapter.setOrderKey(KEY_ALL_CATEGORIES_ORDER)
            adapter.setRecyclerView(recyclerView)
            adapter.setList(orderedList)
        }

        private fun showAddCategoryDialog() {
            showCategoryDialog("Agregar nueva categoría") { name, url ->
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    addCustomCategory(name, url)
                    setCategoryEnabled(name, true)
                    refreshCategoryList()
                }
            }
        }

        private fun showEditCategoryDialog(categoryName: String) {
            val allCats = getAllCategories()
            val existingUrl = allCats.find { it.second == categoryName }?.first ?: ""
            showCategoryDialog("Editar categoría", categoryName, existingUrl, 
                onSave = { name, url ->
                    if (name.isNotEmpty() && url.isNotEmpty()) {
                        deleteCategory(categoryName)
                        addCustomCategory(name, url)
                        // Preserve enabled state
                        setCategoryEnabled(name, isCategoryEnabled(categoryName))
                        refreshCategoryList()
                    }
                },
                onDelete = {
                    deleteCategory(categoryName)
                    refreshCategoryList()
                }
            )
        }
        
        // Unified Dialog helper for Add/Edit
        private fun showCategoryDialog(
            title: String, 
            defaultName: String = "", 
            defaultUrl: String = "", 
            onDelete: (() -> Unit)? = null,
            onSave: (String, String) -> Unit
        ) {
            val nameInput = createDialogInput("Ej: Wife", defaultName)
            val urlInput = createDialogInput("Ej: /genres/Wife", defaultUrl)
            val dialogLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 40)
                setBackgroundColor(Color.parseColor(COLOR_BACKGROUND))
                
                addView(TextView(context).apply { text = "Nombre"; setTextColor(Color.WHITE) })
                addView(nameInput)
                addView(TextView(context).apply { text = "Ruta API"; setTextColor(Color.WHITE); setPadding(0, 30, 0, 0) })
                addView(urlInput)
            }
            
            val builder = AlertDialog.Builder(context)
                .setTitle(title)
                .setView(dialogLayout)
                .setPositiveButton(if (onDelete == null) "Agregar" else "Guardar") { _, _ ->
                    onSave(nameInput.text.toString().trim(), urlInput.text.toString().trim())
                }
                .setNegativeButton("Cancelar", null)

            if (onDelete != null) {
                builder.setNeutralButton("Eliminar") { dialog, _ -> 
                    onDelete()
                    dialog.dismiss() 
                }
            }
            builder.show()
        }

        private fun createDialogInput(hintText: String, defaultText: String): EditText {
            return EditText(context).apply {
                hint = hintText
                setText(defaultText)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor(COLOR_PRIMARY))
            }
        }

        // --- Settings/Layout Tab UI ---
        private fun createLayoutView(): View {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
            }

            // 1. Language Spinner
            layout.addView(createSectionLabel("Idioma de la página"))
            val langOptions = listOf("zh", "en", "pt", "ja", "cn", "ko", "de", "fr")
            val langDisplayOptions = listOf(
                "🇭🇰 Default", "🇺🇸 English", "🇵🇹 Português", "🇨🇳 日本語", "🇯🇵 简体中文",
                "🇰🇷 Coreano", "🇩🇪 Deutsch", "🇫🇷 Français"
            )
            val currentLang = getLangInPage()
            val safeIndex = max(0, langOptions.indexOf(currentLang))
            val langSpinner = Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, langDisplayOptions).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                setSelection(safeIndex)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 30 }
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        setLangInPage(langOptions[position])
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
            layout.addView(langSpinner)
            // 2. Switches
            layout.addView(createSwitchOption("Código como título", getTitleInCode()) { setTitleInCode(it) })
            layout.addView(createSwitchOption("Mostrar trailer", getShowTrailer()) { setShowTrailer(it) })
            // 3. Reset Button
            layout.addView(createStyledButton("Restablecer ajustes", COLOR_DELETE).apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = 20
                isFocusable = false
                isFocusableInTouchMode = false
                setOnClickListener { showResetDialog() }
            })
            return layout
        }

        // Helper for Switch rows
        private fun createSwitchOption(label: String, initialState: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 30 }
            }

            val text = TextView(context).apply {
                this.text = label
                textSize = 16f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val switchView = Switch(context).apply {
                isChecked = initialState
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
            }

            container.addView(text)
            container.addView(switchView)
            return container
        }

        private fun showResetDialog() {
            AlertDialog.Builder(context)
                .setTitle("Restablecer ajustes")
                .setMessage("Todas las configuraciones volverán a los valores predeterminados.")
                .setPositiveButton("Sí") { _, _ ->
                    resetAllSettings()
                    refreshCategoryList()
                    adapter.notifyDataSetChanged()
                }
                .setNegativeButton("No", null)
                .show()
        }

        // --- View Factory Helpers ---

        private fun createTabButton(text: String, idVal: Int, onClick: () -> Unit): Button {
            return Button(context).apply {
                id = idVal
                this.text = text
                setTextColor(Color.WHITE)
                textSize = 13f
                minHeight = 0
                minimumHeight = 0
                setPadding(15, 10, 15, 10)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 5 }
                isFocusable = false
                isFocusableInTouchMode = false
                setOnClickListener { onClick() }
            }
        }

        private fun createStyledButton(text: String, colorHex: String): Button {
            return Button(context).apply {
                this.text = text
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = createButtonDrawable(Color.parseColor(colorHex))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        private fun createSectionLabel(text: String): TextView {
            return TextView(context).apply {
                this.text = text
                textSize = 16f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 10)
            }
        }

        private fun createButtonDrawable(color: Int): StateListDrawable {
            return StateListDrawable().apply {
                val cornerRadius = 16f
                val shape = GradientDrawable().apply { 
                    setColor(color)
                    this.cornerRadius = cornerRadius
                    setStroke(2, Color.TRANSPARENT)
                }
                val focusedShape = GradientDrawable().apply { 
                    setColor(Color.parseColor(COLOR_FOCUS))
                    this.cornerRadius = cornerRadius
                    setStroke(2, Color.WHITE)
                }
                addState(intArrayOf(android.R.attr.state_focused), focusedShape)
                addState(intArrayOf(), shape)
            }
        }

        // --- Recycler Adapter (Inner Class) ---
        private inner class CategoryAdapter(
            private val ctx: Context,
            private val onCheckedChange: (String, Boolean) -> Unit
        ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

            private val items = mutableListOf<String>()
            private var orderKey: String = ""
            private var recyclerViewRef: RecyclerView? = null

            // Generate stable IDs once
            private val CHECKBOX_ID = View.generateViewId()
            private val UP_BUTTON_ID = View.generateViewId()
            private val DOWN_BUTTON_ID = View.generateViewId()
            private val DELETE_BUTTON_ID = View.generateViewId()

            fun setOrderKey(key: String) { orderKey = key }
            fun getSelectedCount(): Int = items.count { isCategoryEnabled(it) }
            fun setRecyclerView(rv: RecyclerView) { recyclerViewRef = rv }

            fun setList(newList: List<String>) {
                val selected = newList.filter { isCategoryEnabled(it) }
                val unselected = newList.filter { !isCategoryEnabled(it) }.sortedBy { it.lowercase() }
                
                items.clear()
                items.addAll(selected)
                items.addAll(unselected)
                notifyDataSetChanged()

                // Restoration of focus to top item if possible
                recyclerViewRef?.post {
                    val rv = recyclerViewRef ?: return@post
                    if (items.isNotEmpty()) {
                        rv.scrollToPosition(0)
                        val vh = rv.findViewHolderForAdapterPosition(0)
                        (vh as? ViewHolder)?.checkBox?.requestFocus()
                    }
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                return ViewHolder(createItemLayout(parent.context))
            }

            override fun getItemCount() = items.size
            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                val name = items[position]
                val selectedCount = getSelectedCount()
                val isEnabled = position < selectedCount

                holder.checkBox.text = name
                holder.checkBox.isChecked = isEnabled

                // Listeners
                val toggleAction = {
                    val pos = holder.adapterPosition
                    if (pos != RecyclerView.NO_POSITION) handleToggle(items[pos], !isEnabled, pos)
                }

                holder.container.setOnClickListener { toggleAction() }
                holder.checkBox.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        handleToggle(items[pos], holder.checkBox.isChecked, pos)
                    }
                }

                holder.checkBox.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                        val pos = holder.adapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            val newState = !isCategoryEnabled(items[pos])
                            holder.checkBox.isChecked = newState
                            handleToggle(items[pos], newState, pos)
                            true
                        } else false
                    } else false
                }

                // Move Button Logic
                val canMoveUp = isEnabled && position > 0
                val canMoveDown = isEnabled && position < (selectedCount - 1)

                setupMoveButton(holder.upButton, isEnabled, canMoveUp, UP_BUTTON_ID) {
                    val fromPos = holder.adapterPosition
                    if (fromPos != RecyclerView.NO_POSITION) moveItem(fromPos, fromPos - 1, UP_BUTTON_ID)
                }

                setupMoveButton(holder.downButton, isEnabled, canMoveDown, DOWN_BUTTON_ID) {
                    val fromPos = holder.adapterPosition
                    if (fromPos != RecyclerView.NO_POSITION) moveItem(fromPos, fromPos + 1, DOWN_BUTTON_ID)
                }
                holder.downButton.nextFocusRightId = DELETE_BUTTON_ID // Explicit link

                // Delete Button Logic
                holder.deleteButton.apply {
                    visibility = View.VISIBLE
                    setOnClickListener {
                        val pos = holder.adapterPosition
                        if (pos != RecyclerView.NO_POSITION) showEditCategoryDialog(items[pos])
                    }
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                            callOnClick()
                            true
                        } else false
                    }
                }
            }

            private fun setupMoveButton(btn: Button, shouldShow: Boolean, canMove: Boolean, nextFocusId: Int? = null, action: () -> Unit) {
                btn.apply {
                    visibility = if (shouldShow) View.VISIBLE else View.INVISIBLE
                    this.isEnabled = canMove
                    alpha = if (canMove) 1.0f else 0.3f
                    isFocusable = canMove
                    isFocusableInTouchMode = canMove
                    if (nextFocusId != null) this.nextFocusRightId = nextFocusId
                    
                    setOnClickListener { action() }
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                            if (isEnabled) callOnClick()
                            true
                        } else false
                    }
                }
            }

            private fun handleToggle(categoryName: String, newState: Boolean, fromPos: Int) {
                setCategoryEnabled(categoryName, newState)
                onCheckedChange(categoryName, newState)

                val selectedCountBefore = items.count { it != categoryName && isCategoryEnabled(it) }
                val item = items.removeAt(fromPos)

                val targetPosition = if (newState) {
                    selectedCountBefore
                } else {
                    val unselectedItems = items.filter { !isCategoryEnabled(it) }
                    // Find correct alphabetical insertion point among disabled items
                    val insertIndex = unselectedItems.indexOfFirst { it.lowercase() > categoryName.lowercase() }
                    if (insertIndex == -1) items.size else (selectedCountBefore + insertIndex)
                }

                items.add(targetPosition, item)
                notifyItemMoved(fromPos, targetPosition)
                
                // Update range to refresh visually affected items (button states etc.)
                val start = min(fromPos, targetPosition)
                val end = max(fromPos, targetPosition)
                notifyItemRangeChanged(start, end - start + 1)

                setOrderedCategories(orderKey, items)
                ensureFocusOnPositionAndClearPressed(targetPosition)
            }

            private fun moveItem(from: Int, to: Int, focusTargetId: Int = CHECKBOX_ID) {
                if (from == RecyclerView.NO_POSITION || from == to) return
                
                val item = items.removeAt(from)
                items.add(to, item)

                notifyItemMoved(from, to)
                val start = min(from, to)
                val end = max(from, to)
                notifyItemRangeChanged(start, end - start + 1)

                setOrderedCategories(orderKey, items)
                
                // Ensure focus follows the item
                ensureFocusOnPositionAndClearPressed(to, focusTargetId)
            }

            /**
             * Recursive focus request to handle RecyclerView layout pass delays.
             * Necessary for D-Pad navigation continuity.
             */
            private fun ensureFocusOnPositionAndClearPressed(position: Int, focusTargetId: Int = CHECKBOX_ID) {
                val rv = recyclerViewRef ?: return
                rv.scrollToPosition(position)
                rv.post {
                    val vh = rv.findViewHolderForAdapterPosition(position) as? ViewHolder
                    if (vh != null) {
                        vh.itemView.findViewById<View>(focusTargetId)?.requestFocus()
                        // Clear press states to prevent visual artifacts
                        listOf(vh.container, vh.checkBox, vh.upButton, vh.downButton, vh.deleteButton, vh.itemView)
                            .forEach { it.isPressed = false }
                    } else {
                        // Retry if viewholder not yet bound
                        rv.postDelayed({ ensureFocusOnPositionAndClearPressed(position, focusTargetId) }, 40)
                    }
                }
            }

            // --- View Creation ---
            inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
                val container: LinearLayout = view as LinearLayout
                val checkBox: CheckBox = view.findViewById(CHECKBOX_ID)
                val upButton: Button = view.findViewById(UP_BUTTON_ID)
                val downButton: Button = view.findViewById(DOWN_BUTTON_ID)
                val deleteButton: Button = view.findViewById(DELETE_BUTTON_ID)
            }

            private fun createItemLayout(context: Context): LinearLayout {
                return LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    
                    val paddingH = 12.toPx(context)
                    val paddingV = 4.toPx(context)
                    setPadding(paddingH, paddingV, paddingH, paddingV)
                    
                    setBackgroundColor(if (useItemBackground()) Color.parseColor(COLOR_DARK_GRAY) else Color.TRANSPARENT)
                    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    
                    addView(createFocusableCheckBox(context))
                    addView(createButtonContainer(context))
                }
            }

            private fun createFocusableCheckBox(context: Context): CheckBox {
                val checkedColor = Color.parseColor(COLOR_PRIMARY)
                val focusColor = Color.parseColor(COLOR_FOCUS)
                
                return CheckBox(context).apply {
                    id = CHECKBOX_ID
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                    
                    // State List for Tint (Focused, Checked, etc)
                    buttonTintList = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_focused, android.R.attr.state_checked), 
                            intArrayOf(android.R.attr.state_focused, -android.R.attr.state_checked), 
                            intArrayOf(-android.R.attr.state_checked), 
                            intArrayOf(android.R.attr.state_checked)
                        ),
                        intArrayOf(focusColor, focusColor, Color.GRAY, checkedColor)
                    )
                    
                    setBackgroundColor(Color.TRANSPARENT)
                    setTextColor(Color.WHITE)
                    setOnFocusChangeListener { _, hasFocus -> setTextColor(if (hasFocus) focusColor else Color.WHITE) }
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
            }

            private fun createButtonContainer(context: Context): LinearLayout {
                val buttonSize = 40.toPx(context)
                val buttonMargin = 4.toPx(context)

                return LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isFocusable = false
                    isFocusableInTouchMode = false
                    
                    addView(createIconLabelButton(context, "▲", UP_BUTTON_ID).apply {
                        layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply { marginEnd = buttonMargin }
                    })
                    addView(createIconLabelButton(context, "▼", DOWN_BUTTON_ID).apply {
                        layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply { marginStart = buttonMargin }
                    })
                    addView(createIconLabelButton(context, "📝", DELETE_BUTTON_ID, COLOR_DELETE).apply {
                        layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply { marginStart = buttonMargin }
                    })
                }
            }

            private fun createIconLabelButton(context: Context, symbol: String, idVal: Int, defaultColor: String = COLOR_PRIMARY): Button {
                val focusColor = Color.parseColor(COLOR_FOCUS)
                return Button(context).apply {
                    id = idVal
                    text = symbol
                    textSize = 14f
                    setBackgroundColor(Color.TRANSPARENT)
                    setTextColor(Color.parseColor(defaultColor))
                    
                    setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus && isEnabled) {
                            setBackgroundColor(focusColor)
                            setTextColor(Color.BLACK)
                        } else {
                            setBackgroundColor(Color.TRANSPARENT)
                            setTextColor(if (isEnabled) Color.parseColor(defaultColor) else Color.GRAY)
                        }
                    }
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
            }

            private fun Int.toPx(context: Context): Int {
                return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, this.toFloat(),
                    context.resources.displayMetrics
                ).toInt()
            }
        }
    }
}