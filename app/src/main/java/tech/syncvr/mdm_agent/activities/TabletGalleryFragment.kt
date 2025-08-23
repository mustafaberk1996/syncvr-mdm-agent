package tech.syncvr.mdm_agent.activities

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.OnItemClickListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import tech.syncvr.mdm_agent.R
import tech.syncvr.mdm_agent.activities.items.TabletGalleryApp
import tech.syncvr.mdm_agent.activities.items.TabletGalleryAppItem
import tech.syncvr.mdm_agent.activities.items.TabletGalleryLink
import tech.syncvr.mdm_agent.activities.items.TabletGalleryLinkItem
import tech.syncvr.mdm_agent.repositories.auto_start.tablet.TabletAutoStartManager.Companion.BROWSER_PACKAGE

@AndroidEntryPoint
class TabletGalleryFragment : Fragment() {

    companion object {
        private const val TAG = "TabletGalleryFragment"
    }

    private val viewModel: TabletGalleryViewModel by viewModels()

    private val onAppItemClickListener = OnItemClickListener { item, _ ->
        if (item is TabletGalleryAppItem) {
            Log.d(TAG, "App Button Clicked: ${item.galleryApp.appNameHumanReadable}")
            viewModel.onTabletGalleryAppClicked(item.galleryApp.appName)
        }
    }

    private val onLinkItemClickListener = OnItemClickListener { item, _ ->
        if (item is TabletGalleryLinkItem) {
            Log.d(TAG, "Link Button Clicked: ${item.galleryLink.linkDestination}")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.galleryLink.linkDestination))
            intent.setPackage(BROWSER_PACKAGE)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.d(
                    TAG,
                    "Failed to open link $item.galleryLink.linkDestination because: ${e.message}"
                )
            }
        }
    }

    private val appsRecyclerAdapter: GroupAdapter<GroupieViewHolder> =
        GroupAdapter<GroupieViewHolder>().apply {
            setOnItemClickListener(onAppItemClickListener)
        }

    private val linksRecyclerAdapter: GroupAdapter<GroupieViewHolder> =
        GroupAdapter<GroupieViewHolder>().apply {
            setOnItemClickListener(onLinkItemClickListener)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.tablet_gallery_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appsRecyclerAdapter.spanCount =
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                3
            } else {
                7
            }

        linksRecyclerAdapter.spanCount =
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                3
            } else {
                7
            }

        viewModel.galleryApps.observe(viewLifecycleOwner) {
            populateGalleryApps(it)
        }
        viewModel.galleryLinks.observe(viewLifecycleOwner) {
            populateGalleryLinks(it)
        }

        view.findViewById<RecyclerView>(R.id.gallery_apps).let {
            it.adapter = appsRecyclerAdapter
            it.layoutManager = GridLayoutManager(activity, appsRecyclerAdapter.spanCount).apply {
                spanSizeLookup = appsRecyclerAdapter.spanSizeLookup
            }
        }
        view.findViewById<RecyclerView>(R.id.links).let {
            it.adapter = linksRecyclerAdapter
            it.layoutManager = GridLayoutManager(activity, linksRecyclerAdapter.spanCount).apply {
                spanSizeLookup = linksRecyclerAdapter.spanSizeLookup
            }
        }
        view.findViewById<TextView>(R.id.nameTextView).let { textView ->
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.deviceName.observe(viewLifecycleOwner) { identifier ->
                        textView.text = identifier
                    }
                }
            }
        }
    }

    private fun populateGalleryApps(apps: List<TabletGalleryApp>) {
        appsRecyclerAdapter.clear()
        apps.forEach {
            appsRecyclerAdapter.add(TabletGalleryAppItem(it))
        }
    }

    private fun populateGalleryLinks(links: List<TabletGalleryLink>) {
        linksRecyclerAdapter.clear()
        links.forEach {
            linksRecyclerAdapter.add(TabletGalleryLinkItem(it))
        }
    }
}