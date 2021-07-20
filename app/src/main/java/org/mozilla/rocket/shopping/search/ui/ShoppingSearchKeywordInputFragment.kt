package org.mozilla.rocket.shopping.search.ui

import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dagger.Lazy
import org.mozilla.focus.R
import org.mozilla.focus.databinding.FragmentShoppingSearchKeywordInputBinding
import org.mozilla.focus.glide.GlideApp
import org.mozilla.focus.telemetry.TelemetryWrapper
import org.mozilla.focus.utils.ViewUtils
import org.mozilla.rocket.content.appComponent
import org.mozilla.rocket.content.getViewModel
import org.mozilla.rocket.shopping.search.data.ShoppingSearchMode
import javax.inject.Inject

class ShoppingSearchKeywordInputFragment :
    Fragment(), View.OnClickListener, ViewTreeObserver.OnGlobalLayoutListener {

    var binding: FragmentShoppingSearchKeywordInputBinding? = null

    @Inject
    lateinit var viewModelCreator: Lazy<ShoppingSearchKeywordInputViewModel>

    private lateinit var viewModel: ShoppingSearchKeywordInputViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        appComponent().inject(this)
        super.onCreate(savedInstanceState)
        viewModel = getViewModel(viewModelCreator)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentShoppingSearchKeywordInputBinding.inflate(inflater, container, false).also {
        this.binding = it
    }.root

    override fun onDestroyView() {
        super.onDestroyView()
        this.binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = this.binding ?: return

        ShoppingSearchMode.getInstance(view.context).deleteKeyword()

        viewModel.uiModel.observe(viewLifecycleOwner) { uiModel ->
            setupView(binding, uiModel)
        }

        viewModel.navigateToResultTab.observe(viewLifecycleOwner) { showResultTab(it) }

        binding.searchKeywordEdit.addTextChangedListener(
            object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // TODO: Deal with non-sequence responses when a user types quickly
                    s?.let { viewModel.onTypingKeyword(it.toString()) }
                }
            }
        )
        binding.searchKeywordEdit.setOnEditorActionListener { editTextView, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    viewModel.onTypedKeywordSent(editTextView.text.toString())
                    true
                }
                else -> false
            }
        }
        binding.searchKeywordEdit.onFocusChangeListener =
            View.OnFocusChangeListener { v, hasFocus ->
                // Avoid showing keyboard again when returning to the previous page by back key.
                if (hasFocus) {
                    ViewUtils.showKeyboard(v)
                } else {
                    ViewUtils.hideKeyboard(v)
                }
            }

        binding.clear.setOnClickListener(this)

        binding.root.setOnKeyboardVisibilityChangedListener { visible ->
            if (visible) {
                viewModel.onKeyboardShown()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val binding = this.binding ?: return
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(this)
        binding.searchKeywordEdit.requestFocus()
        viewModel.onStart(binding.searchKeywordEdit.text.toString())
    }

    override fun onStop() {
        super.onStop()
        val binding = this.binding ?: return
        binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }

    override fun onClick(view: View) {
        val binding = this.binding ?: return
        when (view.id) {
            R.id.clear -> binding.searchKeywordEdit.text.clear()
            R.id.suggestion_item -> {
                val searchTerm = (view as TextView).text
                val isTrendingKeyword = binding.searchKeywordEdit.text.isEmpty()
                binding.searchKeywordEdit.text = SpannableStringBuilder(searchTerm)
                viewModel.onSuggestionKeywordSent(searchTerm.toString(), isTrendingKeyword)
            }
            else -> throw IllegalStateException("Unhandled view in onClick()")
        }
    }

    override fun onGlobalLayout() {
        val binding = this.binding ?: return
        val contentLayoutHeight = binding.contentLayout.measuredHeight
        val descriptionHeight = binding.description.measuredHeight
        val logoManHeight = binding.logoMan.measuredHeight
        val searchSuggestionLayoutHeight = binding.searchSuggestionLayout.measuredHeight
        val inputContainerHeight = binding.inputContainer.measuredHeight

        val extraMargin = (contentLayoutHeight / 10)
        val expectedContentHeight =
            descriptionHeight + logoManHeight + searchSuggestionLayoutHeight + inputContainerHeight + extraMargin

        binding.logoMan.isVisible = (contentLayoutHeight > expectedContentHeight)
    }

    private fun setupView(
        binding: FragmentShoppingSearchKeywordInputBinding,
        uiModel: ShoppingSearchKeywordInputUiModel
    ) {
        binding.description.text = uiModel.description
        if (uiModel.logoManUrl.isNotEmpty()) {
            GlideApp.with(binding.logoMan.context)
                .asBitmap()
                .placeholder(uiModel.defaultLogoManResId)
                .load(uiModel.logoManUrl)
                .into(binding.logoMan)
        }
        binding.clear.visibility = if (uiModel.hideClear) View.GONE else View.VISIBLE
        setSuggestions(binding, uiModel.keywordSuggestions)
    }

    private fun setSuggestions(
        binding: FragmentShoppingSearchKeywordInputBinding,
        suggestions: List<CharSequence>?
    ) {
        binding.searchSuggestionView.removeAllViews()
        if (suggestions == null || suggestions.isEmpty()) {
            binding.searchSuggestionLayout.visibility = View.GONE
            return
        }

        binding.searchSuggestionLayout.visibility = View.VISIBLE
        for (suggestion in suggestions) {
            val item = View.inflate(context, R.layout.tag_text, null) as TextView
            item.text = suggestion
            item.setOnClickListener(this)
            binding.searchSuggestionView.addView(item)
        }
    }

    private fun showResultTab(keyword: String) {
        findNavController().navigate(
            ShoppingSearchKeywordInputFragmentDirections.actionSearchKeywordToResult(keyword)
        )

        TelemetryWrapper.addTabSwipeTab(TelemetryWrapper.Extra_Value.SHOPPING)
    }
}
