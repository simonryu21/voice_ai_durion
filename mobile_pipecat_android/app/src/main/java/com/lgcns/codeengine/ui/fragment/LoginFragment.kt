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
import com.lgcns.codeengine.activity.WebViewTestActivity
import com.lgcns.codeengine.data.Result
import com.lgcns.codeengine.data.entity.Auth
import com.lgcns.codeengine.viewmodel.LoginViewModel
import com.lgcns.codeengine.viewmodel.TokenViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : Fragment() {
    private val viewModel: LoginViewModel by lazy {
        ViewModelProvider(this)[LoginViewModel::class.java]
    }

    private val tokenViewModel: TokenViewModel by lazy {
        ViewModelProvider(this)[TokenViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tvLogin = view.findViewById<TextView>(R.id.tvLogin)

        tokenViewModel.token.observe(viewLifecycleOwner) { token ->
            if (token != null) {
                activity?.supportFragmentManager?.beginTransaction()
                    ?.replace(R.id.login_fragment, MainFragment(), "MainFragment")
                    ?.commit();
            }
        }

        viewModel.loginResponse.observe(viewLifecycleOwner) {
            it.let {
                when (it) {
                    is Result.Error -> tvLogin.text = it.exception.message
                    is Result.Success -> {
                        tokenViewModel.saveToken(it.data.token)
                    }
                }
            }
        }

        view.findViewById<Button>(R.id.loginButton).setOnClickListener {
            viewModel.login(
                // Note: ID, Password를 UI로부터 입력받습니다. 지금은 테스트를 위해 hard coding된 값을 사용합니다.
                Auth("test@gmail.com", "123Test")
            )
        }

        view.findViewById<Button>(R.id.skipButton).setOnClickListener {
            val intent = Intent(activity, PlaceSearchActivity::class.java)
            startActivity(intent)
            activity?.finish()
        }

        view.findViewById<Button>(R.id.webviewTestButton).setOnClickListener {
            val intent = Intent(activity, WebViewTestActivity::class.java)
            startActivity(intent)
            activity?.finish()
        }
    }
}