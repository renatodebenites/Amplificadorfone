package br.app.amplificador

import android.app.*
import android.content.*
import android.media.AudioManager
import android.media.audiofx.*
import android.os.Build
import android.os.IBinder
import kotlin.math.roundToInt

class BoostService : Service() {
    companion object {
        const val EXTRA_REFRESH="refresh"; const val ACTION_STOP="br.app.amplificador.STOP"; const val ACTION_UP="br.app.amplificador.UP"; const val ACTION_DOWN="br.app.amplificador.DOWN"
        private const val CHANNEL="boost"; private const val NOTIF_ID=1
        @Volatile var running=false; @Volatile var globalOk=false; @Volatile var players=0; @Volatile var effectsSummary="Amplificação"
    }
    private data class Fx(val loud:LoudnessEnhancer, val bass:BassBoost?, val virt:Virtualizer?, val eq:Equalizer?)
    private val lock=Any(); private val effects=HashMap<Int,Fx>(); private var started=false
    private val receiver=object:BroadcastReceiver(){ override fun onReceive(c:Context,i:Intent){ val s=i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,AudioEffect.ERROR_BAD_VALUE); if(s==AudioEffect.ERROR_BAD_VALUE||s==0)return; if(i.action==AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)attach(s) else detach(s) } }
    override fun onCreate(){ super.onCreate(); running=true; createChannel(); startForeground(NOTIF_ID,notification()); val f=IntentFilter().apply{addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)}; if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_EXPORTED) else registerReceiver(receiver,f) }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        when(intent?.action){ ACTION_STOP->{stopSelf();return START_NOT_STICKY}; ACTION_UP->{adjust(1)}; ACTION_DOWN->{adjust(-1)} }
        applyPrefs(); if(!started){started=true;attach(0)}; updateNotification(); return START_STICKY
    }
    private fun adjust(delta:Int){ val p=getSharedPreferences("amp",MODE_PRIVATE); val v=(p.getInt("db",6)+delta).coerceIn(0,15); p.edit().putInt("db",v).apply() }
    private fun applyPrefs(){ val p=getSharedPreferences("amp",MODE_PRIVATE); val am=getSystemService(AudioManager::class.java); val max=am.getStreamMaxVolume(AudioManager.STREAM_MUSIC); am.setStreamVolume(AudioManager.STREAM_MUSIC,(max*p.getInt("volume",80)/100f).roundToInt(),0); synchronized(lock){ effects.forEach{(_,fx)->configure(fx,p)} } }
    private fun configure(fx:Fx,p:SharedPreferences){ try{fx.loud.setTargetGain((p.getInt("db",6)*100).coerceIn(0,1500))}catch(_:Exception){}; try{fx.bass?.setStrength((p.getInt("bass",0)*10).coerceIn(0,1000).toShort());fx.bass?.enabled=p.getInt("bass",0)>0}catch(_:Exception){}; try{fx.virt?.setStrength((p.getInt("virtual",0)*10).coerceIn(0,1000).toShort());fx.virt?.enabled=p.getInt("virtual",0)>0}catch(_:Exception){}; try{fx.eq?.let{ e-> val n=e.numberOfBands.toInt(); for(i in 0 until n){ val ui=p.getInt("eq${i.coerceAtMost(4)}",50); val r=e.bandLevelRange; val level=(r[0]+(r[1]-r[0])*ui/100).toShort(); e.setBandLevel(i.toShort(),level); e.enabled=true } }}catch(_:Exception){} }
    private fun attach(session:Int){ synchronized(lock){if(effects.containsKey(session))return; try{ val loud=LoudnessEnhancer(session); val fx=Fx(loud,try{BassBoost(0,session)}catch(_:Exception){null},try{Virtualizer(0,session)}catch(_:Exception){null},try{Equalizer(0,session)}catch(_:Exception){null}); configure(fx,getSharedPreferences("amp",MODE_PRIVATE)); val r=loud.setEnabled(true); effects[session]=fx; if(session==0)globalOk=r==AudioEffect.SUCCESS }catch(_:Exception){if(session==0)globalOk=false}; players=effects.keys.count{it!=0}; effectsSummary="Amplificação + ${if(effects.values.any{it.bass!=null})"Bass" else "Bass indisponível"} + ${if(effects.values.any{it.eq!=null})"EQ" else "EQ indisponível"}" } }
    private fun detach(s:Int){ synchronized(lock){effects.remove(s)?.let{release(it)};players=effects.keys.count{it!=0}} }
    private fun release(f:Fx){ listOf(f.loud,f.bass,f.virt,f.eq).forEach{try{it?.release()}catch(_:Exception){}} }
    override fun onDestroy(){running=false;try{unregisterReceiver(receiver)}catch(_:Exception){};synchronized(lock){effects.values.forEach{release(it)};effects.clear()};globalOk=false;players=0;super.onDestroy()}
    override fun onBind(i:Intent?):IBinder?=null
    private fun createChannel(){getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Amplificador",NotificationManager.IMPORTANCE_LOW))}
    private fun pending(req:Int,action:String)=PendingIntent.getService(this,req,Intent(this,BoostService::class.java).setAction(action),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun notification():Notification{ val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT); val db=getSharedPreferences("amp",MODE_PRIVATE).getInt("db",6); return Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).setContentTitle("Amplificador ativo (+${db} dB)").setContentText("Use − / + para ajustar rapidamente").setContentIntent(open).addAction(android.R.drawable.ic_media_previous,"−",pending(2,ACTION_DOWN)).addAction(android.R.drawable.ic_media_next,"+",pending(3,ACTION_UP)).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Desligar",pending(1,ACTION_STOP)).setOngoing(true).build() }
    private fun updateNotification(){ getSystemService(NotificationManager::class.java).notify(NOTIF_ID,notification()) }
}
