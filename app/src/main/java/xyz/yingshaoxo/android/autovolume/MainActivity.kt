package xyz.yingshaoxo.android.autovolume

import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import com.nabinbhandari.android.permissions.PermissionHandler
import android.Manifest.permission
import android.os.Environment
import android.widget.Toast
import com.nabinbhandari.android.permissions.Permissions
import java.io.File


var started = false
class SoundMeter {
    var mediaRecorder = MediaRecorder()

    fun start(){
        if(started){
            return
        }

        mediaRecorder = MediaRecorder();

        mediaRecorder.setAudioSource(
                MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(
                MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(
                MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile("/dev/null");

        mediaRecorder.prepare();
        mediaRecorder.start()

        started = true
    }

    fun getAmplitude(): Int {
        return  mediaRecorder.maxAmplitude
    }
}

class MainActivity : AppCompatActivity() {
    val debug = true
    var record_button_was_pressed = false
    var use_button_was_pressed = false

    val soundMeter = SoundMeter()
    var handler = Handler()

    fun add_text_to_file(text: String, file_name: String = "data.txt", clean: Boolean = false) {
        val sd_main = File(Environment.getExternalStorageDirectory().toString() + "/Download/AutoVolume")
        var success = true
        if (!sd_main.exists()) {
            success = sd_main.mkdir()
        }
        if (success) {
            // directory exists or already created
            val dest = File(sd_main, file_name)
            if ( (!dest.exists()) || (clean)) {
                dest.writeText("")
            } else {
                dest.appendText(text + "\n")
            }
        } else {
            //failed to create folder
            Toast.makeText(this, "we can't write file to your storage!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab_use.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        if (debug) {
            fab_record.show()
        } else {
            fab_record.hide()
        }

        // Get the audio manager system service
        val audioManager: AudioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        button_minus.setOnClickListener {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, -1, AudioManager.FLAG_SHOW_UI)
        }

        button_keep.setOnClickListener {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        }

        button_plus.setOnClickListener {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, 1, AudioManager.FLAG_SHOW_UI)
        }

        /*
        // Get the maximum media/music volume
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        // Set the media/music volume programmatically
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
        */

        val useTask = object : Runnable {
            override fun run() {
                label_decibel.text = soundMeter.getAmplitude().toString()
                label_volume.text = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toString()

                handler.postDelayed(this, 1000)
            }
        }

        val recordTask = object : Runnable {
            override fun run() {
                label_decibel.text = soundMeter.getAmplitude().toString()
                label_volume.text = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toString()

                add_text_to_file(label_decibel.text.toString() + "," + label_volume.text.toString())

                handler.postDelayed(this, 1000)
            }
        }

        // Make sure we got permissions
        var permissions = arrayOf<String>(permission.RECORD_AUDIO, "")
        if (debug) {
            permissions.set(1, permission.WRITE_EXTERNAL_STORAGE)
        }
        Permissions.check(this/*context*/, permissions, null/*options*/, null, object : PermissionHandler() {
            override fun onGranted() {

                soundMeter.start()

                fab_use.setOnClickListener {
                    if (!use_button_was_pressed) {
                        if (record_button_was_pressed) {
                            handler.removeCallbacks(recordTask)
                            record_button_was_pressed = false
                        }
                        handler.postDelayed(useTask, 1000)
                        Snackbar.make(it, "We haven't a AI model yet...\nBut you just started Environmental decibel detector!", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show()
                        use_button_was_pressed = true
                    } else {
                        handler.removeCallbacks(useTask)
                        Snackbar.make(it, "You stopped decibel detector!", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show()
                        use_button_was_pressed = false
                    }
                }

                fab_record.setOnClickListener {
                    if (!record_button_was_pressed) {
                        if (use_button_was_pressed) {
                            handler.removeCallbacks(useTask)
                            use_button_was_pressed = false
                        }
                        handler.postDelayed(recordTask, 1000)
                        Snackbar.make(it, "Now check the /sdcord/Download/AutoVolume/data.txt\nyou shall find your data for ML", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show()
                        record_button_was_pressed = true
                    } else {
                        handler.removeCallbacks(recordTask)
                        Snackbar.make(it, "You stopped data collecting", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show()
                        record_button_was_pressed = false
                    }
                }

            }
        })

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            R.id.action_reset-> {
                add_text_to_file("", clean=true)
                Toast.makeText(this, "Data reseated!", Toast.LENGTH_LONG).show()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
