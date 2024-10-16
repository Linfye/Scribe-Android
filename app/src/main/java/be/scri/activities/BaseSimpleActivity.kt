package be.scri.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.RecoverableSecurityException
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.telecom.TelecomManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import be.scri.R
import be.scri.extensions.addBit
import be.scri.extensions.baseConfig
import be.scri.extensions.buildDocumentUriSdk30
import be.scri.extensions.createAndroidDataOrObbPath
import be.scri.extensions.createAndroidDataOrObbUri
import be.scri.extensions.createFirstParentTreeUri
import be.scri.extensions.getAppIconColors
import be.scri.extensions.getColoredDrawableWithColor
import be.scri.extensions.getContrastColor
import be.scri.extensions.getFirstParentLevel
import be.scri.extensions.getFirstParentPath
import be.scri.extensions.getProperStatusBarColor
import be.scri.extensions.getProperTextColor
import be.scri.extensions.getThemeId
import be.scri.extensions.hideKeyboard
import be.scri.extensions.humanizePath
import be.scri.extensions.isPathOnOTG
import be.scri.extensions.isPathOnSD
import be.scri.extensions.removeBit
import be.scri.extensions.showErrorToast
import be.scri.extensions.storeAndroidTreeUri
import be.scri.extensions.toast
import be.scri.extensions.updateActionBarTitle
import be.scri.extensions.updateOTGPathFromPartition
import be.scri.fragments.AboutFragment
import be.scri.helpers.APP_ICON_IDS
import be.scri.helpers.APP_LAUNCHER_NAME
import be.scri.helpers.APP_LICENSES
import be.scri.helpers.APP_NAME
import be.scri.helpers.APP_VERSION_NAME
import be.scri.helpers.CREATE_DOCUMENT_SDK_30
import be.scri.helpers.EXTERNAL_STORAGE_PROVIDER_AUTHORITY
import be.scri.helpers.INVALID_NAVIGATION_BAR_COLOR
import be.scri.helpers.MyContextWrapper
import be.scri.helpers.OPEN_DOCUMENT_TREE_FOR_ANDROID_DATA_OR_OBB
import be.scri.helpers.OPEN_DOCUMENT_TREE_FOR_SDK_30
import be.scri.helpers.OPEN_DOCUMENT_TREE_OTG
import be.scri.helpers.OPEN_DOCUMENT_TREE_SD
import be.scri.helpers.REQUEST_CODE_SET_DEFAULT_DIALER
import be.scri.helpers.SD_OTG_SHORT
import be.scri.helpers.SHOW_FAQ_BEFORE_MAIL
import be.scri.helpers.isMarshmallowPlus
import be.scri.helpers.isOreoPlus
import be.scri.helpers.isQPlus
import be.scri.helpers.isRPlus
import java.util.regex.Pattern

abstract class BaseSimpleActivity : AppCompatActivity() {
    var copyMoveCallback: ((destinationPath: String) -> Unit)? = null
    var actionOnPermission: ((granted: Boolean) -> Unit)? = null
    var isAskingPermissions = false
    var useDynamicTheme = true
    var showTransparentTop = false
    var checkedDocumentPath = ""
    var configItemsToExport = LinkedHashMap<String, Any>()

    companion object {
        var funAfterSAFPermission: ((success: Boolean) -> Unit)? = null
        var funAfterSdk30Action: ((success: Boolean) -> Unit)? = null
        var funAfterUpdate30File: ((success: Boolean) -> Unit)? = null
        var funRecoverableSecurity: ((success: Boolean) -> Unit)? = null
        private const val GENERIC_PERM_HANDLER = 100
        private const val DELETE_FILE_SDK_30_HANDLER = 300
        private const val RECOVERABLE_SECURITY_HANDLER = 301
        private const val UPDATE_FILE_SDK_30_HANDLER = 302
    }

    abstract fun getAppIconIDs(): ArrayList<Int>

    abstract fun getAppLauncherName(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        if (useDynamicTheme) {
            setTheme(getThemeId(showTransparentTop = showTransparentTop))
        }

        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (useDynamicTheme) {
            setTheme(getThemeId(showTransparentTop = showTransparentTop))

            val backgroundColor =
                if (baseConfig.isUsingSystemTheme) {
                    resources.getColor(R.color.you_background_color, theme)
                } else {
                    baseConfig.backgroundColor
                }

            updateBackgroundColor(backgroundColor)
        }

        if (showTransparentTop) {
            window.statusBarColor = Color.TRANSPARENT
        } else {
            val color =
                if (baseConfig.isUsingSystemTheme) {
                    resources.getColor(R.color.you_status_bar_color)
                } else {
                    getProperStatusBarColor()
                }

            updateActionbarColor(color)
        }

        updateRecentsAppIcon()
        updateNavigationBarColor()
    }

    override fun onDestroy() {
        super.onDestroy()
        funAfterSAFPermission = null
        actionOnPermission = null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                hideKeyboard()
                finish()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun attachBaseContext(newBase: Context) {
        if (newBase.baseConfig.useEnglish) {
            super.attachBaseContext(MyContextWrapper(newBase).wrap(newBase, "en"))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    fun updateBackgroundColor(color: Int = baseConfig.backgroundColor) {
        window.decorView.setBackgroundColor(color)
    }

    fun updateStatusbarColor(color: Int) {
        window.statusBarColor = color

        if (isMarshmallowPlus()) {
            if (color.getContrastColor() == 0xFF333333.toInt()) {
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility.addBit(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
            } else {
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility.removeBit(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
            }
        }
    }

    fun updateActionbarColor(color: Int = getProperStatusBarColor()) {
        updateActionBarTitle(supportActionBar?.title.toString(), color)
        supportActionBar?.setBackgroundDrawable(ColorDrawable(color))
        updateStatusbarColor(color)
        setTaskDescription(ActivityManager.TaskDescription(null, null, color))
    }

    fun updateNavigationBarColor(color: Int = baseConfig.navigationBarColor) {
        if (baseConfig.navigationBarColor != INVALID_NAVIGATION_BAR_COLOR) {
            try {
                val colorToUse = if (color == -2) -1 else color
                window.navigationBarColor = colorToUse

                if (isOreoPlus()) {
                    if (color.getContrastColor() == 0xFF333333.toInt()) {
                        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility.addBit(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
                    } else {
                        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility.removeBit(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
                    }
                }
            } catch (ignored: Exception) {
            }
        }
    }

    fun updateRecentsAppIcon() {
        if (baseConfig.isUsingModifiedAppIcon) {
            val appIconIDs = getAppIconIDs()
            val currentAppIconColorIndex = getCurrentAppIconColorIndex()
            if (appIconIDs.size - 1 < currentAppIconColorIndex) {
                return
            }

            val recentsIcon = BitmapFactory.decodeResource(resources, appIconIDs[currentAppIconColorIndex])
            val title = getAppLauncherName()
            val color = baseConfig.primaryColor

            val description = ActivityManager.TaskDescription(title, recentsIcon, color)
            setTaskDescription(description)
        }
    }

    fun updateMenuItemColors(
        menu: Menu?,
        useCrossAsBack: Boolean = false,
        baseColor: Int = getProperStatusBarColor(),
        updateHomeAsUpColor: Boolean = true,
        isContextualMenu: Boolean = false,
        forceWhiteIcons: Boolean = false,
    ) {
        if (menu == null) {
            return
        }

        var color = baseColor.getContrastColor()
        if (baseConfig.isUsingSystemTheme && !isContextualMenu) {
            color = getProperTextColor()
        }

        if (forceWhiteIcons) {
            color = Color.WHITE
        }

        for (i in 0 until menu.size()) {
            try {
                menu.getItem(i)?.icon?.setTint(color)
            } catch (ignored: Exception) {
            }
        }

        if (updateHomeAsUpColor && !isContextualMenu) {
            val drawableId = if (useCrossAsBack) R.drawable.ic_cross_vector else R.drawable.ic_arrow_left_vector
            val icon = resources.getColoredDrawableWithColor(drawableId, color)
            supportActionBar?.setHomeAsUpIndicator(icon)
        }
    }

    private fun getCurrentAppIconColorIndex(): Int {
        val appIconColor = baseConfig.appIconColor
        getAppIconColors().forEachIndexed { index, color ->
            if (color == appIconColor) {
                return index
            }
        }
        return 0
    }

    fun setTranslucentNavigation() {
        window.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        resultData: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, resultData)
        val partition: String =
            if (checkedDocumentPath.length >= 18) {
                checkedDocumentPath.substring(9, 18)
            } else {
                showErrorToast("An unexpected error occurred while extracting the substring.")
                ""
            }

        val sdOtgPattern = Pattern.compile(SD_OTG_SHORT)
        if (requestCode == CREATE_DOCUMENT_SDK_30) {
            if (resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
                val treeUri = resultData.data
                val checkedUri = buildDocumentUriSdk30(checkedDocumentPath)

                if (treeUri != checkedUri) {
                    toast(getString(R.string.wrong_folder_selected, checkedDocumentPath))
                    return
                }

                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                applicationContext.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                val funAfter = funAfterSdk30Action
                funAfterSdk30Action = null
                funAfter?.invoke(true)
            } else {
                funAfterSdk30Action?.invoke(false)
            }
        } else if (requestCode == OPEN_DOCUMENT_TREE_FOR_SDK_30) {
            if (resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
                val treeUri = resultData.data
                val checkedUri = createFirstParentTreeUri(checkedDocumentPath)

                if (treeUri != checkedUri) {
                    val level = getFirstParentLevel(checkedDocumentPath)
                    val firstParentPath = checkedDocumentPath.getFirstParentPath(this, level)
                    toast(getString(R.string.wrong_folder_selected, humanizePath(firstParentPath)))
                    return
                }

                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                applicationContext.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                val funAfter = funAfterSdk30Action
                funAfterSdk30Action = null
                funAfter?.invoke(true)
            } else {
                funAfterSdk30Action?.invoke(false)
            }
        } else if (requestCode == OPEN_DOCUMENT_TREE_FOR_ANDROID_DATA_OR_OBB) {
            if (resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
                if (isProperAndroidRoot(checkedDocumentPath, resultData.data!!)) {
                    if (resultData.dataString == baseConfig.otgTreeUri || resultData.dataString == baseConfig.sdTreeUri) {
                        val pathToSelect = createAndroidDataOrObbPath(checkedDocumentPath)
                        toast(getString(R.string.wrong_folder_selected, pathToSelect))
                        return
                    }

                    val treeUri = resultData.data
                    storeAndroidTreeUri(checkedDocumentPath, treeUri.toString())

                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    applicationContext.contentResolver.takePersistableUriPermission(treeUri!!, takeFlags)
                    funAfterSAFPermission?.invoke(true)
                    funAfterSAFPermission = null
                } else {
                    toast(getString(R.string.wrong_folder_selected, createAndroidDataOrObbPath(checkedDocumentPath)))
                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        if (isRPlus()) {
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, createAndroidDataOrObbUri(checkedDocumentPath))
                        }

                        try {
                            startActivityForResult(this, requestCode)
                        } catch (e: ActivityNotFoundException) {
                            showErrorToast("No application found to handle this request.")
                        } catch (e: SecurityException) {
                            showErrorToast("Security exception: ${e.message}")
                        } catch (e: IllegalArgumentException) {
                            showErrorToast("Invalid argument provided: ${e.message}")
                        }
                    }
                }
            } else {
                funAfterSAFPermission?.invoke(false)
            }
        } else if (requestCode == OPEN_DOCUMENT_TREE_SD) {
            if (resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
                val isProperPartition =
                    partition.isEmpty() ||
                        !sdOtgPattern.matcher(partition).matches() ||
                        (
                            sdOtgPattern
                                .matcher(partition)
                                .matches() &&
                                resultData.dataString!!.contains(partition)
                        )
                if (isProperSDRootFolder(resultData.data!!) && isProperPartition) {
                    if (resultData.dataString == baseConfig.otgTreeUri) {
                        toast(R.string.sd_card_usb_same)
                        return
                    }

                    saveTreeUri(resultData)
                    funAfterSAFPermission?.invoke(true)
                    funAfterSAFPermission = null
                } else {
                    toast(R.string.wrong_root_selected)
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

                    try {
                        startActivityForResult(intent, requestCode)
                    } catch (e: ActivityNotFoundException) {
                        showErrorToast("No application found to handle this request.")
                    } catch (e: SecurityException) {
                        showErrorToast("Security exception: ${e.message}")
                    } catch (e: IllegalArgumentException) {
                        showErrorToast("Invalid argument provided: ${e.message}")
                    }
                }
            } else {
                funAfterSAFPermission?.invoke(false)
            }
        } else if (requestCode == OPEN_DOCUMENT_TREE_OTG) {
            if (resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
                val isProperPartition =
                    partition.isEmpty() ||
                        !sdOtgPattern.matcher(partition).matches() ||
                        (
                            sdOtgPattern
                                .matcher(partition)
                                .matches() &&
                                resultData.dataString!!.contains(partition)
                        )
                if (isProperOTGRootFolder(resultData.data!!) && isProperPartition) {
                    if (resultData.dataString == baseConfig.sdTreeUri) {
                        funAfterSAFPermission?.invoke(false)
                        toast(R.string.sd_card_usb_same)
                        return
                    }
                    baseConfig.otgTreeUri = resultData.dataString!!
                    baseConfig.otgPartition =
                        baseConfig.otgTreeUri
                            .removeSuffix("%3A")
                            .substringAfterLast('/')
                            .trimEnd('/')
                    updateOTGPathFromPartition()

                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    applicationContext.contentResolver.takePersistableUriPermission(resultData.data!!, takeFlags)

                    funAfterSAFPermission?.invoke(true)
                    funAfterSAFPermission = null
                } else {
                    toast(R.string.wrong_root_selected_usb)
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)

                    try {
                        startActivityForResult(intent, requestCode)
                    } catch (e: ActivityNotFoundException) {
                        showErrorToast("No application found to handle this request.")
                    } catch (e: SecurityException) {
                        showErrorToast("Security exception: ${e.message}")
                    }
                }
            } else {
                funAfterSAFPermission?.invoke(false)
            }
        } else if (requestCode == Companion.DELETE_FILE_SDK_30_HANDLER) {
            funAfterSdk30Action?.invoke(resultCode == Activity.RESULT_OK)
        } else if (requestCode == Companion.RECOVERABLE_SECURITY_HANDLER) {
            funRecoverableSecurity?.invoke(resultCode == Activity.RESULT_OK)
            funRecoverableSecurity = null
        } else if (requestCode == Companion.UPDATE_FILE_SDK_30_HANDLER) {
            funAfterUpdate30File?.invoke(resultCode == Activity.RESULT_OK)
        }
    }

    private fun saveTreeUri(resultData: Intent) {
        val treeUri = resultData.data
        baseConfig.sdTreeUri = treeUri.toString()

        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        applicationContext.contentResolver.takePersistableUriPermission(treeUri!!, takeFlags)
    }

    private fun isProperSDRootFolder(uri: Uri) = isExternalStorageDocument(uri) && isRootUri(uri) && !isInternalStorage(uri)

    private fun isProperSDFolder(uri: Uri) = isExternalStorageDocument(uri) && !isInternalStorage(uri)

    private fun isProperOTGRootFolder(uri: Uri) = isExternalStorageDocument(uri) && isRootUri(uri) && !isInternalStorage(uri)

    private fun isProperOTGFolder(uri: Uri) = isExternalStorageDocument(uri) && !isInternalStorage(uri)

    private fun isRootUri(uri: Uri) = uri.lastPathSegment?.endsWith(":") ?: false

    private fun isInternalStorage(uri: Uri) = isExternalStorageDocument(uri) && DocumentsContract.getTreeDocumentId(uri).contains("primary")

    private fun isAndroidDir(uri: Uri) = isExternalStorageDocument(uri) && DocumentsContract.getTreeDocumentId(uri).contains(":Android")

    private fun isInternalStorageAndroidDir(uri: Uri) = isInternalStorage(uri) && isAndroidDir(uri)

    private fun isOTGAndroidDir(uri: Uri) = isProperOTGFolder(uri) && isAndroidDir(uri)

    private fun isSDAndroidDir(uri: Uri) = isProperSDFolder(uri) && isAndroidDir(uri)

    private fun isExternalStorageDocument(uri: Uri) = EXTERNAL_STORAGE_PROVIDER_AUTHORITY == uri.authority

    private fun isProperAndroidRoot(
        path: String,
        uri: Uri,
    ): Boolean =
        when {
            isPathOnOTG(path) -> isOTGAndroidDir(uri)
            isPathOnSD(path) -> isSDAndroidDir(uri)
            else -> isInternalStorageAndroidDir(uri)
        }

    fun startAboutActivity(
        appNameId: Int,
        licenseMask: Int,
        versionName: String,
        showFAQBeforeMail: Boolean,
    ) {
        hideKeyboard()
        Intent(applicationContext, AboutFragment::class.java).apply {
            putExtra(APP_ICON_IDS, getAppIconIDs())
            putExtra(APP_LAUNCHER_NAME, getAppLauncherName())
            putExtra(APP_NAME, getString(appNameId))
            putExtra(APP_LICENSES, licenseMask)
            putExtra(APP_VERSION_NAME, versionName)
            putExtra(SHOW_FAQ_BEFORE_MAIL, showFAQBeforeMail)
            startActivity(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun handleOTGPermission(callback: (success: Boolean) -> Unit) {
        hideKeyboard()
        if (baseConfig.otgTreeUri.isNotEmpty()) {
            callback(true)
            return
        }

        funAfterSAFPermission = callback
    }

    @SuppressLint("NewApi")
    fun deleteSDK30Uris(
        uris: List<Uri>,
        callback: (success: Boolean) -> Unit,
    ) {
        hideKeyboard()
        if (isRPlus()) {
            funAfterSdk30Action = callback
            try {
                val deleteRequest = MediaStore.createDeleteRequest(contentResolver, uris).intentSender
                startIntentSenderForResult(deleteRequest, Companion.DELETE_FILE_SDK_30_HANDLER, null, 0, 0, 0)
            } catch (e: SecurityException) {
                showErrorToast("Security exception: ${e.message}")
            } catch (e: IllegalArgumentException) {
                showErrorToast("Illegal argument: ${e.message}")
            }
        } else {
            callback(false)
        }
    }

    @SuppressLint("NewApi")
    fun updateSDK30Uris(
        uris: List<Uri>,
        callback: (success: Boolean) -> Unit,
    ) {
        hideKeyboard()
        if (isRPlus()) {
            funAfterUpdate30File = callback
            try {
                val writeRequest = MediaStore.createWriteRequest(contentResolver, uris).intentSender
                startIntentSenderForResult(writeRequest, Companion.UPDATE_FILE_SDK_30_HANDLER, null, 0, 0, 0)
            } catch (e: SecurityException) {
                showErrorToast("Security exception: ${e.message}")
            } catch (e: IllegalArgumentException) {
                showErrorToast("Illegal argument: ${e.message}")
            }
        } else {
            callback(false)
        }
    }

    @SuppressLint("NewApi")
    fun handleRecoverableSecurityException(callback: (success: Boolean) -> Unit) {
        try {
            callback.invoke(true)
        } catch (securityException: SecurityException) {
            if (isQPlus()) {
                funRecoverableSecurity = callback
                val recoverableSecurityException = securityException as? RecoverableSecurityException ?: throw securityException
                val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                startIntentSenderForResult(intentSender, Companion.RECOVERABLE_SECURITY_HANDLER, null, 0, 0, 0)
            } else {
                callback(false)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        isAskingPermissions = false
        if (requestCode == Companion.GENERIC_PERM_HANDLER && grantResults.isNotEmpty()) {
            actionOnPermission?.invoke(grantResults[0] == 0)
        }
    }

    @SuppressLint("InlinedApi")
    protected fun launchSetDefaultDialerIntent() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                startActivityForResult(intent, REQUEST_CODE_SET_DEFAULT_DIALER)
            }
        } else {
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName).apply {
                try {
                    startActivityForResult(this, REQUEST_CODE_SET_DEFAULT_DIALER)
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.no_app_found)
                }
            }
        }
    }
}
