@file:JvmName("PromotionDialogExt")

package org.mozilla.rocket.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import org.mozilla.focus.databinding.LayoutPromotionDialogBinding
import org.mozilla.rocket.landing.DialogQueue

class PromotionDialog(
    private val context: Context,
    private val data: CustomViewDialogData
) {

    val binding: LayoutPromotionDialogBinding

    private var onPositiveListener: (() -> Unit)? = null
    private var onNegativeListener: (() -> Unit)? = null
    private var onCloseListener: (() -> Unit)? = null
    private var onCancelListener: (() -> Unit)? = null

    private val onShowListeners = mutableListOf<() -> Unit>()
    private val onDismissListeners = mutableListOf<() -> Unit>()

    private var cancellable = false

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        binding = LayoutPromotionDialogBinding.inflate(inflater, null, false)
        initView()
    }

    fun onPositive(listener: () -> Unit): PromotionDialog {
        this.onPositiveListener = listener
        return this
    }

    fun onNegative(listener: () -> Unit): PromotionDialog {
        this.onNegativeListener = listener
        return this
    }

    fun onClose(listener: () -> Unit): PromotionDialog {
        this.onCloseListener = listener
        return this
    }

    fun onCancel(listener: () -> Unit): PromotionDialog {
        this.onCancelListener = listener
        return this
    }

    fun addOnShowListener(listener: () -> Unit): PromotionDialog {
        onShowListeners.add(listener)
        return this
    }

    fun addOnDismissListener(listener: () -> Unit): PromotionDialog {
        onDismissListeners.add(listener)
        return this
    }

    fun setCancellable(cancellable: Boolean): PromotionDialog {
        this.cancellable = cancellable
        return this
    }

    fun show() {
        createDialog().show()
    }

    private fun initView() {
        with(binding.image) {
            val width = data.imgWidth
            val height = data.imgHeight
            if (width != null && height != null) {
                layoutParams.apply {
                    this.width = width
                    this.height = height
                }
            }
            data.drawable?.let { setImageDrawable(it) } ?: run { visibility = View.GONE }
        }

        with(binding.title) {
            data.title?.let { text = it } ?: run { visibility = View.GONE }
        }

        with(binding.description) {
            data.description?.let { text = it } ?: run { visibility = View.GONE }
        }

        with(binding.positiveButton) {
            data.positiveText?.let { text = it } ?: run {
                visibility = View.GONE
                binding.buttonDivider1.visibility = View.GONE
            }
        }

        with(binding.negativeButton) {
            data.negativeText?.let { text = it } ?: run {
                visibility = View.GONE
                binding.buttonDivider2.visibility = View.GONE
            }
        }

        binding.closeButton.visibility = if (data.showCloseButton) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun createDialog(): AlertDialog {
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setOnCancelListener {
                onCancelListener?.invoke()
            }
            .setCancelable(cancellable)
            .create()

        binding.positiveButton.setOnClickListener {
            dialog.dismiss()
            onPositiveListener?.invoke()
        }

        binding.negativeButton.setOnClickListener {
            dialog.dismiss()
            onNegativeListener?.invoke()
        }

        binding.closeButton.setOnClickListener {
            dialog.dismiss()
            onCloseListener?.invoke()
        }

        dialog.setOnShowListener {
            onShowListeners.forEach { it() }
        }

        dialog.setOnDismissListener {
            onDismissListeners.forEach { it() }
        }

        return dialog
    }
}

fun DialogQueue.enqueue(dialog: PromotionDialog, onShow: () -> Unit) {
    enqueue(object : DialogQueue.DialogDelegate {
        override fun setOnDismissListener(listener: () -> Unit) {
            dialog.addOnDismissListener(listener)
        }

        override fun show() {
            dialog.show()
            onShow()
        }
    })
}

@Suppress("unused")
fun DialogQueue.tryShow(dialog: PromotionDialog, onShow: () -> Unit): Boolean {
    return tryShow(object : DialogQueue.DialogDelegate {
        override fun setOnDismissListener(listener: () -> Unit) {
            dialog.addOnDismissListener(listener)
        }

        override fun show() {
            dialog.show()
            onShow()
        }
    })
}
