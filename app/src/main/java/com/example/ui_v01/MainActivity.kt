package com.example.ui_v01

import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ui_v01.databinding.ActivityMainBinding
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.DoubleArray
import kotlin.Exception
import kotlin.Int
import kotlin.String
import kotlin.Unit
import kotlin.byteArrayOf
import kotlin.concurrent.fixedRateTimer
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {
    // system var
    lateinit var binding : ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "MainActivity"

    // Set IP/Port
    private val newip = "192.168.0.100" // leelab3 // 192.168.0.100 : ZUB , 192.168.0.11 : msi
    private val port = 23

    // communication var
    private lateinit var show_text: TextView
    private var sendflag = false
    private var recvflag = false
    private var jointflag = false
    private var upjointflag = false
    private var downjointflag = false
    private var pressedid = 0

    private var lsupflag = false
    private var lsdwnflag = false
    private var lsstpflag = false
    private var homeflag = false
    private var packageflag = false
    private var gosamplflag = false

    private var demomode = false

    var recvdata = ByteArray(32)

    // index val
    val idx_cur_joint = 21
    val idx_des_joint = 27
    val idx_joint_control = 10
    val idx_direction_stage = 97 //1일때 up , 0이면 down
    val idx_move_stage = 98 // 1일때 움직임.
    val idx_go_home = 16
    val idx_rb_pose = 87
    val idx_cartesian_control = 17
    val idx_joint_status = 33
    val idx_stop = 12

    val interval_btw_poses : Long = 3  // seconds,  time delay during robot motion
    val interval_go_signal : Long = 100  // milliseconds
    val sampling_time : Long = 10 // milliseconds

    val unit_from_ui_to_zub = 1000.0
    val curq = DoubleArray(6)

    var rawfiledata : String? = null

    val q_range = arrayOf(arrayOf(-1, 95),
        arrayOf(-1, 100),
        arrayOf(-1, 130),
        arrayOf(-90, 0),
        arrayOf(-25, 25),
        arrayOf(-30, 30))
    val step_q = 2//10
    var T0E : Array<Array<Double>> = arrayOf(arrayOf(1.0, 0.0, 0.0, 0.0),
        arrayOf(0.0, 1.0, 0.0, 0.0),
        arrayOf(0.0, 0.0, 1.0, 0.0),
        arrayOf(0.0, 0.0, 0.0, 1.0))

    // About socket
    private lateinit var mHandler: Handler
    private lateinit var socket: Socket
    private lateinit var outstream: DataOutputStream
    private lateinit var instream: DataInputStream

    // timer
    private lateinit var timetext: TextView
    private lateinit var time2text: TextView
    private var timestopflag = 0
    private var timecount = 0
    private var time2count = 0
    private var testflag = false

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1){
            if (resultCode == Activity.RESULT_OK) {
                val samplepose = data?.getStringExtra("samplepose")
                rawfiledata = samplepose
                Toast.makeText(applicationContext, "sample pose : $samplepose", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_item)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        ////////status text
        show_text = binding.textstatus

        ////////option
        binding.connectbt.setOnClickListener{
            connectt(0)
            binding.textstatus.text="connecting...."
        }
        binding.demo.setOnClickListener{
            gosamplflag = true
        }
        binding.file.setOnClickListener {
            Toast.makeText(applicationContext, "file open", Toast.LENGTH_SHORT).show()
            val myIntent : Intent = Intent(this, Fileexplr :: class.java) //testact
            startActivityForResult(myIntent,1)
        }


        ///////////linear stage
        lsctrlbtn(binding.lsup, true) //true up
        lsctrlbtn(binding.lsdown, false) // false down
        binding.packagebt.setOnClickListener {
            packageflag = true
        }

        ///////////coil posi
        binding.gobtn.setOnClickListener{
            show_text.text = "$rawfiledata"
        }
        binding.stopbtn.setOnClickListener{
        }
        binding.homepositionbtn.setOnClickListener{
            homeflag = true
        }

        ///////////position
        binding.xp.setOnClickListener{
            sendflag = true
            //sended_num += 1
            binding.textstatus.text="send...."
        }
        binding.yp.setOnClickListener{
            recvflag = true
            //sended_num -= 1
            binding.textstatus.text="recv...."
        }
        binding.zp.setOnClickListener {
            testflag = true
            binding.textstatus.text="testfunc"
        }

        binding.xm.setOnClickListener{
            sendflag = true
            //sended_num += 1
            binding.textstatus.text="send...."
        }
        binding.ym.setOnClickListener{
            recvflag = true
            //sended_num -= 1
            binding.textstatus.text="recv...."
        }
        binding.zm.setOnClickListener {
            testflag = true
            binding.textstatus.text="testfunc"
        }

        ///////////orientation
        binding.rxp.setOnClickListener{
            sendflag = true
            //sended_num += 1
            binding.textstatus.text="send...."
        }
        binding.ryp.setOnClickListener{
            recvflag = true
            //sended_num -= 1
            binding.textstatus.text="recv...."
        }
        binding.rzp.setOnClickListener {
            testflag = true
            binding.textstatus.text="testfunc"
        }

        binding.rxm.setOnClickListener{
            sendflag = true
            //sended_num += 1
            binding.textstatus.text="send...."
        }
        binding.rym.setOnClickListener{
            recvflag = true
            //sended_num -= 1
            binding.textstatus.text="recv...."
        }
        binding.rzm.setOnClickListener {
            testflag = true
            binding.textstatus.text="testfunc"
        }

        //seek bar slider (joint control)
        binding.seekBar1.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                binding.textbaseval.text="$progress" //문자 내부에 변수 처리
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {
                println("움직임 시작")
            }
            override fun onStopTrackingTouch(p0: SeekBar?) {
                println("움직임 끝")
            }
        })
        binding.seekBar2.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                binding.textsholderval.text="$p1" //문자 내부에 변수 처리 $
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {
                println("움직임 시작")
            }
            override fun onStopTrackingTouch(p0: SeekBar?) {
                println("움직임 끝")
            }
        })
        binding.seekBar3.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                binding.textdepthval.text="$p1" //문자 내부에 변수 처리 $
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {
                println("움직임 시작")
            }
            override fun onStopTrackingTouch(p0: SeekBar?) {
                println("움직임 끝")
            }
        })
        binding.seekBar4.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                binding.textwrist1val.text="$p1" //문자 내부에 변수 처리 $
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {
                println("움직임 시작")
            }
            override fun onStopTrackingTouch(p0: SeekBar?) {
                println("움직임 끝")
            }
        })
        binding.seekBar5.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                binding.textwrist2val.text="$p1" //문자 내부에 변수 처리 $
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {
                println("움직임 시작")
            }
            override fun onStopTrackingTouch(p0: SeekBar?) {
                println("움직임 끝")
            }
        })
        binding.seekBar6.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                binding.textwrist3val.text="$p1" //문자 내부에 변수 처리 $
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {
                println("움직임 시작")
            }
            override fun onStopTrackingTouch(p0: SeekBar?) {
                println("움직임 끝")
            }
        })

        //seek bar button (joint control +,- button)
        sbctrlbtn(binding.m1,binding.seekBar1,true, 0)
        sbctrlbtn(binding.m2,binding.seekBar2,true, 1)
        sbctrlbtn(binding.m3,binding.seekBar3,true, 2)
        sbctrlbtn(binding.m4,binding.seekBar4,true, 3)
        sbctrlbtn(binding.m5,binding.seekBar5,true, 4)
        sbctrlbtn(binding.m6,binding.seekBar6,true, 5)
        sbctrlbtn(binding.p1,binding.seekBar1,false, 0)
        sbctrlbtn(binding.p2,binding.seekBar2,false, 1)
        sbctrlbtn(binding.p3,binding.seekBar3,false, 2)
        sbctrlbtn(binding.p4,binding.seekBar4,false, 3)
        sbctrlbtn(binding.p5,binding.seekBar5,false, 4)
        sbctrlbtn(binding.p6,binding.seekBar6,false, 5)


    }

    @SuppressLint("ClickableViewAccessibility")
    fun sbctrlbtn(targetbutton: Button, targetseekBar: SeekBar, opt:Boolean, id : Int){
        Log.d(TAG, "sbctrlbtn on , id : $id, opt : $opt")
        targetbutton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { //눌렀을때
                    pressedid = id
                    Log.d(TAG, "prss btn : $pressedid")

                    if(opt) {
                        startDecre(targetseekBar)
                        Log.d(TAG, "start decre")
                        downjointflag = true
                        //up_joint(pressedid)
                    }
                    else {
                        startIncre(targetseekBar)
                        Log.d(TAG, "start incre")
                        upjointflag = true
                        //down_joint(pressedid)
                    }
                }
                MotionEvent.ACTION_UP -> { //뗄때
                    jointflag = false
                    Log.d(TAG, "joint flag $jointflag")
                    if(opt) targetseekBar.progress--
                    else targetseekBar.progress++
                    stopIncrementing()
                }
            }
            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun lsctrlbtn(targetbutton: Button, opt:Boolean){
        Log.d(TAG, "lsctrlbtn on , opt : $opt")

        targetbutton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { //눌렀을때
                    if (opt) {
                        lsupflag = true
                        lsdwnflag = false
                        lsstpflag = false
                        Log.d(TAG, "linear stage up / up : $lsupflag / down : $lsdwnflag / stop : $lsstpflag")
                    }
                    else {
                        lsupflag = false
                        lsdwnflag = true
                        lsstpflag = false
                        Log.d(TAG, "linear stage down / up : $lsupflag / down : $lsdwnflag / stop : $lsstpflag")
                    }
                }
                MotionEvent.ACTION_UP -> { //뗄때
                    lsupflag = false
                    lsdwnflag = false
                    lsstpflag = true
                    Log.d(TAG, "linear stage stop / up : $lsupflag / down : $lsdwnflag / stop : $lsstpflag")
                }
            }
            false
        }
    }

    private fun startIncre(targetseekBar: SeekBar) {
        handler.postDelayed(object : Runnable {
            override fun run() {
                targetseekBar.progress++
                handler.postDelayed(this, 50) // delay마다 변화
            }
        }, 1000) //delay 만큼 눌렀을때 실행
    }
    private fun startDecre(targetseekBar: SeekBar) {
        handler.postDelayed(object : Runnable {
            override fun run() {
                targetseekBar.progress--
                handler.postDelayed(this, 50) // delay마다 변화
            }
        }, 1000)
    }
    private fun stopIncrementing() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun connectt(id: Int) {
        mHandler = Handler(Looper.getMainLooper())
        show_text.text = "연결하는중"

        val checkUpdate = Thread {
            // Access server
            try {
                socket = Socket(newip, port)
                show_text.text = "서버 접속됨"
            }
            catch (e1: IOException) {
                show_text.text = "서버 접속 못함"
                e1.printStackTrace()
            }
            try {
                outstream = DataOutputStream(socket.getOutputStream())
                instream = DataInputStream(socket.getInputStream())
                //outstream.writeUTF("안드로이드에서 서버로 연결 요청")
            }
            catch (e: IOException) {
                e.printStackTrace()
                show_text.text = "버퍼 생성 잘못 됨"
            }
            show_text.text = "버퍼 생성 잘 됨"

            try {
                while (true) {

                    get_joint_value() //add timer

                    //////////////////////////////
                    if (upjointflag == true){
                        Log.d(TAG, "cnct jointflag true / prss id : $pressedid")
                        up_joint(pressedid)
                        upjointflag = false
                    }
                    if (downjointflag == true){
                        Log.d(TAG, "cnct jointflag true / prss id : $pressedid")
                        down_joint(pressedid)
                        downjointflag = false
                    }
//                    if (sendflag == true) {
//                        setvalue(subindex, value)
//                        showrecvdata()
//                    }
//                    if (recvflag == true){
//                        getvalue(subindex)
//                        showrecvdata()
//                    }
                    if (lsupflag == true){
                        up_stage()
                    }
                    if (lsdwnflag == true){
                        down_stage()
                    }
                    if (lsstpflag == true){
                        stop_stage()
                    }
                    if (gosamplflag == true){
                        Log.d(TAG, "go samplflag / flag : $gosamplflag")
                        load_file(rawfiledata)
                        go_sample_pose()
                    }
                    if (homeflag == true){
                        Log.d(TAG, "go home / flag : $homeflag")
                        go_home()
                        homeflag = false
                    }
                    if (packageflag == true){
                        Log.d(TAG, "go package / flag : $packageflag")
                        go_package()
                        packageflag = false
                    }
                }
            }
            catch (e: Exception) {
            }
        }
        checkUpdate.start()
    }

    @SuppressLint("SetTextI18n")
    private fun get_joint_value(){
        //Log.d(TAG, "get joint value func in.")
        for(i in 0..5) {
            getvalue(idx_cur_joint+i)//idx_cur_joint + i
            //showrecvdata()

            val rcv8 = recvdata[8]
            val rcv9 = recvdata[9]
            val rcv10 = recvdata[10]
            val rcv11 = recvdata[11]

            val curqfromzub = (-1 * (byteToInt(rcv11) shr 0x07) shl 31) +
                                (((byteToInt(rcv11) and 0x7F) shl 24) or
                                (byteToInt(rcv10) shl 16) or
                                (byteToInt(rcv9) shl 8) or
                                byteToInt(rcv8))

            curq[i] = (curqfromzub / unit_from_ui_to_zub)

        }

        val curq1 = curq[0]
        val curq2 = curq[1]
        val curq3 = curq[2]
        val curq4 = curq[3]
        val curq5 = curq[4]
        val curq6 = curq[5]

        binding.textbaseval.text = "$curq1"
        binding.textsholderval.text = "$curq2"
        binding.textdepthval.text = "$curq3"
        binding.textwrist1val.text = "$curq4"
        binding.textwrist2val.text = "$curq5"
        binding.textwrist3val.text = "$curq6"
        //Log.d(TAG, "read joint value done")

        val q = curq.plus(0.0)// + 0.0

        T0E = fktms(q)
        //Log.d(TAG, "fktms(q) done")
        calcT0E(T0E)
    }

    //kine
    private fun fktms(q: DoubleArray) : Array<Array<Double>> {
        //Log.d(TAG, "fktms func in.")
        val l7 = 118.0 + 42.0 + 11.0
        val radius = 455.09
        val alp = deg2rad(52.0) //0.9075
        val beta = deg2rad(60.0) //1.0472

        val dhpara : Array<Array<Double>> = arrayOf(arrayOf((PI/2), alp, beta, 0.0, (PI/2), -(PI/2), 0.0),
            arrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, l7),
            arrayOf(0.0, 0.0, radius-q[2], 0.0, 0.0, 0.0, 0.0),
            arrayOf(deg2rad(q[0]), deg2rad(q[1]), 0.0, deg2rad(q[3]), deg2rad(q[4]), deg2rad(q[5]), deg2rad(q[6])))

        var T_0_6 : Array<Array<Double>>  = Array(4){Array(4){0.0}}


        val dhparamscount = dhpara.size //4
        val number_dh_trafos = dhpara[0].size //7
        //show_text.text="$dhparamscount, $number_dh_trafos "

        if (dhparamscount !=4){
            Log.d(TAG, "wrong dh-para.")
        }

        var trafo_matrizes : Array<Array<Array<Double>>> = Array(7){Array(4){Array(4){0.0}}}

        //Log.d(TAG, "trafo calc in.")
        //Log.d(TAG, "trafo calc for-loop in. $number_dh_trafos loop")
        for(i in 0..number_dh_trafos-1){
            // dh_para[i, 3] = dh_para[i, 3] + np.pi/2
            //Log.d(TAG, "loop $i")

            val temp1 = dhpara[0][i]
            val temp2 = dhpara[1][i]
            val temp3 = dhpara[2][i]
            var temp4 = dhpara[3][i]

            //Log.d(TAG, "//temp1: $temp1 //temp2: $temp2 //temp3: $temp3 //temp4: $temp4") //dh받아오기 잘됨

            if(i == 0){
                //Log.d(TAG, "if i == 0 in.")
                temp4 = (temp4+(PI/2))
                //Log.d(TAG, "new temp4 $temp4")
            }
            //dh_para[i, 3] = dh_para[i, 3] - np.pi/2
            else if(i == 4){
                //Log.d(TAG, "else if i == 4 in.")
                temp4 = (temp4-(PI/2))
                //Log.d(TAG, "new temp4 $temp4")
            }
            //Log.d(TAG, "cond out.")
            val dhtemp = dh(temp1, temp2, temp3, temp4)
            //Log.d(TAG, "dhtemp $dhtemp")
            for(i in 0..3){
                for(j in 0..3){
                    //println("$i,$j :"+ dhtemp[i][j])
                }
            }
            trafo_matrizes[i] = dhtemp ///add arr ++ // dhtemp 비교하기
        }

        if (trafo_matrizes != null) {
            for(i in 0..trafo_matrizes.size-2){
                //Log.d(TAG, "trafo loop $i")
                if(i == 0){
                    T_0_6 = dotprod(trafo_matrizes[i], trafo_matrizes[i+1]) //matmult
                    //println(T_0_6.contentDeepToString())
                }
                else{
                    T_0_6 = dotprod(T_0_6,trafo_matrizes[i+1])
                    //println(T_0_6.contentDeepToString())
                }
            }
        }
        return T_0_6

    }

    private fun calcT0E(T0E : Array<Array<Double>>){
        //Log.d(TAG, "calcT0E in")
        var ry = atan2(sqrt((T0E[2][0]).pow(2) + (T0E[2][1]).pow(2)), (T0E[2][2]))
        var rz1 : Double
        var rz2 : Double
        if (isclose(ry, 0.0, deg2rad(1.0))) {
            ry = 0.0
            rz1 = 0.0
            rz2 = atan2(-(T0E[0][1]), (T0E[0][0]))
        }
        else if (isclose(ry, PI, atol=deg2rad(1.0))) {
            ry = PI
            rz1 = 0.0
            rz2 = atan2((T0E[0][1]), -(T0E[0][0]))
        }
        else {
            rz1 = atan2((T0E[1][2]) / sin(ry), (T0E[0][2]) / sin(ry))
            rz2 = atan2((T0E[2][1]) / sin(ry), -(T0E[2][0]) / sin(ry))
            if (rz1 < 0) rz1 = rz1 + PI * 2
            if (rz2 < 0) rz2 = rz2 + PI * 2
        }


        binding.textxval.text = "%.3f".format(T0E[0][3]) //ok
        binding.textyval.text = "%.3f".format(T0E[1][3]) //ok
        binding.textzval.text = "%.3f".format(T0E[2][3]) //ok
        binding.textrxval.text = "%.3f".format(rad2deg(rz1))
        binding.textryval.text = "%.3f".format(rad2deg(ry))
        binding.textrzval.text = "%.3f".format(rad2deg(rz2))
    }

    private fun dh(alpha : Double, a : Double, d : Double, q : Double)  : Array<Array<Double>> {
        val Tx : Array<Array<Double>> = arrayOf(arrayOf(1.0,        0.0,         0.0, a),
            arrayOf(0.0, cos(alpha), -sin(alpha), 0.0),
            arrayOf(0.0, sin(alpha),  cos(alpha), 0.0),
            arrayOf(0.0,        0.0,         0.0, 1.0))
        val Tz : Array<Array<Double>> = arrayOf(arrayOf(cos(q), -sin(q), 0.0, 0.0),
            arrayOf(sin(q),  cos(q), 0.0, 0.0),
            arrayOf(0.0,        0.0, 1.0, d),
            arrayOf(0.0,        0.0, 0.0, 1.0))

        val result = Array(4) { Array(4) { 0.0 } }

        for(i in 0..3) {
            for (j in 0..3) {
                var temp = 0.0
                for (k in 0..3) {
                    temp += Tx[i][k] * Tz[k][j]
                    //Log.d(TAG, "dot prod Loop i:$i,j:$j,k:$k, temp: $temp")
                }
                result[i][j] = temp
            }
        }

        return result
    }


    private fun set_q_value(des_q : DoubleArray){
        Log.d(TAG, "set q val")
        for(i in des_q.indices) {
            //Log.d(TAG, "set q val loop : $i")
            setvalue(idx_des_joint + i, (des_q[i] * unit_from_ui_to_zub).toInt())
        }
        val value = 1
        setvalue(idx_joint_control,value)
        Log.d(TAG, "value:$value")
        //show_text.text="MoveJ(Subindex: $idx_joint_control , Value: $value )"
    }

    private fun up_joint(i: Int){
        Log.d(TAG, "upjoint/ i:$i")
        val des_q = curq

        val qrantemp = q_range[i][1]
        Log.d(TAG, "qrange : $qrantemp")

        des_q[i]=curq[i] + step_q
        if (des_q[i] > q_range[i][1]){
            des_q[i] = (q_range[i][1] - 1).toDouble()
        } //tolerance: 1
        val temp = i+1
        val showdesqi = des_q[i]
        show_text.text = "$temp th joint value(desired): $showdesqi"
        set_q_value(des_q)
    }

    private fun down_joint(i: Int){
        Log.d(TAG, "downjoint/ i:$i")
        val des_q = curq

        val qrantemp = q_range[i][0]
        Log.d(TAG, "qrange : $qrantemp")

        des_q[i]=curq[i] - step_q
        if (des_q[i] < q_range[i][0]){
            des_q[i] = (q_range[i][0] - 1).toDouble()
        } //tolerance: 1
        val temp = i+1
        val showdesqi = des_q[i]
        show_text.text = "$temp th joint value(desired): $showdesqi"
        set_q_value(des_q)
    }

    private fun go_home(){
        val value = 1
        Log.d(TAG, "index : $idx_go_home / value : $value")
        setvalue(idx_go_home, value)
        //'Go home(Subindex:' + str(self.idx_go_home) + ', Value:' + str(value) + ')'
    }
    private fun go_package(){
        val value = 2
        Log.d(TAG, "index : $idx_go_home / value : $value")
        setvalue(idx_go_home, value)
        //'Go home(Subindex:' + str(self.idx_go_home) + ', Value:' + str(value) + ')'
    }

    private fun up_stage() {
        val value = 1
        setvalue(idx_direction_stage, value)
        setvalue(idx_move_stage, 1)

        //#status_msg = 'Go home(Subindex:' + str(self.idx_go_home) + ', Value:' + str(value) + ')'
        //#self.statusBar().showMessage(status_msg)
    }

    private fun down_stage(){
        val value = 0
        setvalue(idx_direction_stage, value)
        setvalue(idx_move_stage, 1)

        //status_msg = 'Go home(Subindex:' + str(self.idx_go_home) + ', Value:' + str(value) + ')'
    }

    private fun stop_stage(){
        val value = 0
        setvalue(idx_move_stage, value)
        //status_msg = 'Go home(Subindex:' + str(self.idx_go_home) + ', Value:' + str(value) + ')'
    }

    private fun go_sample_pose(){
        demomode = true
        fixedRateTimer(period = interval_go_signal, initialDelay = 0){
            go_target()
        }
    }
    private fun go_target() {
        //setvalue(idx_rb_pose,value)
        if(demomode == true) {
        //var robot_pose =
        }
    }

    private fun setvalue(subindex : Int, value : Int){
        Log.d(TAG, "setvalue in/ sbidx : $subindex / val : $value")
        val index = 0x2201
        val msg = byteArrayOf(0x05.toByte(), 0x0B.toByte(), 0x00, 23,
            0x22,(index and 0xFF).toByte(), ((index shr 8) and 0xFF).toByte(), subindex.toByte(),
            (value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte(),((value shr 16) and 0xFF).toByte(), ((value shr 24) and 0xFF).toByte(), 0x03)
        outstream.write(msg)
        instream.read(recvdata,0,13)
        sendflag = false
    }

    private fun getvalue(subindex : Int) : Unit{
        val index = 0x2201
        val msg1 = byteArrayOf(0x05.toByte(), 0x0B.toByte(), 0x00, 23,
            0x40,(index and 0xFF).toByte(), ((index shr 8) and 0xFF).toByte(), subindex.toByte(),
            0x00, 0x00,0x00, 0x00, 0x03)
        outstream.write(msg1)
        instream.read(recvdata,0,13)
        recvflag = false
    }


    private fun load_file(rawfiledata : String?){
        //string 6개씩 분할 (공백 / 엔터) -> n*6 double arr에 할당
        val temp = rawfiledata?.split("\\n")
        val cmdlength = temp?.size

        var temptemp: Array<Array<String>>? =
            cmdlength?.let { Array(it){Array(6){""}} }
        for(i in 0..cmdlength!!) {
            temptemp?.set(i, temp[i].split("\\s").toTypedArray())
        }
        Log.d(TAG, "load file split : \n$temptemp")
    }

    ////util
    private fun matmltply(a :Array<Array<Double>>, b : Array<Array<Double>>) : Array<Array<Double>> {
        var result: Array<Array<Double>> =arrayOf(arrayOf(0.0,0.0,0.0,0.0),
            arrayOf(0.0,0.0,0.0,0.0),
            arrayOf(0.0,0.0,0.0,0.0),
            arrayOf(0.0,0.0,0.0,0.0))

        return result
    }
    private fun matmltply2(a :Array<Array<Double>>, b : Array<Double>) : Array<Array<Double>> {
        var result: Array<Array<Double>> =arrayOf(arrayOf(0.0,0.0,0.0,0.0),
            arrayOf(0.0,0.0,0.0,0.0),
            arrayOf(0.0,0.0,0.0,0.0),
            arrayOf(0.0,0.0,0.0,0.0))

        return result
    }

    private fun dotprod(a : Array<Array<Double>>, b : Array<Array<Double>>): Array<Array<Double>> {
        val result = Array(4) { Array(4) { 0.0 } }

        for(i in 0..3) {
            for (j in 0..3) {
                var temp = 0.0
                for (k in 0..3) {
                    temp += a[i][k] * b[k][j]
                    //Log.d(TAG, "dot prod Loop i:$i,j:$j,k:$k, temp: $temp")
                }
                result[i][j] = temp
            }
        }

        return result
    }

    private fun isclose(a : Double, b : Double, atol : Double =0.00000001, rtol : Double = 0.00001) : Boolean{
        return (a - b).absoluteValue <= (atol + rtol * b.absoluteValue)
    }

    private fun deg2rad(deg: Double): Double {
        return deg * (PI / 180)
    }

    private fun rad2deg(rad: Double): Double {
        return rad * (180 / PI)
    }

    private fun byteToInt(a: Byte): Int{
        var temp = a.toInt()
        if (temp < 0) {
            temp = temp + 256
        }
        return temp
    }

    private fun byteArrayToHex(a: ByteArray): String? {
        val sb = StringBuilder()
        for (b in a) sb.append(String.format("%02x ", b.toInt() and 0xff))
        return sb.toString()
    }

    @SuppressLint("SetTextI18n")
    private fun showrecvdata(){
        val rawdt = byteArrayToHex(recvdata)

        show_text.text=rawdt
    }

    private fun timertest(){
        fixedRateTimer(period = 1000, initialDelay = 100){
//            time2count += 1
//            timecount += time2count/10
//            time2count = time2count%10
//
//            timetext.text = "$timecount"
//            time2text.text = "$time2count"
//
//            if (timestopflag==1)
//                cancel()
        }
        timestopflag = 0
    }
}


