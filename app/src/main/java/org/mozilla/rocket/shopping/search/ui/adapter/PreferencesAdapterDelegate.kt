package org.mozilla.rocket.shopping.search.ui.adapter

import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.mozilla.focus.R
import org.mozilla.rocket.adapter.AdapterDelegate
import org.mozilla.rocket.adapter.DelegateAdapter
import org.mozilla.rocket.shopping.search.ui.ShoppingSearchPreferencesViewModel

class PreferencesAdapterDelegate(private val viewModel: ShoppingSearchPreferencesViewModel) :
    AdapterDelegate, ItemMoveCallback.ItemTouchHelperContract {

    override fun onRowClear(viewHolder: SiteViewHolder) {
        viewModel.onEditModeEnd()
        viewHolder.itemView.setBackgroundColor(Color.WHITE)
    }

    override fun onRowSelected(viewHolder: SiteViewHolder) {
        viewModel.onEditModeStart()
        viewHolder.itemView.setBackgroundColor(Color.GRAY)
    }

    override fun onRowMoved(viewHolder: SiteViewHolder, target: SiteViewHolder) {
        val toPosition = target.adapterPosition
        val fromPosition = viewHolder.adapterPosition
        viewHolder.containerView.findViewById<View>(R.id.preference_site_switch).tag = toPosition
        target.containerView.findViewById<View?>(R.id.preference_site_switch)?.tag = toPosition

        viewModel.onItemMoved(fromPosition, toPosition)
    }

    override fun onCreateViewHolder(view: View): DelegateAdapter.ViewHolder =
        SiteViewHolder(view, viewModel)
}

class SiteViewHolder(
    override val containerView: View,
    private val viewModel: ShoppingSearchPreferencesViewModel
) : DelegateAdapter.ViewHolder(containerView) {

    override fun bind(uiModel: DelegateAdapter.UiModel) {
        uiModel as ShoppingSiteItem
        containerView.findViewById<TextView>(R.id.preference_site_name).text = uiModel.title
        containerView.findViewById<TextView>(R.id.preference_site_url).text = uiModel.displayUrl
        containerView.findViewById<SwitchCompat>(R.id.preference_site_switch)?.apply {
            isChecked = uiModel.isChecked
            isEnabled = uiModel.isEnabled
            setOnCheckedChangeListener { _, isChecked ->
                viewModel.onItemToggled(adapterPosition, isChecked)
            }
        }
    }
}

data class ShoppingSiteItem(
    val title: String,
    val searchUrl: String,
    val displayUrl: String,
    var showPrompt: Boolean,
    var isChecked: Boolean
) : DelegateAdapter.UiModel() {
    var isEnabled: Boolean = true
}

class ItemMoveCallback(private val delegate: PreferencesAdapterDelegate) :
    ItemTouchHelper.Callback() {

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return if (viewHolder is SiteViewHolder && target is SiteViewHolder) {
            delegate.onRowMoved(viewHolder, target)
            true
        } else {
            false
        }
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return false
    }

    override fun isLongPressDragEnabled(): Boolean {
        return true
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            if (viewHolder is SiteViewHolder) {
                delegate.onRowSelected(viewHolder)
            }
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (viewHolder is SiteViewHolder) {
            delegate.onRowClear(viewHolder)
        }
    }

    interface ItemTouchHelperContract {
        fun onRowMoved(viewHolder: SiteViewHolder, target: SiteViewHolder)
        fun onRowSelected(viewHolder: SiteViewHolder)
        fun onRowClear(viewHolder: SiteViewHolder)
    }
}
