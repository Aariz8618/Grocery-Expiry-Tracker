package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.aariz.expirytracker.data.model.Recipe
import com.aariz.expirytracker.data.repository.RecipeRepository
import kotlinx.coroutines.launch

class RecipesFragment : Fragment() {

    private lateinit var searchView: SearchView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecipesAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: LinearLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val recipeRepository = RecipeRepository()
    private val firestoreRepository = FirestoreRepository()
    private val recipes = mutableListOf<Recipe>()
    private var userIngredients = listOf<String>()
    private var isSearchMode = false
    private var currentSearchQuery = ""

    private val suggestedRecipesCache = mutableListOf<Recipe>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_recipes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.header_section)?.applyHeaderInsets()

        initViews(view)
        setupRecyclerView()
        setupSearchView()
        setupSwipeRefresh()

        loadAllRecipes()
    }

    private fun initViews(view: View) {
        searchView = view.findViewById(R.id.search_view)
        recyclerView = view.findViewById(R.id.recycler_recipes)
        progressBar = view.findViewById(R.id.progress_bar)
        emptyState = view.findViewById(R.id.empty_state)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = RecipesAdapter(recipes) { recipe ->
            openRecipeDetail(recipe)
        }
        recyclerView.adapter = adapter
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    isSearchMode = true
                    currentSearchQuery = query
                    searchRecipes(query)
                } else {
                    isSearchMode = false
                    currentSearchQuery = ""
                    loadAllRecipes()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank() && isSearchMode) {
                    isSearchMode = false
                    currentSearchQuery = ""
                    loadAllRecipes()
                }
                return true
            }
        })
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            if (isSearchMode) {
                if (currentSearchQuery.isNotBlank()) {
                    searchRecipes(currentSearchQuery)
                } else {
                    loadAllRecipes()
                }
            } else {
                loadAllRecipes()
            }
        }

        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
    }

    private fun loadAllRecipes(forceRefresh: Boolean = false) {
        showLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val itemsResult = firestoreRepository.getUserGroceryItems()

                // Check if fragment is still attached
                if (!isAdded) return@launch

                if (itemsResult.isSuccess) {
                    val groceryItems = itemsResult.getOrNull() ?: emptyList()

                    userIngredients = groceryItems
                        .filter { it.status != "expired" && it.status != "used" }
                        .map { it.name.lowercase() }
                        .distinct()

                    if (suggestedRecipesCache.isEmpty() || forceRefresh) {
                        val suggestedResult = recipeRepository.getSuggestedRecipes(userIngredients)
                        val suggestedRecipes = suggestedResult.getOrNull() ?: emptyList()

                        suggestedRecipesCache.clear()
                        suggestedRecipesCache.addAll(suggestedRecipes)
                    }

                    val popularQueries = getRandomPopularQueries()
                    val allPopularRecipes = mutableListOf<Recipe>()

                    for (query in popularQueries) {
                        val result = recipeRepository.searchRecipes(query)
                        if (result.isSuccess) {
                            val newRecipes = result.getOrNull() ?: emptyList()
                            allPopularRecipes.addAll(newRecipes)
                        }
                    }

                    // Check again before updating UI
                    if (!isAdded) return@launch

                    val allRecipes = mutableListOf<Recipe>()
                    allRecipes.addAll(suggestedRecipesCache)

                    val suggestedTitles = suggestedRecipesCache.map { it.title.lowercase() }.toSet()
                    val uniquePopular = allPopularRecipes.filter {
                        it.title.lowercase() !in suggestedTitles
                    }
                    allRecipes.addAll(uniquePopular)

                    val uniqueRecipes = allRecipes.distinctBy { it.title.lowercase() }
                    val sortedRecipes = sortRecipesByRelevance(uniqueRecipes)

                    recipes.clear()
                    recipes.addAll(sortedRecipes)
                    adapter.notifyDataSetChanged()
                    updateEmptyState()

                    showLoading(false)
                    swipeRefreshLayout.isRefreshing = false
                } else {
                    if (!isAdded) return@launch

                    showLoading(false)
                    swipeRefreshLayout.isRefreshing = false

                    context?.let {
                        Toast.makeText(it, "Failed to load your ingredients", Toast.LENGTH_SHORT).show()
                    }
                    loadPopularRecipesOnly()
                }
            } catch (e: Exception) {
                if (!isAdded) return@launch

                showLoading(false)
                swipeRefreshLayout.isRefreshing = false

                context?.let {
                    Toast.makeText(it, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                updateEmptyState()
            }
        }
    }

    private fun loadPopularRecipesOnly() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val allRecipes = mutableListOf<Recipe>()
                val queries = getRandomPopularQueries()

                for (query in queries) {
                    val result = recipeRepository.searchRecipes(query)
                    if (result.isSuccess) {
                        val newRecipes = result.getOrNull() ?: emptyList()
                        allRecipes.addAll(newRecipes)
                    }
                }

                if (!isAdded) return@launch

                val uniqueRecipes = allRecipes.distinctBy { it.title.lowercase() }

                recipes.clear()
                recipes.addAll(uniqueRecipes)
                adapter.notifyDataSetChanged()
                updateEmptyState()

                showLoading(false)
                swipeRefreshLayout.isRefreshing = false
            } catch (e: Exception) {
                if (!isAdded) return@launch

                showLoading(false)
                swipeRefreshLayout.isRefreshing = false

                context?.let {
                    Toast.makeText(it, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                updateEmptyState()
            }
        }
    }

    private fun getRandomPopularQueries(): List<String> {
        val allQueries = listOf(
            "chicken", "pasta", "salad", "soup", "beef", "fish", "vegetarian",
            "rice", "noodles", "curry", "steak", "sandwich", "burger", "pizza",
            "dessert", "cake", "cookies", "seafood", "lamb", "pork", "tacos",
            "stir fry", "casserole", "breakfast", "lunch", "dinner", "healthy",
            "quick", "easy", "mexican", "italian", "chinese", "indian", "thai"
        )
        return allQueries.shuffled().take((10..12).random())
    }

    private fun sortRecipesByRelevance(recipeList: List<Recipe>): List<Recipe> {
        return recipeList.sortedByDescending { recipe ->
            var score = 0
            recipe.ingredients.forEach { ingredient ->
                val ingredientLower = ingredient.lowercase()
                userIngredients.forEach { userIngredient ->
                    if (ingredientLower.contains(userIngredient) ||
                        userIngredient.contains(ingredientLower)) {
                        score += 10
                    }
                }
            }
            if (recipe.ingredients.size <= 5) {
                score += 5
            }
            score
        }
    }

    private fun searchRecipes(query: String) {
        showLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val searchQueries = generateSearchVariations(query)
                val allSearchResults = mutableListOf<Recipe>()

                for (searchQuery in searchQueries) {
                    val result = recipeRepository.searchRecipes(searchQuery)
                    if (result.isSuccess) {
                        val newRecipes = result.getOrNull() ?: emptyList()
                        allSearchResults.addAll(newRecipes)
                    }
                }

                if (!isAdded) return@launch

                val uniqueResults = allSearchResults.distinctBy { it.title.lowercase() }
                val finalResult = Result.success(uniqueResults)
                handleRecipeResult(finalResult)
                swipeRefreshLayout.isRefreshing = false
            } catch (e: Exception) {
                if (!isAdded) return@launch

                showLoading(false)
                swipeRefreshLayout.isRefreshing = false

                context?.let {
                    Toast.makeText(it, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                updateEmptyState()
            }
        }
    }

    private fun generateSearchVariations(query: String): List<String> {
        val variations = mutableListOf<String>()
        variations.add(query)
        variations.add("$query recipe")
        variations.add("$query recipes")
        variations.add("$query dish")
        variations.add("easy $query")
        variations.add("best $query")
        variations.add("simple $query")

        val words = query.split(" ").filter { it.length > 3 }
        words.forEach { word ->
            if (word != query) {
                variations.add(word)
            }
        }

        return variations.distinct()
    }

    private fun handleRecipeResult(result: Result<List<Recipe>>) {
        if (!isAdded) return

        showLoading(false)

        if (result.isSuccess) {
            val fetchedRecipes = result.getOrNull() ?: emptyList()

            val sortedRecipes = if (userIngredients.isNotEmpty() && isSearchMode) {
                sortRecipesByRelevance(fetchedRecipes)
            } else {
                fetchedRecipes
            }

            recipes.clear()
            recipes.addAll(sortedRecipes)
            adapter.notifyDataSetChanged()
            updateEmptyState()

            if (fetchedRecipes.isEmpty()) {
                context?.let {
                    Toast.makeText(it, "No recipes found. Try a different search.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val error = result.exceptionOrNull()
            context?.let {
                Toast.makeText(it, "Failed to load recipes: ${error?.message}", Toast.LENGTH_LONG).show()
            }
            updateEmptyState()
        }
    }

    private fun openRecipeDetail(recipe: Recipe) {
        if (!isAdded) return

        val times = extractTimesFromInstructions(recipe.instructions)

        val intent = Intent(requireContext(), RecipeDetailActivity::class.java).apply {
            putExtra("title", recipe.title)
            putExtra("servings", recipe.servings)
            putExtra("prepTime", "Varies")
            putExtra("difficulty", "Medium")
            putExtra("ingredientsPreview", recipe.getIngredientsPreview())
            putExtra("instructionsPreview", recipe.getInstructionsPreview())
            putStringArrayListExtra("ingredients", ArrayList(recipe.ingredients))
            putStringArrayListExtra("instructions", ArrayList(recipe.instructions))
            putStringArrayListExtra("times", times)
            putExtra("notes", "Recipe from API Ninjas")
        }
        startActivity(intent)
    }

    private fun extractTimesFromInstructions(instructions: List<String>): ArrayList<String> {
        val times = ArrayList<String>()
        val timePattern = Regex("(\\d+)\\s*(min|minute|minutes|hour|hours|seconds?)", RegexOption.IGNORE_CASE)

        instructions.forEach { instruction ->
            val match = timePattern.find(instruction)
            if (match != null) {
                val value = match.groupValues[1]
                val unit = match.groupValues[2].lowercase()

                val minutes = when {
                    unit.startsWith("hour") -> value.toInt() * 60
                    unit.startsWith("sec") -> 1
                    else -> value.toInt()
                }
                times.add("$minutes min")
            } else {
                times.add("5 min")
            }
        }

        return times
    }

    private fun showLoading(show: Boolean) {
        if (!isAdded) return

        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyState() {
        if (!isAdded) return

        if (recipes.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }
    }
}