package com.example.ui_v01

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileFilter
import java.io.FileReader


class Fileexplr : AppCompatActivity() {
    private var mCurrent: String? = null
    private var mRoot: String? = null
    private var mCurrentTxt: TextView? = null
    private var mFileList: ListView? = null
    private var mAdapter: ArrayAdapter<*>? = null
    private var arFiles: ArrayList<String> = ArrayList()
    private val TAG = "fileexpr"

    val readval = Intent()
    companion object {
        private const val MY_PERMISSIONS_REQUEST_READ_CONTACTS = 1
        private val permission = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        private val permission1 = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        @RequiresApi(Build.VERSION_CODES.R)
        private val permission2 = arrayOf(
            Manifest.permission.MANAGE_EXTERNAL_STORAGE
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fileexplr)

        mCurrentTxt = findViewById<TextView>(R.id.current)
        mFileList = findViewById<View>(R.id.filelist) as ListView



        //SD카드 루트 가져옴
        mRoot = Environment.getExternalStorageDirectory().absolutePath
        mCurrent = mRoot


        //어댑터를 생성하고 연결해줌
        mAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,arFiles)
        mFileList!!.adapter = mAdapter //리스트뷰에 어댑터 연결
        mFileList!!.onItemClickListener = mItemClickListener //리스너 연결
        refreshFiles()

        //권한허용
        checkPermission()

        findViewById<Button>(R.id.rtnbtn).setOnClickListener {
            Toast.makeText(applicationContext, "Go root", Toast.LENGTH_LONG).show()
            finish()
        }

    }

    //리스트뷰 클릭 리스너
    var mItemClickListener =
        OnItemClickListener { parent, view, position, id ->
            // TODO Auto-generated method stub
            var Name: String = arFiles!![position]//클릭된 위치의 값을 가져옴


            //디렉토리이면
            if (Name.startsWith("[") && Name.endsWith("]")) {
                Name = Name.substring(1, Name.length - 1) //[]부분을 제거해줌
            }

            //들어가기 위해 /와 터치한 파일 명을 붙여줌
            val Path = "$mCurrent/$Name"
            val f = File(Path) //File 클래스 생성

            if (f.isDirectory) { //디렉토리면?
                mCurrent = Path //현재를 Path로 바꿔줌
                refreshFiles() //리프레쉬
            }
            else if(f.name.endsWith(".txt")){ //txt 파일이면 처리
                Toast.makeText(this@Fileexplr, "File  ' ${arFiles!![position]} ' selected!! " , Toast.LENGTH_LONG).show()
                readtxt(mCurrent,arFiles!![position])
            }
            else{ //txt 파일 아니면 알림
                Toast.makeText(this@Fileexplr,"Not .txt file", Toast.LENGTH_SHORT).show()
            }
        }

    fun readtxt(curpath : String? , target : String){
        Log.d(TAG, "readtxt func in")
        Log.d(TAG, "curpath : $curpath / target $target")
        val fullPath = "$curpath/$target"
        Log.d(TAG, "fullpath : $fullPath")

        val read = FileReader(fullPath)
        val res = read.readText()

        readval.putExtra("samplepose",res)
        setResult(Activity.RESULT_OK,readval)

        Log.d(TAG, "sample pose : $res")
        finish()
    }

    fun listFilesInDirectory(pathString: String?) {
        // A local class (a class defined inside a block, here a method).
        class MyFilter : FileFilter {
            override fun accept(file: File): Boolean {
                return !file.isHidden && file.name.endsWith(".txt")
            }
        }

        val directory = File(pathString)
        val files = directory.listFiles(MyFilter())
        for (fileLoop in files) {
            println(fileLoop.name)
        }
    }

    //버튼 2개 클릭시
    fun mOnClick(v: View) {
        when (v.id) {
            R.id.btnroot -> if (mCurrent!!.compareTo(mRoot!!) != 0) { //루트가 아니면 루트로 가기
                mCurrent = mRoot
                refreshFiles() //리프레쉬
            }

            R.id.btnup -> if (mCurrent!!.compareTo(mRoot!!) != 0) { //루트가 아니면
                val end = mCurrent!!.lastIndexOf("/") ///가 나오는 마지막 인덱스를 찾고
                val uppath = mCurrent!!.substring(0, end) //그부분을 짤라버림 즉 위로가게됨
                mCurrent = uppath
                refreshFiles() //리프레쉬
            }
        }
    }

    fun refreshFiles() {
        mCurrentTxt!!.text = mCurrent //현재 PATH를 가져옴
        arFiles!!.clear() //배열리스트를 지움
        val current = File(mCurrent) //현재 경로로 File클래스를 만듬
        val files = current.list() //현재 경로의 파일과 폴더 이름을 문자열 배열로 리턴


        //파일이 있다면?
        if (files != null) {

            //여기서 출력을 해줌
            for (i in files.indices) {
                val Path = mCurrent + "/" + files[i]
                var Name: String
                val f = File(Path)
                Name = if (f.isDirectory) {
                    "[" + files[i] + "]" //디렉토리면 []를 붙여주고
                } else {
                    files[i] //파일이면 그냥 출력
                }
                arFiles!!.add(Name) //배열리스트에 추가해줌
            }
        }

        //다끝나면 리스트뷰를 갱신시킴
        mAdapter!!.notifyDataSetChanged()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun checkPermission() {
        //Toast.makeText(applicationContext, "Check Permission..", Toast.LENGTH_LONG).show()
        // 접근권한이 없을때(저장공간)
        if ((PackageManager.PERMISSION_GRANTED != checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))||
            (PackageManager.PERMISSION_GRANTED != checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE))||
            (PackageManager.PERMISSION_GRANTED != checkSelfPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE))){
            //Toast.makeText(applicationContext, "No permissions..", Toast.LENGTH_LONG).show()

            // 최초 권한 요청인지, 혹은 사용자에 의한 재요청인지 확인
            if ((shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE))||
                (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE))||
                (shouldShowRequestPermissionRationale(Manifest.permission.MANAGE_EXTERNAL_STORAGE))){

                // 사용자가 임의로 권한을 취소한 경우
                // 권한 재요청
                //Toast.makeText(applicationContext, "Permission required..", Toast.LENGTH_LONG).show()
                Log.i(TAG, "권한 재요청")
                requestPermissions(permission, MY_PERMISSIONS_REQUEST_READ_CONTACTS)
                requestPermissions(permission1, MY_PERMISSIONS_REQUEST_READ_CONTACTS)
                requestPermissions(permission2, MY_PERMISSIONS_REQUEST_READ_CONTACTS)

            } else {
                // 최초로 권한을 요청하는 경우(첫실행)
                //Toast.makeText(applicationContext, "Initial setting..", Toast.LENGTH_LONG).show()
                Log.i(TAG, "권한 최초요청")
                requestPermissions(permission, MY_PERMISSIONS_REQUEST_READ_CONTACTS)
                requestPermissions(permission1, MY_PERMISSIONS_REQUEST_READ_CONTACTS)
                requestPermissions(permission2, MY_PERMISSIONS_REQUEST_READ_CONTACTS)
            }
        } else { // 접근권한이 있을때
            //Toast.makeText(applicationContext, "Permission checked..", Toast.LENGTH_LONG).show()
            Log.i(TAG, "접근 허용")
        }
    }

    // 권한체크
    //onRequestPermissionsResult는 사용자가 (허용, 거절) 버턴을 눌렀는지 판별하기위해 존재
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_READ_CONTACTS -> {
                // 권한 허용
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "권한 허용")
                    //Toast.makeText(applicationContext, "허용되었습니다", Toast.LENGTH_LONG).show()
                } else { //권한 허용 불가
                    Log.i(TAG, "권한 거절")
                    //Toast.makeText(applicationContext, "앱권한설정하세요", Toast.LENGTH_LONG).show()
                }
                return
            }
        }
    }

}