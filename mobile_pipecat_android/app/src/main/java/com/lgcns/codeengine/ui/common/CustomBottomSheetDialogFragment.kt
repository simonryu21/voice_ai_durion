package com.lgcns.codeengine.ui.common

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lgcns.codeengine.databinding.FragmentCustomBottomSheetDialogBinding
import com.lgcns.codeengine.ui.model.CustomBottomSheetDialogModel

class CustomBottomSheetDialogFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentCustomBottomSheetDialogBinding? = null
    private val binding get() = _binding!!

    private var onButtonClicked = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCustomBottomSheetDialogBinding.inflate(LayoutInflater.from(context))
        initView()
        return binding.root
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!onButtonClicked) onDismissListener?.invoke()
        super.onDismiss(dialog)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun show(manager: FragmentManager, tag: String?) {
        manager.beginTransaction()
            .add(this, tag)
            .commitAllowingStateLoss()
    }

    private fun initView() {
        val model =
            arguments?.getParcelable<CustomBottomSheetDialogModel>(KEY_BOTTOM_SHEET_DIALOG_MODEL) ?: return

        binding.title.apply {
            if (model.title.isBlank()) {
                this.visibility = View.GONE
            } else {
                this.visibility = View.VISIBLE
                text = model.title
            }
        }

        binding.closeBtn.apply {
            if (model.hasCloseBtn) {
                this.visibility = View.VISIBLE
                setOnClickListener {
                    closeClickListener?.invoke()
                    dismiss()
                }
            } else {
                this.visibility = View.GONE
            }
        }

        if (model.message.isEmpty()) {
            binding.message.visibility = View.GONE
        } else {
            binding.message.visibility = View.VISIBLE
            binding.message.text = model.message
        }

        if (model.highlightedMessage.isEmpty()) {
            binding.highlightedMessage.visibility = View.GONE
        } else {
            binding.highlightedMessage.visibility = View.VISIBLE
            binding.highlightedMessage.text = model.highlightedMessage
        }

        binding.positiveButton.apply {
            if (model.hasPositiveBtn) {
                this.visibility = View.VISIBLE
                text = model.positiveBtnText
                setOnClickListener {
                    onButtonClicked = true
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
                    onButtonClicked = true
                    negativeClickListener?.invoke()
                    dismiss()
                }
            } else {
                this.visibility = View.GONE
            }
        }

        isCancelable = model.cancelable
    }

    class Builder {
        private var model = CustomBottomSheetDialogModel()

        fun setTitle(title: String): Builder {
            model.title = title
            return this
        }

        fun setCloseBtn(showCloseBtn: Boolean, onClick: (() -> Unit) = {}): Builder {
            model.hasCloseBtn = showCloseBtn
            closeClickListener = onClick
            return this
        }

        fun setMessage(message: String): Builder {
            model.message = message
            return this
        }

        fun setHighlightedMessage(highlightedMessage: String): Builder {
            model.highlightedMessage = highlightedMessage
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

        fun setDismissCallback(dismissEvent: (() -> Unit)?): Builder {
            onDismissListener = dismissEvent
            return this
        }

        fun show(manager: FragmentManager): CustomBottomSheetDialogFragment {
            val newCustomBottomSheetDialog = CustomBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(KEY_BOTTOM_SHEET_DIALOG_MODEL, model)
                }
            }
            newCustomBottomSheetDialog.show(manager, TAG)
            return newCustomBottomSheetDialog
        }
    }

    companion object {
        private var positiveClickListener: (() -> Unit)? = null
        private var negativeClickListener: (() -> Unit)? = null
        private var closeClickListener: (() -> Unit)? = null
        private var onDismissListener: (() -> Unit)? = null

        val TAG: String = CustomBottomSheetDialogFragment::class.java.simpleName
        const val KEY_BOTTOM_SHEET_DIALOG_MODEL = "KEY_BOTTOM_SHEET_DIALOG_MODEL"
    }

}