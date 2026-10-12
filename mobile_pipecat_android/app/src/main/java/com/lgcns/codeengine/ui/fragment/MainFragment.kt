package com.lgcns.codeengine.ui.fragment

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.lgcns.codeengine.R
import com.lgcns.codeengine.activity.PlaceSearchActivity
import com.lgcns.codeengine.data.Result
import com.lgcns.codeengine.viewmodel.MainViewModel
import com.lgcns.codeengine.viewmodel.TokenViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainFragment : Fragment() {
    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private val tokenViewModel: TokenViewModel by lazy {
        ViewModelProvider(this)[TokenViewModel::class.java]
    }

    private var token: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tokenViewModel.token.observe(viewLifecycleOwner) { token ->
            this.token = token
            if (token == null) {
                activity?.supportFragmentManager?.beginTransaction()
                    ?.replace(R.id.login_fragment, LoginFragment(), "LoginFragment")
                    ?.commit();
            }
        }

        val tvInfo = view.findViewById<TextView>(R.id.tvInfo)
        viewModel.userInfoResponse.observe(viewLifecycleOwner) {
            it.let {
                tvInfo.text = when (it) {
                    is Result.Error -> "Code: ${it.exception}, ${it.exception.message}"
                    is Result.Success -> "ID: ${it.data.userInfo._id}\nMail: ${it.data.userInfo.email_address}\n\nToken: $token"
                }
            }
        }

        view.findViewById<Button>(R.id.infoButton).setOnClickListener {
            viewModel.getUserInfo()
        }

        view.findViewById<Button>(R.id.logoutButton).setOnClickListener {
            tokenViewModel.deleteToken()
        }

        view.findViewById<Button>(R.id.nextButton).setOnClickListener {
            val intent = Intent(activity, PlaceSearchActivity::class.java)
            startActivity(intent)
            activity?.finish()
        }
    }
}