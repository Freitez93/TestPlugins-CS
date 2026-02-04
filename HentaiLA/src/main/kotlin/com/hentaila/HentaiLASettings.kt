package com.hentaila

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

object HentaiLASettings {
    // =================================================================================================
    // Constants & Configuration
    // =================================================================================================
    private const val PREFS_PREFIX = "HentaiLA_"

    // Preference Keys
    private const val KEY_ALL_CATEGORIES_ORDER = "${PREFS_PREFIX}ALL_order"
    private const val KEY_CUSTOM_CATEGORIES = "${PREFS_PREFIX}CUSTOM_categories"
    private const val KEY_DELETED_CATEGORIES = "${PREFS_PREFIX}DELETED_categories"
    private const val KEY_USE_ITEM_BACKGROUND = "${PREFS_PREFIX}USE_ITEM_BACKGROUND"

    // Interface Colors
    private const val COLOR_BACKGROUND = "#15111B"
    private const val COLOR_PRIMARY = "#FF7396"
    private const val COLOR_FOCUS = "#60d5fa"
    private const val COLOR_BUTTON = "#29242d"
    private const val COLOR_DELETE = "#f87171"
    private const val COLOR_SAVE = "#4ade80"
    private const val COLOR_DARK_GRAY = "#423a46"

    // Lista de categorías por defecto (sin años)
    private val defaultCategories = listOf(
        // Predeterminados
        "/hub" to "Episodios Actualizados",
        "/catalogo" to "Contenido Aleatorio",
        "/catalogo?status=emision&order=latest_released" to "Últimos Estrenados",
        "/catalogo?status=emision&order=latest_added" to "Últimos Añadidos",

        // Géneros
        "/catalogo?genre=3d" to "3D",
        "/catalogo?genre=ahegao" to "Ahegao",
        "/catalogo?genre=anal" to "Anal",
        "/catalogo?genre=casadas" to "Casadas",
        "/catalogo?genre=chikan" to "Chikan",
        "/catalogo?genre=ecchi" to "Ecchi",
        "/catalogo?genre=enfermeras" to "Enfermeras",
        "/catalogo?genre=escolares" to "Escolares",
        "/catalogo?genre=futanari" to "Futanari",
        "/catalogo?genre=gore" to "Gore",
        "/catalogo?genre=hardcore" to "Hardcore",
        "/catalogo?genre=harem" to "Harem",
        "/catalogo?genre=incesto" to "Incesto",
        "/catalogo?genre=juegos-sexuales" to "Juegos Sexuales",
        "/catalogo?genre=suspenso" to "Suspenso",
        "/catalogo?genre=milfs" to "Milfs",
        "/catalogo?genre=maids" to "Maids",
        "/catalogo?genre=netorare" to "Netorare",
        "/catalogo?genre=ninfomania" to "Ninfomania",
        "/catalogo?genre=ninjas" to "Ninjas",
        "/catalogo?genre=orgias" to "Orgías",
        "/catalogo?genre=romance" to "Romance",
        "/catalogo?genre=shota" to "Shota",
        "/catalogo?genre=softcore" to "Softcore",
        "/catalogo?genre=succubus" to "Succubus",
        "/catalogo?genre=teacher" to "Teacher",
        "/catalogo?genre=tentaculos" to "Tentáculos",
        "/catalogo?genre=tetonas" to "Tetonas",
        "/catalogo?genre=vanilla" to "Vanilla",
        "/catalogo?genre=violacion" to "Violación",
        "/catalogo?genre=virgenes" to "Vírgenes",
        "/catalogo?genre=yaoi" to "Yaoi",
        "/catalogo?genre=yuri" to "Yuri",
        "/catalogo?genre=bondage" to "Bondage",
        "/catalogo?genre=elfas" to "Elfas",
        "/catalogo?genre=petit" to "Petit",
        "/catalogo?genre=threesome" to "Threesome",
        "/catalogo?genre=paizuri" to "Paizuri",
        "/catalogo?genre=gal" to "Gal",
        "/catalogo?genre=oyakodon" to "Oyakodon"
    )

    // --- Categorías habilitadas por defecto ---
    private val defaultEnabledNames = setOf(
        "Episodios Actualizados", "Últimos Estrenados", "Últimos Añadidos", "Contenido Aleatorio"
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
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
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

            layout.addView(createStyledButton("Restablecer ajustes", COLOR_DELETE).apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = 20
                isFocusable = false
                isFocusableInTouchMode = false
                setOnClickListener { showResetDialog() }
            })
            return layout
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