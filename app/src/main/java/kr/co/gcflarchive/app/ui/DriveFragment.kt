package kr.co.gcflarchive.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import kr.co.gcflarchive.app.Config
import kr.co.gcflarchive.app.MainActivity
import kr.co.gcflarchive.app.databinding.FragmentDriveBinding
import kr.co.gcflarchive.app.share.ShareReceiverActivity
import kr.co.gcflarchive.app.web.GcflWebView

/** 극플드라이브 tab: the website's /share page plus a native "파일 올리기" button. */
class DriveFragment : Fragment(), MainActivity.Reselectable {
    private var _binding: FragmentDriveBinding? = null
    private val binding get() = _binding!!

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            _binding?.webView?.goBack()
        }
    }

    private val web = GcflWebView(this, object : GcflWebView.Listener {
        override fun onProgress(progress: Int) {
            val b = _binding ?: return
            b.progress.progress = progress
            b.progress.isVisible = progress < 100
        }

        override fun onPageFinished(url: String) {
            val b = _binding ?: return
            b.swipe.isRefreshing = false
            updateBack()
        }
    })

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) startActivity(ShareReceiverActivity.intent(requireContext(), uris))
    }

    private var needsReload = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDriveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        web.attach(binding.webView)
        binding.swipe.setOnRefreshListener { binding.webView.reload() }
        binding.swipe.setOnChildScrollUpCallback { _, _ -> binding.webView.scrollY > 0 }
        binding.fabUpload.setOnClickListener {
            needsReload = true
            pickFiles.launch(arrayOf("*/*"))
        }
        binding.webView.loadUrl(Config.url("/share"))
    }

    override fun onResume() {
        super.onResume()
        // Coming back from an upload (share sheet or picker): show the new files.
        if (needsReload) {
            needsReload = false
            binding.webView.reload()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        updateBack()
        if (!hidden) _binding?.webView?.reload()
    }

    override fun onReselected() {
        binding.webView.loadUrl(Config.url("/share"))
    }

    private fun updateBack() {
        backCallback.isEnabled = !isHidden && _binding?.webView?.canGoBack() == true
    }

    override fun onDestroyView() {
        binding.webView.destroy()
        super.onDestroyView()
        _binding = null
    }
}
