package br.app.amplificador

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private lateinit var gainText: TextView
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var seek: SeekBar
    private lateinit var systemVolume: SeekBar
    private lateinit var bass: SeekBar
    private lateinit var surround: SeekBar
    private lateinit var equalizer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { refresh(); handler.postDelayed(this, 1000) } }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = getSharedPreferences("amp", MODE_PRIVATE)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(32), dp(20), dp(28)) }
        scroll.addView(root)

        root.addView(TextView(this).apply { text="AMPLIFICADOR"; textSize=28f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(25,25,35)) })
        root.addView(TextView(this).apply { text="Som mais forte, com controles rápidos e efeitos"; textSize=14f; setPadding(0,dp(4),0,dp(20)) })

        gainText = TextView(this).apply { textSize=46f; gravity=Gravity.CENTER; setTypeface(typeface,Typeface.BOLD); setPadding(0,dp(18),0,0) }
        root.addView(gainText, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply { text="AMPLIFICAÇÃO EXTRA"; gravity=Gravity.CENTER; textSize=12f; setPadding(0,0,0,dp(8)) })
        seek = SeekBar(this).apply { max=15; progress=p.getInt("db",6).coerceIn(0,15) }
        root.addView(seek)
        updateGainLabel(seek.progress)

        val presets = LinearLayout(this).apply { gravity=Gravity.CENTER; orientation=LinearLayout.HORIZONTAL; setPadding(0,dp(8),0,dp(8)) }
        listOf(0,6,10,15).forEach { value ->
            val b=Button(this).apply { text= if(value==0) "Normal" else "+${value}dB"; textSize=12f; setOnClickListener { seek.progress=value } }
            presets.addView(b, LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(2),0,dp(2),0) })
        }
        root.addView(presets)

        toggle=Button(this).apply { textSize=18f; setPadding(0,dp(8),0,dp(8)) }
        root.addView(toggle, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) })
        status=TextView(this).apply { textSize=13f; setPadding(dp(12),dp(14),dp(12),dp(14)); setBackgroundColor(Color.rgb(242,244,248)) }
        root.addView(status, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12) })

        section(root,"CONTROLES DE ÁUDIO")
        systemVolume=slider(root,"Volume do sistema",p.getInt("volume",80),100) { v -> saveAndSend("volume",v) }
        bass=slider(root,"Bass Boost",p.getInt("bass",0),100) { v -> saveAndSend("bass",v) }
        surround=slider(root,"Surround / Virtualizer",p.getInt("virtual",0),100) { v -> saveAndSend("virtual",v) }

        section(root,"EQUALIZADOR")
        equalizer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(equalizer)
        val bands=listOf("Graves 60 Hz","Graves 230 Hz","Médios 910 Hz","Presença 3,6 kHz","Agudos 14 kHz")
        bands.forEachIndexed { i,name -> slider(equalizer,name,p.getInt("eq$i",50),100) { v -> saveAndSend("eq$i",v) } }

        root.addView(TextView(this).apply { text="⚠️ Volume alto pode causar distorção e danos à audição. Comece com níveis baixos."; textSize=12f; setPadding(0,dp(22),0,0) })
        setContentView(scroll)

        seek.setOnSeekBarChangeListener(listener { v -> updateGainLabel(v); saveAndSend("db",v) })
        toggle.setOnClickListener {
            if (BoostService.running) stopService(Intent(this,BoostService::class.java)) else {
                if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)
                startServiceNow()
            }
            handler.postDelayed({refresh()},350)
        }
    }
    private fun section(root:LinearLayout,title:String){ root.addView(TextView(this).apply { text=title; textSize=13f; setTypeface(typeface,Typeface.BOLD); setPadding(0,dp(24),0,dp(6)) }) }
    private fun slider(root:LinearLayout,label:String,progress:Int,max:Int,onChange:(Int)->Unit):SeekBar {
        root.addView(TextView(this).apply { text=label; textSize=14f; setPadding(0,dp(8),0,0) })
        return SeekBar(this).apply { this.max=max; this.progress=progress.coerceIn(0,max); root.addView(this); setOnSeekBarChangeListener(listener { if(it>=0) onChange(it) }) }
    }
    private fun listener(on:(Int)->Unit)=object:SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(b:SeekBar,p:Int,f:Boolean){ if(f) on(p) }; override fun onStartTrackingTouch(b:SeekBar){}; override fun onStopTrackingTouch(b:SeekBar){} }
    private fun saveAndSend(key:String,value:Int){ getSharedPreferences("amp",MODE_PRIVATE).edit().putInt(key,value).apply(); if(BoostService.running) startService(Intent(this,BoostService::class.java).putExtra(BoostService.EXTRA_REFRESH,true)) }
    private fun startServiceNow(){ startForegroundService(Intent(this,BoostService::class.java)) }
    private fun updateGainLabel(db:Int){ gainText.text=if(db==0) "NORMAL" else "+$db dB" }
    private fun refresh(){
        val on=BoostService.running; toggle.text=if(on) "DESLIGAR AMPLIFICADOR" else "LIGAR AMPLIFICADOR"
        status.text=if(!on) "● Desligado\nAtive para aplicar os efeitos compatíveis com o seu aparelho." else "● Amplificador ativo\nGeral: ${if(BoostService.globalOk) "disponível" else "limitado pelo aparelho"} • Players: ${BoostService.players}\nEfeitos ativos: ${BoostService.effectsSummary}"
    }
    override fun onResume(){ super.onResume(); handler.post(tick) }
    override fun onPause(){ handler.removeCallbacks(tick); super.onPause() }
}
