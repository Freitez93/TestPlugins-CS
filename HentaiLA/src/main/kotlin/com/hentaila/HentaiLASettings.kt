package com.hentaila

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.text.InputType
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
import java.util.Collections

object HentaiLASettings {
    // --- Colores y Constantes ---
    private const val PREFS_PREFIX = "HentaiLA_"
    const val ALL_CATEGORIES_ORDER_KEY = "${PREFS_PREFIX}ALL_order"
    const val CUSTOM_CATEGORIES_KEY = "${PREFS_PREFIX}CUSTOM_categories"
    const val DELETED_CATEGORIES_KEY = "${PREFS_PREFIX}DELETED_categories"
    const val USE_ITEM_BACKGROUND_KEY = "${PREFS_PREFIX}USE_ITEM_BACKGROUND"

    private const val COLOR_BG = "#15111B"
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

    // --- Funciones para manejar categorías ---
    fun getOrderedAndEnabledCategories(): List<Pair<String, String>> {
        val allCats = getAllCategories()
        val allCategoryNames = allCats.map { it.second }
        val orderedNames = getOrderedCategories(ALL_CATEGORIES_ORDER_KEY, allCategoryNames)
        return orderedNames.filter {
            name -> isCategoryEnabled(name)
        }.mapNotNull {
            name -> allCats.find {
                it.second == name
            }
        }
    }

    // --- Diálogo de configuración ---
    fun showSettingsDialog(activity: AppCompatActivity, onSave: () -> Unit) {
        SettingsManager(activity, onSave).show()
    }

    // --- Obtener todas las categorías (incluyendo personalizadas) ---
    fun getAllCategories(): List<Pair<String, String>> {
        val allCategories = defaultCategories + getCustomCategories()
        val deletedCategories = getDeletedCategories()
        return allCategories.filter { it.second !in deletedCategories }
    }

    // --- Obtener categorías personalizadas ---
    private fun getCustomCategories(): List<Pair<String, String>> {
        val json = getKey<String>(CUSTOM_CATEGORIES_KEY) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map {
                val jsonObject = jsonArray.getJSONObject(it)
                Pair(jsonObject.getString("url"), jsonObject.getString("name"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // --- Agregar categoría personalizada ---
    private fun addCustomCategory(name: String, url: String) {
        var finalUrl = url.trim()
        if (!finalUrl.startsWith("http") && !finalUrl.startsWith("/")) {
            finalUrl = "/$finalUrl"
        }
        val currentCategories = getCustomCategories().toMutableList()
        currentCategories.add(Pair(finalUrl, name))
        val jsonArray = JSONArray()
        currentCategories.forEach { (url, name) ->
            val jsonObject = JSONObject().apply { put("url", url); put("name", name) }
            jsonArray.put(jsonObject)
        }
        setKey(CUSTOM_CATEGORIES_KEY, jsonArray.toString())
    }

    private fun addCategoryToDeletedList(categoryName: String) {
        val deletedSet = getDeletedCategories().toMutableSet()
        deletedSet.add(categoryName)
        val jsonArray = JSONArray()
        deletedSet.forEach { jsonArray.put(it) }
        setKey(DELETED_CATEGORIES_KEY, jsonArray.toString())
    }

    fun deleteCategory(name: String) {
        if (defaultCategories.any { it.second == name }) {
            addCategoryToDeletedList(name)
            return
        }
        val currentCustomCategories = getCustomCategories().toMutableList()
        if (currentCustomCategories.removeAll { it.second == name }) {
            if (currentCustomCategories.isEmpty()) {
                setKey(CUSTOM_CATEGORIES_KEY, null)
            } else {
                val jsonArray = JSONArray()
                currentCustomCategories.forEach { (url, name) ->
                    val jsonObject = JSONObject().apply { put("url", url); put("name", name) }
                    jsonArray.put(jsonObject)
                }
                setKey(CUSTOM_CATEGORIES_KEY, jsonArray.toString())
            }
        }
    }

    fun useItemBackground(): Boolean {
        return getKey<String>(USE_ITEM_BACKGROUND_KEY) == "true"
    }

    fun setUseItemBackground(use: Boolean) {
        setKey(USE_ITEM_BACKGROUND_KEY, use.toString())
    }

    fun isCategoryEnabled(categoryName: String): Boolean {
        val key = "${PREFS_PREFIX}${categoryName}_enabled"
        return when (getKey<String>(key)) {
            "true" -> true
            "false" -> false
            else -> defaultEnabledNames.contains(categoryName)
        }
    }

    fun setCategoryEnabled(categoryName: String, enabled: Boolean) {
        setKey("${PREFS_PREFIX}${categoryName}_enabled", enabled.toString())
    }

    fun resetAppearanceSettings() {
        setKey(USE_ITEM_BACKGROUND_KEY, null)
    }

    fun resetCategoriesOnly() {
        setKey(ALL_CATEGORIES_ORDER_KEY, null)
        setKey(DELETED_CATEGORIES_KEY, null)
        setKey(CUSTOM_CATEGORIES_KEY, null)
        getAllCategories().forEach { (_, name) -> setKey("${PREFS_PREFIX}${name}_enabled", null) }
    }

    fun resetAllSettings() {
        resetCategoriesOnly()
        resetAppearanceSettings()
    }

    private fun getDeletedCategories(): Set<String> {
        val json = getKey<String>(DELETED_CATEGORIES_KEY) ?: return emptySet()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun getOrderedCategories(key: String, defaultList: List<String>): List<String> {
        val savedOrderJson: String? = getKey(key)
        return if (savedOrderJson != null) {
            try {
                val jsonArray = JSONArray(savedOrderJson)
                val savedList = (0 until jsonArray.length()).map { jsonArray.getString(it) }
                val defaultSet = defaultList.toSet()
                val validSavedList = savedList.filter { it in defaultSet }
                val newItems = defaultList.filter { it !in validSavedList }
                validSavedList + newItems
            } catch (e: Exception) { defaultList }
        } else { defaultList }
    }

    fun setOrderedCategories(key: String, list: List<String>) {
        val jsonArray = JSONArray().apply { list.forEach { put(it) } }
        setKey(key, jsonArray.toString())
    }

    // --- Gestor de la interfaz ---
    // Esta sección permanece igual, funciona de forma genérica.
    private class SettingsManager(val context: AppCompatActivity, val onSave: () -> Unit) {
        private val ID_BTN_CATEGORIES = 1001
        private val ID_BTN_LAYOUT = 1002
        private val ID_RECYCLER_VIEW = 1003

        private val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(COLOR_BG))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        private lateinit var categoriesView: View
        private lateinit var layoutView: View
        private lateinit var btnCategories: Button
        private lateinit var btnLayout: Button
        private lateinit var recyclerView: RecyclerView
        private lateinit var adapter: CategoryAdapter

        fun show() {
            val dialog = AlertDialog.Builder(context)
                .setView(createRootView())
                .setCancelable(false)
                .create()

            dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
                setColor(Color.parseColor(COLOR_BG));
                cornerRadius = 16f
            })
            dialog.show()

            val window = dialog.window
            val layoutParams = window?.attributes
            val displayMetrics = context.resources.displayMetrics
            layoutParams?.width = (displayMetrics.widthPixels * 0.90).toInt()
            layoutParams?.height = (displayMetrics.heightPixels * 0.80).toInt()
            window?.attributes = layoutParams

            mainLayout.findViewWithTag<Button>("SAVE_BTN")?.apply {
                nextFocusUpId = ID_RECYCLER_VIEW
                setOnClickListener {
                    onSave()
                    dialog.dismiss()
                }
            }
        }

        private fun createRootView(): View {
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(10, 10, 10, 10)
            }
            btnCategories = createTabButton("Categorías") { switchTab(0) }
            btnCategories.id = ID_BTN_CATEGORIES
            btnLayout = createTabButton("Ajustes") { switchTab(1) }
            btnLayout.id = ID_BTN_LAYOUT
            header.addView(btnCategories)
            header.addView(btnLayout)
            mainLayout.addView(header)

            val container = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            categoriesView = createCategoriesView()
            layoutView = createLayoutView()
            container.addView(categoriesView)
            container.addView(layoutView)
            mainLayout.addView(container)

            val footer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 10, 20, 20)
                gravity = Gravity.CENTER
            }

            val btnSave = Button(context).apply {
                tag = "SAVE_BTN"
                text = "Guardar Y Salir"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = createButtonDrawable(Color.parseColor(COLOR_SAVE))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            footer.addView(btnSave)
            mainLayout.addView(footer)

            switchTab(0)
            return mainLayout
        }

        private fun switchTab(index: Int) {
            categoriesView.visibility = if (index == 0) View.VISIBLE else View.GONE
            layoutView.visibility = if (index == 1) View.VISIBLE else View.GONE
            val activeColor = Color.parseColor(COLOR_PRIMARY)
            val inactiveColor = Color.TRANSPARENT
            btnCategories.background = createButtonDrawable(if (index == 0) activeColor else inactiveColor)
            btnLayout.background = createButtonDrawable(if (index == 1) activeColor else inactiveColor)
        }

        private fun createCategoriesView(): View {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 20, 20, 20)
                gravity = Gravity.CENTER
            }

            adapter = CategoryAdapter(context) { name, enabled -> setCategoryEnabled(name, enabled) }
            recyclerView = RecyclerView(context).apply {
                id = ID_RECYCLER_VIEW
                layoutManager = LinearLayoutManager(context)
                this.adapter = this@SettingsManager.adapter
                setBackgroundColor(Color.parseColor(COLOR_BG))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                isFocusable = false
                isFocusableInTouchMode = false
            }

            val addCategoryButton = Button(context).apply {
                tag = "ADD_BTN"
                text = "Agregar categoría"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = createButtonDrawable(Color.parseColor(COLOR_BUTTON))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
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
            val orderedList = getOrderedCategories(ALL_CATEGORIES_ORDER_KEY, allCategoryNames)
            adapter.setOrderKey(ALL_CATEGORIES_ORDER_KEY)
            adapter.setRecyclerView(recyclerView)
            adapter.setList(orderedList)
        }

        private fun showAddCategoryDialog() {
            val nameInput = EditText(context).apply {
                hint = "Ej: Categoria 3D"
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor(COLOR_PRIMARY))
            }
            val urlInput = EditText(context).apply {
                hint = "Ej: /catalogo?genre=3d"
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor(COLOR_PRIMARY))
            }

            val dialogLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 40)
                setBackgroundColor(Color.parseColor(COLOR_BG))
                addView(TextView(context).apply { text = "Nombre"; setTextColor(Color.WHITE) })
                addView(nameInput)
                addView(TextView(context).apply { text = "Ruta API"; setTextColor(Color.WHITE); setPadding(0, 30, 0, 0) })
                addView(urlInput)
            }

            AlertDialog.Builder(context)
                .setTitle("Agregar nueva categoría")
                .setView(dialogLayout)
                .setPositiveButton("Agregar") { _, _ ->
                    val name = nameInput.text.toString().trim()
                    val url = urlInput.text.toString().trim()
                    if (name.isNotEmpty() && url.isNotEmpty()) {
                        addCustomCategory(name, url)
                        setCategoryEnabled(name, true)
                        refreshCategoryList()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        private fun createLayoutView(): View {
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
            }
            val btnReset = Button(context).apply {
                text = "Restablecer ajustes"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = createButtonDrawable(Color.parseColor(COLOR_DELETE))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 20 }
                isFocusable = false
                isFocusableInTouchMode = false
                setOnClickListener { showResetDialog() }
            }
            layout.addView(btnReset)
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

        private fun createTabButton(text: String, onClick: () -> Unit): Button {
            return Button(context).apply {
                this.text = text; setTextColor(Color.WHITE); textSize = 13f
                minHeight = 0; minimumHeight = 0
                setPadding(15, 10, 15, 10)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 5 }
                isFocusable = false
                isFocusableInTouchMode = false
                setOnClickListener { onClick() }
            }
        }

        private fun createButtonDrawable(color: Int): StateListDrawable {
            return StateListDrawable().apply {
                val shape = GradientDrawable().apply { setColor(color); cornerRadius = 16f; setStroke(2, Color.TRANSPARENT) }
                val focusedShape = GradientDrawable().apply { setColor(Color.parseColor(COLOR_FOCUS)); cornerRadius = 16f; setStroke(2, Color.WHITE) }
                addState(intArrayOf(android.R.attr.state_focused), focusedShape)
                addState(intArrayOf(), shape)
            }
        }

        // --- ADAPTER ---
        private inner class CategoryAdapter(
            private val ctx: Context,
            private val onCheckedChange: (String, Boolean) -> Unit
        ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

            private val items = mutableListOf<String>()
            private var orderKey: String = ""
            private var recyclerViewRef: RecyclerView? = null

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

                recyclerViewRef?.post {
                    val rv = recyclerViewRef ?: return@post
                    if (items.isNotEmpty()) {
                        rv.scrollToPosition(0)
                        val vh = rv.findViewHolderForAdapterPosition(0)
                        (vh as? ViewHolder)?.checkBox?.requestFocus()
                    }
                }
            }

            inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
                val container: LinearLayout = view as LinearLayout
                val checkBox: CheckBox = view.findViewById(CHECKBOX_ID)
                val upButton: Button = view.findViewById(UP_BUTTON_ID)
                val downButton: Button = view.findViewById(DOWN_BUTTON_ID)
                val deleteButton: Button = view.findViewById(DELETE_BUTTON_ID)
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

                val toggleAction = {
                    val pos = holder.adapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        handleToggle(items[pos], !isEnabled, pos)
                    }
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

                val canMoveUp = isEnabled && position > 0
                val canMoveDown = isEnabled && position < (selectedCount - 1)

                holder.upButton.apply {
                    visibility = if (isEnabled) View.VISIBLE else View.INVISIBLE
                    this.isEnabled = canMoveUp
                    alpha = if (canMoveUp) 1.0f else 0.3f
                    isFocusable = canMoveUp
                    isFocusableInTouchMode = canMoveUp
                    nextFocusRightId = DOWN_BUTTON_ID
                    setOnClickListener {
                        val fromPos = holder.adapterPosition
                        if (fromPos != RecyclerView.NO_POSITION) moveItem(fromPos, fromPos - 1, UP_BUTTON_ID)
                    }
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                            if (isEnabled) callOnClick()
                            true
                        } else false
                    }
                }

                holder.downButton.apply {
                    visibility = if (isEnabled) View.VISIBLE else View.INVISIBLE
                    this.isEnabled = canMoveDown
                    alpha = if (canMoveDown) 1.0f else 0.3f
                    isFocusable = canMoveDown
                    isFocusableInTouchMode = canMoveDown
                    nextFocusRightId = DELETE_BUTTON_ID
                    setOnClickListener {
                        val fromPos = holder.adapterPosition
                        if (fromPos != RecyclerView.NO_POSITION) moveItem(fromPos, fromPos + 1, DOWN_BUTTON_ID)
                    }
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                            if (isEnabled) callOnClick()
                            true
                        } else false
                    }
                }

                holder.deleteButton.apply {
                    visibility = View.VISIBLE
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnClickListener {
                        val pos = holder.adapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            deleteCategory(items[pos])
                            refreshCategoryList()
                        }
                    }
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_UP && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
                            callOnClick()
                            true
                        } else false
                    }
                }
            }

            private fun handleToggle(categoryName: String, newState: Boolean, fromPos: Int) {
                if (fromPos == RecyclerView.NO_POSITION) return
                setCategoryEnabled(categoryName, newState)
                onCheckedChange(categoryName, newState)

                val selectedCountBefore = items.count { it != categoryName && isCategoryEnabled(it) }
                val item = items.removeAt(fromPos)

                val targetPosition = if (newState) {
                    selectedCountBefore
                } else {
                    val unselectedItems = items.filter { !isCategoryEnabled(it) }
                    val insertIndex = unselectedItems.indexOfFirst { it.lowercase() > categoryName.lowercase() }
                    if (insertIndex == -1) {
                        items.size
                    } else {
                        selectedCountBefore + insertIndex
                    }
                }

                items.add(targetPosition, item)
                notifyItemMoved(fromPos, targetPosition)

                val start = kotlin.math.min(fromPos, targetPosition)
                val end = kotlin.math.max(fromPos, targetPosition)
                notifyItemRangeChanged(start, end - start + 1)

                setOrderedCategories(orderKey, items)
                ensureFocusOnPositionAndClearPressed(targetPosition)
            }

            private fun moveItem(from: Int, to: Int, focusTargetId: Int = CHECKBOX_ID) {
                if (from == RecyclerView.NO_POSITION || from == to) return
                val item = items.removeAt(from)
                val boundedTo = to.coerceIn(0, items.size)
                items.add(boundedTo, item)

                notifyItemMoved(from, boundedTo)

                val start = kotlin.math.min(from, boundedTo)
                val end = kotlin.math.max(from, boundedTo)
                notifyItemRangeChanged(start, end - start + 1)

                setOrderedCategories(orderKey, items)
                ensureFocusOnPositionAndClearPressed(boundedTo.coerceIn(0, items.size - 1), focusTargetId)
            }

            private fun ensureFocusOnPositionAndClearPressed(position: Int, focusTargetId: Int = CHECKBOX_ID) {
                val rv = recyclerViewRef ?: return
                rv.scrollToPosition(position)
                rv.post {
                    val vh = rv.findViewHolderForAdapterPosition(position)
                    if (vh is ViewHolder) {
                        vh.itemView.findViewById<View>(focusTargetId)?.requestFocus()
                        vh.container.isPressed = false
                        vh.checkBox.isPressed = false
                        vh.upButton.isPressed = false
                        vh.downButton.isPressed = false
                        vh.deleteButton.isPressed = false
                        vh.itemView.isPressed = false
                    } else {
                        rv.postDelayed({ ensureFocusOnPositionAndClearPressed(position, focusTargetId) }, 40)
                    }
                }
            }
            // Iinterfaz de la lista de categorias.
            private fun createItemLayout(context: Context): LinearLayout {
                return LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    val paddingH = dpToPx(context, 12)
                    val paddingV = dpToPx(context, 4) // Espacio entre la lista de categoria.
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
                    buttonTintList = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_focused, android.R.attr.state_checked), intArrayOf(android.R.attr.state_focused, -android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked)),
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
                return LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isFocusable = false
                    isFocusableInTouchMode = false
                    val buttonSize = dpToPx(context, 40)
                    val buttonMargin = dpToPx(context, 4)

                    addView(createFocusableButton(context, "▲", UP_BUTTON_ID).apply { layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply { marginEnd = buttonMargin } })
                    addView(createFocusableButton(context, "▼", DOWN_BUTTON_ID).apply { layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply { marginStart = buttonMargin } })
                    addView(createFocusableButton(context, "🗑", DELETE_BUTTON_ID, COLOR_DELETE).apply { layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply { marginStart = buttonMargin } })
                }
            }

            private fun createFocusableButton(context: Context, symbol: String, id: Int, defaultColor: String = COLOR_PRIMARY): Button {
                val focusColor = Color.parseColor(COLOR_FOCUS)
                return Button(context).apply {
                    this.id = id
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

            private fun dpToPx(context: Context, dp: Int): Int {
                return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
            }
        }
    }
}