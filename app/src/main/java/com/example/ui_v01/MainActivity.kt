package com.example.ui_v01

import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
import kotlin.Int
import kotlin.String
import kotlin.Unit
import kotlin.byteArrayOf
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.thread
import kotlin.concurrent.timer
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
    private var communicationThread: Thread? = null

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
    private var stopbtnflag = false

    private var connectionflag = false

    private var poseflag = false
    private var upposeflag = false
    private var downposeflag = false

    private var demorst = false

    private var demomode = true

    private var bufferSize = 1000
    var recvdata = ByteArray(bufferSize)

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

    // calc param
    val interval_btw_poses : Long = 3  // seconds,  time delay during robot motion
    val interval_go_signal : Long = 1000  // milliseconds
    val sampling_time : Long = 10 // milliseconds

    val unit_from_ui_to_zub = 1000.0
    var curq = DoubleArray(6)
    var des_pose = DoubleArray(6)
    var q_status = IntArray(6)

    lateinit var robot_poses : Array<Array<Double>> //listOf<Int>()
    var n_pose = 0
    var total_poses = 0
    var rawfiledata : String? = null

    val q_range = arrayOf(arrayOf(-1, 95),
                        arrayOf(-1, 100),
                        arrayOf(-1, 130),
                        arrayOf(-90, 0),
                        arrayOf(-25, 25),
                        arrayOf(-30, 30))

    var step_q = 5//10
    val step_translation = 30 // mm
    val step_rotation = 5 // degree

    var T0E : Array<Array<Double>> = arrayOf(arrayOf(1.0, 0.0, 0.0, 0.0),
                                            arrayOf(0.0, 1.0, 0.0, 0.0),
                                            arrayOf(0.0, 0.0, 1.0, 0.0),
                                            arrayOf(0.0, 0.0, 0.0, 1.0))

    // About socket
    private lateinit var mHandler: Handler
    private lateinit var socket: Socket
    private lateinit var outstream: DataOutputStream
    private lateinit var instream: DataInputStream

    // File reader
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1){
            if (resultCode == Activity.RESULT_OK) {
                rawfiledata = null
                val samplepose = data?.getStringExtra("samplepose")
                rawfiledata = samplepose
//                Toast.makeText(applicationContext, "sample pose : \n $samplepose", Toast.LENGTH_LONG).show()
//                Toast.makeText(applicationContext, "File loading OK!", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Main Act
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_item)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        ////////status text
        show_text = binding.textstatus

        val mainthrid = android.os.Process.myTid()
        Log.d(TAG, "[Main Thread : $mainthrid]")

        ////////
        binding.connectbt.setOnClickListener{
//            connectionflag = !connectionflag
            onToggleConnectButtonClicked()
        }


        binding.demo.setOnClickListener{
            if(rawfiledata==null) {
                Toast.makeText(applicationContext, "No sample pose..", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            else gosamplflag = true
            //load_file(rawfiledata)
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
            demomode = true
        }


//        binding.stopbtn.setOnClickListener{
//            stopbtnflag = true
//        }
        var isToggled = false
        binding.stopbtn.setOnClickListener {
            // Toggle the state
            isToggled = !isToggled
            // Handle the toggle state
            if (isToggled) {
                stopbtnflag = true
                show_text.text="--STOP--"
                binding.demorstbtn.isEnabled = true
            } else {
                stopbtnflag = false
                show_text.text="--READY--"
                binding.demorstbtn.isEnabled = false
            }
        }

        binding.demorstbtn.setOnClickListener {
            if(isToggled) {
                show_text.text = "--Demo Reset & Home Pos--"
                rawfiledata = null
                binding.demorstbtn.isEnabled = false
                binding.stopbtn.isChecked=false
                isToggled = !isToggled
                demorst = true
                stopbtnflag = false

                homeflag = true
            }
        }

        binding.homepositionbtn.setOnClickListener{
            homeflag = true
        }

        //seek bar button (joint control +,- button)
        sbctrlbtn(binding.m1,true, 0)
        sbctrlbtn(binding.m2,true, 1)
        sbctrlbtn(binding.m3,true, 2)
        sbctrlbtn(binding.m4,true, 3)
        sbctrlbtn(binding.m5,true, 4)
        sbctrlbtn(binding.m6,true, 5)
        sbctrlbtn(binding.p1,false, 0)
        sbctrlbtn(binding.p2,false, 1)
        sbctrlbtn(binding.p3,false, 2)
        sbctrlbtn(binding.p4,false, 3)
        sbctrlbtn(binding.p5,false, 4)
        sbctrlbtn(binding.p6,false, 5)

        //pose control btn
//        posectrlbtn(binding.xm,true, 0)
//        posectrlbtn(binding.ym,true, 1)
//        posectrlbtn(binding.zm,true, 2)
//        posectrlbtn(binding.rxm,true, 3)
//        posectrlbtn(binding.rym,true, 4)
//        posectrlbtn(binding.rzm,true, 5)
//        posectrlbtn(binding.xp,false, 0)
//        posectrlbtn(binding.yp,false, 1)
//        posectrlbtn(binding.zp,false, 2)
//        posectrlbtn(binding.rxp,false, 3)
//        posectrlbtn(binding.ryp,false, 4)
//        posectrlbtn(binding.rzp,false, 5)

        binding.speedcontorl.isEnabled = false
        binding.speedcontorl.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                show_text.text="Joint Control Speed ${binding.speedcontorl.progress}"
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {
                println("움직임 시작")
            }
            override fun onStopTrackingTouch(p0: SeekBar?) {
                println("움직임 끝")
                step_q=binding.speedcontorl.progress
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    fun posectrlbtn(targetbutton: Button, opt:Boolean, id : Int){
        Log.d(TAG, "posectrlbtn on , id : $id, opt : $opt")
        targetbutton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { //눌렀을때
                    pressedid = id
                    Log.d(TAG, "prss btn : $pressedid")

                    if(opt) {
                        downposeflag = true
                        Log.d(TAG, "downpose flag $downposeflag")
                        //up_joint(pressedid)
                    }
                    else {
                        upposeflag = true
                        Log.d(TAG, "uppose flag $upposeflag")
                        //down_joint(pressedid)
                    }
                }
                MotionEvent.ACTION_UP -> { //뗄때
                    //poseflag = false
                    downposeflag = false
                    upposeflag = false
                    Log.d(TAG, "Pose flag :: Dwn: $downposeflag / Up: $upposeflag")
                }
            }
            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun sbctrlbtn(targetbutton: Button, opt:Boolean, id : Int){
        Log.d(TAG, "Joint Control Mode / joint: $id / opt : $opt")
        targetbutton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { //눌렀을때
                    pressedid = id
                    Log.d(TAG, "prss btn : $pressedid")

                    if(opt) {
                        downjointflag = true
                        Log.d(TAG, "Down Flag : $downjointflag")
                    }
                    else {
                        upjointflag = true
                        Log.d(TAG, "Up Flag : $upjointflag")
                    }
                }

                MotionEvent.ACTION_UP -> { //뗄때
                    jointflag = false
                    downjointflag = false
                    upjointflag = false
                    Log.d(TAG, "Flag :: dwn :$downjointflag / up :$upjointflag")
                    //if(opt) targetseekBar.progress--
                    //else targetseekBar.progress++
                    //stopIncrementing()
                }
            }
            false
        }
        targetbutton.setOnLongClickListener {
            Log.d(TAG, "jointbtn long press")
            false // 직후 click event 를 받기 위해 false 반환
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
                        show_text.text="Linear Stage up"
                        Log.d(TAG, "linear stage up / up : $lsupflag / down : $lsdwnflag / stop : $lsstpflag")
                    }
                    else {
                        lsupflag = false
                        lsdwnflag = true
                        lsstpflag = false
                        show_text.text="Linear Stage Down"
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

var isrun = false
    private fun onToggleConnectButtonClicked() {
        mHandler = Handler(Looper.getMainLooper())
        isrun = false
        var TID : Int = 0
        //if(connectionflag) requestDisconnection()
//        else requestConnection()

        show_text.text = "연결하는중"


        if(TID != 0){
            requestDisconnection()
            TID = 0
        }

        communicationThread = Thread {
            Log.d(TAG, "[ Thread ID : ${android.os.Process.myTid()}]")
            TID = android.os.Process.myTid()

//             Access server
            if(connectionflag){ //이미 연결되어 있을때
                connectionflag = false
                Log.d(TAG,"in thread connection check..")

                show_text.text = "Diconnected..!"

                runOnUiThread{btnablelist(false)}
                runOnUiThread{binding.connectbt.isChecked=false}
                communicationThread!!.interrupt()
                communicationThread = null
                TID=0
                //socket.close()
                return@Thread
            }

            try {
                socket = Socket(newip, port)
                resetval()

                show_text.text = "서버 접속됨"
            }
            catch (e1: IOException) {
                show_text.text = "서버 접속 못함"

                catchoutConnection()
            }

            try {
                outstream = DataOutputStream(socket.getOutputStream())
                instream = DataInputStream(socket.getInputStream())

                resetval()
                showjoint()
                //outstream.writeUTF("안드로이드에서 서버로 연결 요청")

                show_text.text = "Connected..!"
            }
            catch (e: IOException) {
                show_text.text = "버퍼 생성 잘못 됨"

                catchoutConnection()

                socket.close()
            }
            try {
                runOnUiThread{btnablelist(true)}
                connectionflag = true

                if(isrun) {
                    Log.d(TAG, "Is run")
                    communicationThread!!.interrupt()
                }
                isrun =!isrun

                while (!communicationThread!!.isInterrupted()) {
                    Thread.sleep(100)
                    Log.d(TAG, "[ Run Thread : ${android.os.Process.myTid()}]")
                    if(TID != (android.os.Process.myTid()) ) {
                        Log.d(TAG, "TID mismatched!!")
                        break
                    }
                    if(!connectionflag) break  // *******imprt

                    get_joint_value() //add timer
                    //////////////////////////////

                    if (stopbtnflag){
                        setvalue(idx_stop,1)
//                        show_text.text="--STOP--"
                        while(stopbtnflag){
                            Thread.sleep(300)
                            Log.d(TAG, "Stop button activated!! $stopbtnflag")
                        }
                        Log.d(TAG, "ready!! stpflg : $stopbtnflag")
                    }

                    //////////////////////////////
                    if (upjointflag){
                        Log.d(TAG, "jointflag true / prss id : $pressedid")
                        up_joint(pressedid)
                        //upjointflag = false
                    }
                    if (downjointflag){
                        Log.d(TAG, "jointflag true / prss id : $pressedid")
                        down_joint(pressedid)
                        //downjointflag = false
                    }

                    if (gosamplflag){
                        Log.d(TAG, "go samplflag / flag : $gosamplflag")
                        //show_text.text = "$rawfiledata"
                        load_file(rawfiledata)
                        //go_sample_pose()
                        show_text.text="Demo pos"
                        gosamplflag = false
                    }

//                    if(rawfiledata != null){
//                        if (upposeflag == true){
//                            Log.d(TAG, "poseflag true / prss id : $pressedid")
//                            up_pose(pressedid)
//                            upposeflag = false
//                        }
//                        if (downposeflag){
//                            Log.d(TAG, "poseflag true / prss id : $pressedid")
//                            //down_pose(pressedid)
//                            downposeflag = false
//                        }
//                    }
//                    else if(upposeflag || downposeflag){
//                        show_text.text = "no sample pose data"
//                        upposeflag = false
//                        downposeflag = false
//                    }
//
                    if (lsupflag){
                        up_stage()
                    }

                    if (lsdwnflag){
                        down_stage()
                    }

                    if (lsstpflag){
                        stop_stage()
                    }

                    if (homeflag){
                        Log.d(TAG, "go home / flag : $homeflag")
                        go_home()
                        homeflag = false
                        show_text.text="Home pos"
                    }

                    if (packageflag){
                        Log.d(TAG, "go package / flag : $packageflag")
                        go_package()
                        packageflag = false
                        show_text.text="Package pos"
                    }
                }
            }
            catch (e: NullPointerException ) {
                show_text.text="connection error!"

                catchoutConnection()

                socket.close()
            }
            catch (e: IOException){
                show_text.text="connection error!"

                catchoutConnection()

                socket.close()
            }
        }

        communicationThread!!.start()
    }

    private fun requestDisconnection()
    {

        Log.d(TAG, "requestDisconnection....")
        show_text.text = "Diconnected..!"


        runOnUiThread{btnablelist(false)}
        runOnUiThread{binding.connectbt.isChecked=false}

        connectionflag = false
        communicationThread!!.interrupt()
        communicationThread = null
        return

    }

    private fun requestConnection()
    {
        if(communicationThread!!.isAlive) return
    }

    private fun catchoutConnection(){

        Log.d(TAG, "catchoutConnection....")
        resetval()
        showjoint()

        runOnUiThread{btnablelist(false)}
        runOnUiThread{binding.connectbt.isChecked=false}
        connectionflag = false
    }

    @SuppressLint("SetTextI18n")
    private fun get_joint_value(){
        //Log.d(TAG, "get joint value func in.")

        for(i in 0..5) {
            getvalue(idx_cur_joint+i)//idx_cur_joint + i

            //Log.d(TAG, "curq val ${curq.contentToString()}")
            //Log.d(TAG, "recvdata ${recvdata.contentToString()}")
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

        showjoint()
        //Log.d(TAG, "read joint value done")

        val q = curq.plus(0.0)// + 0.0

        T0E = fktms(q)
        //Log.d(TAG, "fktms(q) done")
        calcT0E(T0E)
    }

    private fun resetval(){
        //Log.d(TAG, "resetval joint ")
        curq = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        recvdata =  ByteArray(bufferSize){0}
        //Log.d(TAG, "rst curq ${curq.contentToString()}")
        //Log.d(TAG, "recvdata ${recvdata.contentToString()}")
        showjoint()
    }
    private fun showjoint(){
        binding.textbaseval.text = "${curq[0]}"
        binding.textsholderval.text = "${curq[1]}"
        binding.textdepthval.text = "${curq[2]}"
        binding.textwrist1val.text = "${curq[3]}"
        binding.textwrist2val.text = "${curq[4]}"
        binding.textwrist3val.text = "${curq[5]}"
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
            val desq_ele_val = des_q[i]
            Log.d(TAG, "des q$i val  : $desq_ele_val")
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
        show_text.text = "Joint Control :: $temp th joint +"
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
        show_text.text = "Joint Control :: $temp th joint -"
        set_q_value(des_q)
    }

    private fun up_pose(i: Int){
        Log.d(TAG, "uppose func in / id : $i ")
        var ry = atan2(sqrt((T0E[2][0]).pow(2) + (T0E[2][1]).pow(2)), (T0E[2][2]))
        var rz1 : Double
        var rz2 : Double

        if (isclose(ry, 0.0, deg2rad(1.0))) {
            Log.d(TAG, "uppose func 1st if in")
            ry = 0.0
            rz1 = 0.0
            rz2 = atan2(-(T0E[0][1]), (T0E[0][0]))
        } else if (isclose(ry, PI, atol = deg2rad(1.0))) {
            Log.d(TAG, "uppose func 1st elif in")
            ry = PI
            rz1 = 0.0
            rz2 = atan2((T0E[0][1]), -(T0E[0][0]))
        } else {
            Log.d(TAG, "uppose func 1st else in")
            rz1 = atan2((T0E[1][2]) / sin(ry), (T0E[0][2]) / sin(ry))
            rz2 = atan2((T0E[2][1]) / sin(ry), -(T0E[2][0]) / sin(ry))
            if (rz1 < 0) rz1 = rz1 + PI * 2
            if (rz2 < 0) rz2 = rz2 + PI * 2
        }


//
//  #     self.des_pose = list(map(float, [self.T0E[0, 3], self.T0E[1, 3], self.T0E[2, 3], np.rad2deg(rz2), np.rad2deg(ry), np.rad2deg(rz1)]))
        des_pose = listOf(T0E[0][3], T0E[1][3], T0E[2][3], rad2deg(rz2), rad2deg(ry), rad2deg(rz1)).toDoubleArray()

        for (l in des_pose.indices) {
            val temp = des_pose[l]
            Log.d(TAG, "before des_pose[$l] : $temp")
        }

        if (i < 3) {
            Log.d(TAG, "uppose func 2nd if in")
            des_pose[i] = des_pose[i] + step_translation
        } else {
            Log.d(TAG, "uppose func 2nd else in")
            des_pose[i] = des_pose[i] + step_rotation
        }

        for (k in des_pose.indices) {
            Log.d(TAG, "uppose func for loop in")
            des_pose[k] = des_pose[k] * unit_from_ui_to_zub
        }

        for (l in des_pose.indices) {
            val temp = des_pose[l]
            Log.d(TAG, "after trans/rot/mult unit/ des_pose[$l] : $temp")
        }
        //demomode = false
        go_target()
//        np_des_pose = np.array(self.des_pose) * unit_from_ui_to_zub
//        # # print(self.des_pose)

//        self.des_pose = list(map(int, np_des_pose.tolist()))
//        print(self.des_pose)
//        self.is_demo_mode = False
//        self.go_target()

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


    private fun go_target() {
        Log.d(TAG, "gotarget func in")
        Log.d(TAG, "total poses $total_poses")
        val comparearr = IntArray(6){1}
        var np_robot_pose = DoubleArray(6)
        var robot_pose = DoubleArray(robot_poses.size)

        if(demomode == true){
            Log.d(TAG, "gotarget func 1st if in")
            robot_pose = robot_poses[n_pose].toDoubleArray()
            for (i in robot_pose.indices){
                Log.d(TAG, "gotarget func 1st if for loop $i")
               robot_pose[i] = robot_pose[i]*unit_from_ui_to_zub
                val temp = robot_pose[i]
                Log.d(TAG, "robot pose $i : temp")
            }
        }
        else{
            Log.d(TAG, "gotarget func 1st else in")
            robot_pose = des_pose
        }

        for(i in robot_pose.indices) {
            Log.d(TAG, "gotarget func 1st for loop $i")
            setvalue(idx_rb_pose+i, robot_pose[i].toInt())
        }

        if(demomode == true){
            Log.d(TAG, "gotarget func 2nd if in")
            if(true) {//q_status.contentEquals(comparearr)
                Log.d(TAG, "gotarget func 2-1 if in")
                setvalue(idx_cartesian_control, 1)
                n_pose = n_pose + 1

                if (n_pose == total_poses) {
                    Log.d(TAG, "gotarget func 2-1-1 if in")
                    n_pose = 0
                }
            }
        }
        else{
            Log.d(TAG, "gotarget func 2nd else in")
            setvalue(idx_cartesian_control,1)
        }
    }

    private fun setvalue(subindex : Int, value : Int){
        Log.d(TAG, "setvalue in/ sbidx : $subindex / val : $value")
        val index = 0x2201
        val msg = byteArrayOf(0x05.toByte(), 0x0B.toByte(), 0x00, 23,
            0x22,(index and 0xFF).toByte(), ((index shr 8) and 0xFF).toByte(), subindex.toByte(),
            (value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte(),((value shr 16) and 0xFF).toByte(), ((value shr 24) and 0xFF).toByte(), 0x03)
        outstream.write(msg)
        instream.read(recvdata,0,bufferSize)
        sendflag = false
    }
    private fun getvalue(subindex : Int) : Unit{
        // outstream flush
        outstream.flush()
        // instream flush
        var numBytes = instream.available()
        if (numBytes > 0)
        {
             instream.read(recvdata, 0, numBytes)
        }

        //Log.d(TAG, "getvalue in/ sbidx : $subindex")
        val index = 0x2201
        val msg1 = byteArrayOf(0x05.toByte(), 0x0B.toByte(), 0x00, 23,
            0x40,(index and 0xFF).toByte(), ((index shr 8) and 0xFF).toByte(), subindex.toByte(),
            0x00, 0x00,0x00, 0x00, 0x03)
        outstream.write(msg1)
        while (instream.available() >= 13);
        instream.read(recvdata,0,bufferSize)

//        Log.d(TAG, "[${android.os.Process.myTid()}]recvdata ${recvdata.contentToString()} // getvalue end")
        //recvflag = false
    }



    private fun load_file(rawfiledata : String?){
        //string 6개씩 분할 (공백 / 엔터) -> n*6 double arr에 할당
        if (rawfiledata.isNullOrBlank()) {
            show_text.text = "No data available"
            return
        }
        // 줄단위 저장
        val linetmp = rawfiledata.toString().split("\n".toRegex())

        total_poses = linetmp.size

        // 데이터를 저장할 2차원 배열 
        val cutdata = Array(linetmp.size) { Array(6) {0.0} }
        Log.d(TAG, "linetmp : $linetmp")

        for (i in linetmp.indices) {
            //줄 별로 요소 저장
            val arrtmp = linetmp[i].split("\\s+".toRegex())
            Log.d(TAG, "arrtmp : $arrtmp")
            show_text.text="${arrtmp.size} sample poses"
            for (j in 0..5) {
                cutdata[i][j] = arrtmp[j].toDouble()
            }
        }

        robot_poses = cutdata

        for(l in robot_poses.indices){
            for(m in 0..5){
                val temp = robot_poses[l][m]
                Log.d(TAG, "robot_poses[$l][$m] : $temp")
            }
        }

        var k = 0
        timer(period = 3000, initialDelay = 100){
            if(demorst || stopbtnflag){
                demorst = !demorst
                cancel()
            }
            else {
                set_q_value(cutdata[k].toDoubleArray())
                Log.d(TAG, "k : $k")
                if (k == cutdata.size - 1) {
                    cancel()
                    show_text.text = " Sample pose work done! "
                    Log.d(TAG, "iter- out")
                }
                Log.d(TAG, "Wait for next pose...")
                show_text.text = " Sample ${k + 1}/ $total_poses is now working! "
                k++
            }

        }
    }

    private fun go_sample_pose(cutdata : Array<Array<Double>>) {
        Log.d(TAG, "go_sample_pose in")
        val sizeofcutdata = cutdata.indices
        Log.d(TAG, "cutdata indice $sizeofcutdata")
        demomode = true
        for (i in cutdata.indices) {
            fixedRateTimer(period = interval_go_signal, initialDelay = 0) {
                //go_target()
                set_q_value(cutdata[i].toDoubleArray())
            }
        }
    }

    ////util
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


    private fun btnablelist(state : Boolean){
        //show_text.text = "btnable list in"
        binding.lsup.isEnabled = state
        binding.lsdown.isEnabled = state
        binding.packagebt.isEnabled = state
        //binding.connectbt.isEnabled = state
        binding.demo.isEnabled = state
        binding.file.isEnabled = state
        binding.m1.isEnabled = state
        binding.m2.isEnabled = state
        binding.m3.isEnabled = state
        binding.m4.isEnabled = state
        binding.m5.isEnabled = state
        binding.m6.isEnabled = state
        binding.p1.isEnabled = state
        binding.p2.isEnabled = state
        binding.p3.isEnabled = state
        binding.p4.isEnabled = state
        binding.p5.isEnabled = state
        binding.p6.isEnabled = state
        binding.speedcontorl.isEnabled = state
        binding.stopbtn.isEnabled = state
        binding.homepositionbtn.isEnabled = state
        binding.gobtn.isEnabled = state
        binding.xm.isEnabled = state
        binding.xp.isEnabled = state
        binding.ym.isEnabled = state
        binding.yp.isEnabled = state
        binding.zm.isEnabled = state
        binding.zp.isEnabled = state
        binding.rxm.isEnabled = state
        binding.rxp.isEnabled = state
        binding.rym.isEnabled = state
        binding.ryp.isEnabled = state
        binding.rzm.isEnabled = state
        binding.rzp.isEnabled = state
//        binding.demorstbtn.isEnabled = state
    }
}
