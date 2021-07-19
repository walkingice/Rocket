package org.mozilla.rocket.shopping.search.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.Lazy
import org.mozilla.focus.R
import org.mozilla.focus.databinding.ActivityShoppingSearchPreferencesBinding
import org.mozilla.rocket.adapter.AdapterDelegatesManager
import org.mozilla.rocket.adapter.DelegateAdapter
import org.mozilla.rocket.content.appComponent
import org.mozilla.rocket.content.getViewModel
import org.mozilla.rocket.shopping.search.ui.adapter.ItemMoveCallback
import org.mozilla.rocket.shopping.search.ui.adapter.PreferencesAdapterDelegate
import org.mozilla.rocket.shopping.search.ui.adapter.ShoppingSiteItem
import javax.inject.Inject

class ShoppingSearchPreferencesActivity : AppCompatActivity() {

    private var binding: ActivityShoppingSearchPreferencesBinding? = null

    @Inject
    lateinit var viewModelCreator: Lazy<ShoppingSearchPreferencesViewModel>

    private lateinit var viewModel: ShoppingSearchPreferencesViewModel

    private lateinit var adapter: DelegateAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        appComponent().inject(this)
        super.onCreate(savedInstanceState)
        val binding = ActivityShoppingSearchPreferencesBinding.inflate(layoutInflater)
            .also { setContentView(it.root) }
        this.binding = binding
        viewModel = getViewModel(viewModelCreator)
        initToolBar(binding)
        initPreferenceList(binding)
    }

    override fun onStop() {
        viewModel.onExitSettings()
        super.onStop()
    }

    private fun initToolBar(binding: ActivityShoppingSearchPreferencesBinding) {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun initPreferenceList(binding: ActivityShoppingSearchPreferencesBinding) {
        val adapterDelegate = PreferencesAdapterDelegate(viewModel)
        val adapterDelegatesManager = AdapterDelegatesManager().apply {
            add(ShoppingSiteItem::class, R.layout.item_shopping_search_preference, adapterDelegate)
        }
        adapter = object : DelegateAdapter(adapterDelegatesManager) {
            override fun getItemId(position: Int): Long {
                val uiModel = data[position]
                uiModel as ShoppingSiteItem
                return uiModel.title.hashCode().toLong()
            }
        }.apply {
            setHasStableIds(true)
        }
        binding.recyclerView.apply {
            val itemMoveCallback = ItemMoveCallback(adapterDelegate)
            val touchHelper = ItemTouchHelper(itemMoveCallback)
            touchHelper.attachToRecyclerView(this)
            layoutManager = LinearLayoutManager(this@ShoppingSearchPreferencesActivity)
            adapter = this@ShoppingSearchPreferencesActivity.adapter
        }
        viewModel.shoppingSites.observe(this) {
            adapter.setData(it)
        }
    }

    companion object {
        fun getStartIntent(context: Context) =
            Intent(context, ShoppingSearchPreferencesActivity::class.java)
    }
}
