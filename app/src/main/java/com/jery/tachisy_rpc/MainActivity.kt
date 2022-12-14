@file:Suppress("UseSwitchCompatOrMaterialCode", "UnusedImport", "UNUSED_VARIABLE", "SetTextI18n",
    "UsePropertyAccessSyntax", "SpellCheckingInspection", "StaticFieldLeak",
    "LiftReturnOrAssignment", "DEPRECATION", "BatteryLife", "ApplySharedPref", "UNUSED_ANONYMOUS_PARAMETER",
    "UNUSED_PARAMETER", "CommitPrefEdits", "MissingInflatedId",
    "RemoveEmptyParenthesesFromLambdaCall"
)

package com.jery.tachisy_rpc

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import com.blankj.utilcode.util.FileUtils.delete
import com.blankj.utilcode.util.NotificationUtils
import com.google.android.material.chip.Chip
import com.jery.tachisy_rpc.utils.Logic
import java.io.File


class MainActivity : AppCompatActivity() {
    companion object {
        lateinit var Token         : String
        lateinit var chpUsername   : Chip
        lateinit var chpName       : Chip
        lateinit var chpState      : Chip
        lateinit var edtDetails    : EditText
        lateinit var numType       : NumberPicker
        lateinit var numChapter    : NumberPicker
        lateinit var swtSwitch     : Switch

        lateinit var sharedPreferences : SharedPreferences
        lateinit var prefsEditor: SharedPreferences.Editor
        lateinit var arrayOfTypes: Array<String>    // ["Vol", "Ch", "Ep", ""]
        lateinit var Main_Context: Context
        lateinit var Main_Activity: Activity
    }

    override fun onCreateOptionsMenu(menu: Menu) : Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
    // Everything below this will be done when the app is opened.
        super.onCreate(null)
        setContentView(R.layout.activity_main)

        // assign valus to some lateinit vars
        sharedPreferences = getSharedPreferences("lastState", Context.MODE_PRIVATE)
        prefsEditor = sharedPreferences.edit()
        arrayOfTypes = resources.getStringArray(R.array.array_of_types)
        Main_Context = this; Main_Activity = this

        // restore the last set theme
        AppCompatDelegate.setDefaultNightMode(sharedPreferences.getInt("keyTheme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))

        // assign the screen components to variables that can be accessed from within other classes and methods.
        chpUsername = findViewById(R.id.chpUsername)
        chpName = findViewById(R.id.chpName)
        chpState = findViewById(R.id.chpState)
        edtDetails = findViewById(R.id.edtDetails)
        numType = findViewById(R.id.numType)
        numChapter = findViewById(R.id.numChapter)
        swtSwitch = findViewById(R.id.swtRPC)

        // From sharedPrefs, restore the Token, name and then remaining keys
        Token = sharedPreferences.getString("token",getString(R.string.Discord_Token)).toString()
        chpName.setText(sharedPreferences.getString("keyName",Logic.v_TachiyomiSy))
        chpState.setText(sharedPreferences.getString("keyState", Logic.v_Manga))
        // set the saved chpType from sharedPrefs
        numChapter.value = sharedPreferences.getInt("keyCh", 0)
        // load the correct states
        restoreFromLastState()
        // load the right chipIcon when restoring lastState
        Logic.restoreCorrectDataOnCreate(this)

        // restore the details entered last if MyService is running
        if (isServiceRunning(MyService::class.java)) {
            chpName.setText(MyService.setName)
            chpState.setText(MyService.setState)
            edtDetails.setText(MyService.setDetails)
            numType.setValue(MyService.setType)
            numChapter.setValue(MyService.setCh)
            swtSwitch.isChecked = true
        }

        // Get user's discord token
        chpUsername.setText(Token)
        chpUsername.setOnCloseIconClickListener {
            // Start the LoginDiscord activity and return to MainActivity when it calls finish()
            if ((Token == getString(R.string.Discord_Token)) || (Token == "")) {
                val intent = Intent(this, LoginDiscord::class.java)
                startActivityForResult(intent, 1)
            } else resetDiscordToken(null)
        }

        // switch between differnet presets
        chpName.setOnCloseIconClickListener {
            nameWasChanged(this)
            restoreFromLastState()
        }

        // inter-switch the name and state chips
        chpState.setOnCloseIconClickListener {
            stateWasChanged(this)
        }

        // Setup numType's options and restore last state
        numType.minValue = 0
        numType.maxValue = arrayOfTypes.size -1
        numType.wrapSelectorWheel = false
        numType.displayedValues = arrayOfTypes
        numType.setOnValueChangedListener { numType, oldVal, newVal -> var chType = arrayOfTypes[numType.value] }
        numType.value = sharedPreferences.getInt("keyType", 0)

        // Setup numChapter's min and max and restore last state.
        numChapter.minValue = 0
        numChapter.maxValue = 9999
        numChapter.wrapSelectorWheel = false
        numChapter.value = sharedPreferences.getInt("keyCh", 0)

        // Start or Stop RPC and save details to sharedPrefs on click
        swtSwitch.setOnClickListener {
            if ((Token == getString(R.string.Discord_Token)) || (Token == "")) {
                Toast.makeText(this, getString(R.string.Enter_your_discord_token_first), Toast.LENGTH_SHORT).show()
                swtSwitch.isChecked = false
            }
            else if (Token.matches(Regex("^.+$")))   // need to change this lol
            {
                if (swtSwitch.isChecked()) {
                    prefsEditor.putLong("keyStartTimestamp", System.currentTimeMillis()).commit()
                    startService(Intent(this, MyService::class.java))
                } else
                    stopService(Intent(this, MyService::class.java))
            } else {
                Toast.makeText(this,getString(R.string.Recheck_token_for_typos) , Toast.LENGTH_SHORT).show()
                swtSwitch.isChecked = false
            }
        }

        // Easter Egg!! - Long click paste btn in Activity Details to copy the details to clipboard
        findViewById<ImageButton>(R.id.imgPasteDetails).setOnLongClickListener() {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Activity Details", edtDetails.text.toString())
            clipboard.setPrimaryClip(clip!!)
            Toast.makeText(this, "Copied text to clipboard!", Toast.LENGTH_SHORT).show()
            return@setOnLongClickListener false
        }
    }

    // Copy text in edtDetails to the clipboard
    fun pasteDetails(view: View?) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipItem = clipboard.primaryClip!!.getItemAt(0)
        edtDetails.setText(clipItem.text!!.toString())
    }

    // Long press footer to disable battery optimization
    fun ignoreBatteryOptimization(view: View?) {
        val packageName = packageName
        val intent = Intent()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else {
            Toast.makeText(
                this@MainActivity,
                getString(R.string.Battery_optimization_already_disabled),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Sign out of discord
    fun resetDiscordToken(view: View?) {
        // get user's confirmation for resetting discord token
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.Discord_Login))
            .setMessage(getString(R.string.Login_to_different_discord_account))
            .setPositiveButton(getString(R.string.Continue)) { dialog, which ->
                // Delete any exisitng instances of Token in webview cache
                delete(File(filesDir.parentFile, "app_webview/Default/Local Storage/leveldb"))
                // remove the saved token
                prefsEditor.remove("token").commit()
                // restart the application
                restartApp(null)
            }
            .setNegativeButton(getString(R.string.Cancel), null)
            .show()
    }
    fun menuSwitchAccount(mi: MenuItem?) {resetDiscordToken(null)}

    // open this app's repo on GitHub in browser
    fun openGithubRepo(view: View?) {
         startActivity( Intent(
             Intent.ACTION_VIEW,
             Uri.parse("https://github.com/jeryjs/TachiSY_RPC")
         ) )
    }

    // check whether a given service is running or not and return it as a boolean value.
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className)
                return true
        }
        return false
    }

    // Function to restart this app
    fun restartApp(mi: MenuItem?) {
        stopService(Intent(this, MyService::class.java))
        val ctx = applicationContext; val pm = ctx.packageManager
        val intent = pm.getLaunchIntentForPackage(ctx.packageName)
        val mainIntent = Intent.makeRestartActivityTask(intent!!.component)
        ctx.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }

    fun themeSwitch(item: MenuItem) {
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        else
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        prefsEditor.putInt("keyTheme", AppCompatDelegate.getDefaultNightMode()).commit()
    }


    /**
     * Set the correct chpName, chpState, chipIcons and numType when the chpName is changed
     * @param activity The activity that calls this function
     */
    private fun nameWasChanged(activity: Activity = this) {
        when {
            chpName.getText().toString() == Logic.v_TachiyomiSy -> {
                chpName.text = Logic.v_LightNovel
                chpState.text = Logic.v_MoonReader
                chpName.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_reading_ln)
                chpState.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_moon_reader)
                numType.value = 0
            }
            chpName.getText().toString() == Logic.v_LightNovel || chpName.getText().toString() == Logic.v_MoonReader -> {
                chpName.text = Logic.v_Aniyomi
                chpState.text = Logic.v_Anime
                chpName.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_aniyomi)
                chpState.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_watching)
                numType.value = 2
            }
            chpName.getText().toString() == Logic.v_Aniyomi || chpName.getText().toString() == Logic.v_Anime -> {
                chpName.text = Logic.v_Mangago
                chpState.text = Logic.v_Manga
                chpName.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_mangago)
                chpState.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_reading)
                numType.value = 0
            }
            chpName.getText().toString() == Logic.v_Mangago -> {
                chpName.text = Logic.v_Webtoon
                chpState.text = Logic.v_Reading
                chpName.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_webtoon)
                chpState.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_reading)
                numType.value = 2
            }
            chpName.getText().toString() == Logic.v_Webtoon -> {
                chpName.text = Logic.v_TachiyomiSy
                chpState.text = Logic.v_Manga
                chpName.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_tachiyomi)
                chpState.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_reading)
                numType.value = 0
            }
            else -> {
                chpName.text = Logic.v_TachiyomiSy
                chpState.text = Logic.v_Manga
                chpName.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_tachiyomi)
                chpState.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_reading)
                numType.value = 0
            }
        }
    }

    /**
     * Set the correct chpName, chpState and chipIcon when the chpState is changed
     * @param activity The activity that calls this function
     */
    private fun stateWasChanged(activity: Activity) {
        when {
            chpState.getText().toString() == Logic.v_Manga -> {
                chpState.text = Logic.v_Manhwa
            }
            chpState.getText().toString() == Logic.v_Manhwa -> {
                chpState.text = Logic.v_Manga
            }
            chpState.getText().toString() == Logic.v_LightNovel -> {
                chpName.text = Logic.v_LightNovel
                chpState.text = Logic.v_MoonReader
                chpName.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_reading_ln)
                chpState.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_moon_reader)
            }
            chpState.getText().toString() == Logic.v_MoonReader -> {
                chpName.text = Logic.v_MoonReader
                chpState.text = Logic.v_LightNovel
                chpName.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_moon_reader)
                chpState.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_reading_ln)
            }
            chpState.getText().toString() == Logic.v_Aniyomi -> {
                chpName.text = Logic.v_Aniyomi
                chpState.text = Logic.v_Anime
                chpName.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_aniyomi)
                chpState.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_watching)
            }
            chpState.getText().toString() == Logic.v_Anime -> {
                chpName.text = Logic.v_Anime
                chpState.text = Logic.v_Aniyomi
                chpName.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_watching)
                chpState.chipIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_aniyomi)
            }
        }
    }
    
    /**
     * restore all details from sharedPrefs
     * @param
     */
    private fun restoreFromLastState() {
        // set the saved edtDetails from sharedPrefs
        when (chpName.text) {
            Logic.v_TachiyomiSy -> edtDetails.setText(sharedPreferences.getString("keyDetails_tachi", ""))
            Logic.v_LightNovel, Logic.v_MoonReader -> edtDetails.setText(sharedPreferences.getString("keyDetails_ln", ""))
            Logic.v_Aniyomi, Logic.v_Anime -> edtDetails.setText(sharedPreferences.getString("keyDetails_anime", ""))
            Logic.v_Mangago -> edtDetails.setText(sharedPreferences.getString("keyDetails_mangago", ""))
            Logic.v_Webtoon -> edtDetails.setText(sharedPreferences.getString("keyDetails_webtoon", ""))
        }
    }

    fun persistentTimestamp(item: MenuItem) {
        if (sharedPreferences.getBoolean("keyPersistentTimestamp", false))  //true
            prefsEditor.putBoolean("keyPersistentTimestamp", false).commit()
        else    //false
            prefsEditor.putBoolean("keyPersistentTimestamp", true).commit()
        Toast.makeText(this, "Persistent Timestamp: ${sharedPreferences.getBoolean("keyPersistentTimestamp", false)}", Toast.LENGTH_SHORT).show()
    }
}