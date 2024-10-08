package be.scri.interfaces

import be.scri.activities.BaseSimpleActivity

interface RenameTab {
    fun initTab(
        activity: BaseSimpleActivity,
        paths: ArrayList<String>,
    )

    fun dialogConfirmed(
        useMediaFileExtension: Boolean,
        callback: (success: Boolean) -> Unit,
    )
}
