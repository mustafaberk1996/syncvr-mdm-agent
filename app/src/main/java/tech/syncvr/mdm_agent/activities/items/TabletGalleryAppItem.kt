package tech.syncvr.mdm_agent.activities.items

import android.view.View
import com.xwray.groupie.viewbinding.BindableItem
import tech.syncvr.mdm_agent.R
import tech.syncvr.mdm_agent.databinding.ItemTabletGalleryAppBinding

class TabletGalleryAppItem(val galleryApp: TabletGalleryApp) :
    BindableItem<ItemTabletGalleryAppBinding>() {

    override fun bind(viewBinding: ItemTabletGalleryAppBinding, position: Int) {
        viewBinding.appName.text = galleryApp.appNameHumanReadable
        viewBinding.appIcon.setImageDrawable(galleryApp.icon)
    }

    override fun getLayout(): Int = R.layout.item_tablet_gallery_app

    override fun initializeViewBinding(view: View): ItemTabletGalleryAppBinding =
        ItemTabletGalleryAppBinding.bind(view)

    override fun getSpanSize(spanCount: Int, position: Int): Int = 1

}