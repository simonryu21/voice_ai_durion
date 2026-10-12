package com.lgcns.codeengine.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.lgcns.codeengine.R
import com.lgcns.codeengine.databinding.FragmentCustomDialogBinding
import com.lgcns.codeengine.ui.common.dp
import com.lgcns.codeengine.ui.model.CustomDialogModel

class CustomDialogFragment : DialogFragment() {
    private var _binding: FragmentCustomDialogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomDialogBinding.inflate(LayoutInflater.from(context))
        initView()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.visibility = View.GONE
        binding.root.post {
            val screenMaxWidth = resources.getDimensionPixelOffset(R.dimen.screen_max_width)
            if (binding.root.rootView.width > screenMaxWidth) {
                val sideMargin = ((binding.root.rootView.width - screenMaxWidth) / 2) + 20.dp
                val marginLayoutParams = binding.root.layoutParams as ViewGroup.MarginLayoutParams
                marginLayoutParams.marginStart = sideMargin
                marginLayoutParams.marginEnd = sideMargin

                binding.root.layoutParams = marginLayoutParams
            }

            binding.root.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getTheme(): Int {
        return R.style.CustomDialogTheme    // 다이얼로그 style 지정
    }

    override fun show(manager: FragmentManager, tag: String?) {
        manager.beginTransaction()
            .add(this, tag)
            .commitAllowingStateLoss()
    }

    private fun initView() {
        val model = arguments?.getParcelable<CustomDialogModel>(KEY_DIALOG_MODEL) ?: return

        binding.title.apply {
            if (model.title.isBlank()) {
                this.visibility = View.GONE
            } else {
                this.visibility = View.VISIBLE
                text = model.title
            }
        }

        binding.message.text = model.message

        binding.positiveButton.apply {
            if (model.hasPositiveBtn) {
                this.visibility = View.VISIBLE
                text = model.positiveBtnText
                setOnClickListener {
                    positiveClickListener?.invoke()
                    dismiss()
                }
            } else {
                this.visibility = View.GONE
            }
        }

        binding.negativeButton.apply {
            if (model.hasNegativeBtn) {
                this.visibility = View.VISIBLE
                text = model.negativeBtnText
                setOnClickListener {
                    negativeClickListener?.invoke()
                    dismiss()
                }
            } else {
                this.visibility = View.GONE
            }
        }

        model.cancelable?.let {
            isCancelable = it
        }
    }

    /**
     * Activity or Fragment 에서 CustomDialog 를 chaining 형태로 호출하여 사용할 수 있도록 Builder Pattern 사용
     * - PositiveButton (확인, 예 등)
     * - NegativeButton (취소, 아니오 등)
     * - 확인 등 1개의 버튼으로 구성된 Alert 형태를 사용하고 싶다면 PositiveButton 만 등록하면 됨
     * - 취소, 아니오 등 2개의 버튼으로 구성된 Confirm 형태를 사용하고 싶다면 NegativeButton 만 등록하면 됨
     */
    class Builder {
        private var model = CustomDialogModel()

        fun setTitle(title: String): Builder {
            model.title = title
            return this
        }

        fun setMessage(message: String): Builder {
            model.message = message
            return this
        }

        fun setPositiveButton(buttonText: String, onClick: (() -> Unit) = {}): Builder {
            model.hasPositiveBtn = true
            model.positiveBtnText = buttonText
            positiveClickListener = onClick
            return this
        }

        fun setNegativeButton(buttonText: String, onClick: (() -> Unit) = {}): Builder {
            model.hasNegativeBtn = true
            model.negativeBtnText = buttonText
            negativeClickListener = onClick
            return this
        }

        fun setCancelable(cancelable: Boolean): Builder {
            model.cancelable = cancelable
            return this
        }

        fun show(manager: FragmentManager, tag: String? = null): CustomDialogFragment {
            val newCustomDialog = CustomDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(KEY_DIALOG_MODEL, model)
                }
            }
            newCustomDialog.show(manager, if (!tag.isNullOrBlank()) tag else TAG)
            return newCustomDialog
        }
    }

    companion object {
        private var positiveClickListener: (() -> Unit)? = null
        private var negativeClickListener: (() -> Unit)? = null

        val TAG: String = CustomDialogFragment::class.java.simpleName
        const val KEY_DIALOG_MODEL = "KEY_DIALOG_MODEL"
    }
}
