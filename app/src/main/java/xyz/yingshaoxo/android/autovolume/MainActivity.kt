package xyz.yingshaoxo.android.autovolume

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
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import com.nabinbhandari.android.permissions.Permissions
import com.pixplicity.easyprefs.library.Prefs
import java.io.File
import java.lang.Exception
import java.util.*

var storage_path = File(Environment.getExternalStorageDirectory().toString() + "/Download/AutoVolume")
fun make_sure_we_have_folder_to_store_something(): Boolean {
    val sd_main = File(Environment.getExternalStorageDirectory().toString() + "/Download/AutoVolume")
    var success = true
    if (!sd_main.exists()) {
        success = sd_main.mkdir()
    }
    return success
}

class SoundMeter {
    lateinit var mediaRecorder: MediaRecorder
    lateinit var recording_file: File
    var started = false

    fun start(){
        mediaRecorder = MediaRecorder();

        mediaRecorder.setAudioSource(
                MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(
                MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(
                MediaRecorder.AudioEncoder.AMR_NB);
        val file_name = Date().toString() + ".amr"
        recording_file = File(storage_path, file_name)
        mediaRecorder.setOutputFile(recording_file);

        mediaRecorder.prepare();
        mediaRecorder.start()

        this.started = true
    }

    fun pause() {
        mediaRecorder.pause()
    }

    fun resume() {
        mediaRecorder.resume()
    }

    fun stop() {
        mediaRecorder.stop()
        mediaRecorder.reset()
        mediaRecorder.release()

        this.started = false
    }

    fun delete_recording() {
        this.recording_file.delete()
    }

    fun getAmplitude(): Int {
        if (this.started) {
            return mediaRecorder.maxAmplitude
        } else {
            return 0
        }
    }
}

class MainActivity : AppCompatActivity() {
    var env_button_was_pressed = false
    var voice_button_was_pressed = false

    val soundMeter = SoundMeter()
    var handler = Handler()

    var amplitude_for_split = 0
    var max_amplitude = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fun set_tips(text: String) {
            Tip.setText(text)
        }

        Prefs.Builder()
                .setContext(this)
                .setMode(ContextWrapper.MODE_PRIVATE)
                .setPrefsName(packageName)
                .setUseDefaultSharedPreference(true)
                .build()

        amplitude_for_split = Prefs.getInt("amplitude_for_split", 0)
        gate.setText(amplitude_for_split.toString())

        fun update_amplitude_from_texteditor() {
            var amplitude = 0
            try {
                amplitude = gate.text.toString().toInt()
            }
            catch (e: Exception) {
                amplitude = 0
            }
            Prefs.putInt("amplitude_for_split", amplitude)
            amplitude_for_split = amplitude
        }

        gate.addTextChangedListener(object : TextWatcher{
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                update_amplitude_from_texteditor()
            }
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })

        fun print(text: String) {
            Snackbar.make(textView, text, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        fun update_amplitude(): Int {
            var amplitude = soundMeter.getAmplitude()
            label_amplitude.text = amplitude.toString()
            return amplitude
        }

        val EnvTask = object : Runnable {
            override fun run() {
                val amplitude = update_amplitude()

                if (amplitude > max_amplitude) {
                    max_amplitude = amplitude
                }

                gate.setText(max_amplitude.toString())

                handler.postDelayed(this, 1000)
            }
        }

        val VoiceTask = object : Runnable {
            override fun run() {
                val amplitude = update_amplitude()

                if (amplitude < amplitude_for_split) {
                    soundMeter.pause()
                    set_tips("声音较小\n就当成背景音吧！\n...暂停录制...")
                } else {
                    soundMeter.resume()
                    set_tips("有人说话\n看我大显身手\n...正在录制...")
                }
                
                handler.postDelayed(this, 50)
            }
        }

        // Make sure we got permissions
        var permissions = arrayOf<String>(permission.RECORD_AUDIO, "")
        permissions.set(1, permission.WRITE_EXTERNAL_STORAGE)
        Permissions.check(this/*context*/, permissions, null/*options*/, null, object : PermissionHandler() {
            override fun onGranted() {
                make_sure_we_have_folder_to_store_something()

                fab_record_env.setOnClickListener {
                    if (!voice_button_was_pressed) {
                        if (!env_button_was_pressed) {
                            soundMeter.start()
                            handler.postDelayed(EnvTask, 1000)
                            env_button_was_pressed = true
                            print("Click it again to stop detection")
                            set_tips("我正在检测周围环境音量\n(嘘~ 不要说话)\n再次点击左边的按钮停止检测")
                        } else if (env_button_was_pressed) {
                            handler.removeCallbacks(EnvTask)
                            soundMeter.stop()
                            soundMeter.delete_recording()
                            env_button_was_pressed = false
                            print("Detection stoped")
                            set_tips("好了，如果你满意下面框中的阈值\n点击右边的按钮开始录音")
                        }
                    } else if (voice_button_was_pressed) {
                        print("点另一个按钮试试")
                    }
                }

                fab_record_voice.setOnClickListener {
                    if (!env_button_was_pressed) {
                        if (!voice_button_was_pressed) {
                            update_amplitude_from_texteditor()
                            soundMeter.start()
                            handler.postDelayed(VoiceTask, 50)
                            voice_button_was_pressed = true
                            print("Recording started")
                            set_tips("录制开始了")
                        } else if (voice_button_was_pressed) {
                            handler.removeCallbacks(VoiceTask)
                            soundMeter.stop()
                            voice_button_was_pressed = false
                            print("Check out /sdcord/Download/AutoVolume")
                            set_tips("好吧，我录完了\n去 Download/AutoVolume 查看文件")
                        }
                    } else if (env_button_was_pressed) {
                        print("点另一个按钮试试")
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

    fun openNewTabWindow(urls: String, context : Context) {
        val uris = Uri.parse(urls)
        val intents = Intent(Intent.ACTION_VIEW, uris)
        val b = Bundle()
        b.putBoolean("new_window", true)
        intents.putExtras(b)
        context.startActivity(intents)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            R.id.action_reset-> {
                return true
            }
            R.id.action_about-> {
                openNewTabWindow("https://yingshaoxo.xyz", this)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
