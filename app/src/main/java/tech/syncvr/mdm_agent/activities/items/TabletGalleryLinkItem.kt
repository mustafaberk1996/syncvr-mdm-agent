package tech.syncvr.mdm_agent.activities.items

import android.view.View
import com.xwray.groupie.viewbinding.BindableItem
import tech.syncvr.mdm_agent.R
import tech.syncvr.mdm_agent.databinding.ItemTabletGalleryLinkBinding

class TabletGalleryLinkItem(val galleryLink: TabletGalleryLink) :
    BindableItem<ItemTabletGalleryLinkBinding>() {

    override fun bind(viewBinding: ItemTabletGalleryLinkBinding, position: Int) {
        viewBinding.linkName.text = galleryLink.linkName
    }

    override fun getLayout(): Int = R.layout.item_tablet_gallery_link

    override fun initializeViewBinding(view: View): ItemTabletGalleryLinkBinding =
        ItemTabletGalleryLinkBinding.bind(view)

    override fun getSpanSize(spanCount: Int, position: Int): Int = 1
}