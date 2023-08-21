package edu.oaklandcc.young.kyle1.mymemory

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import edu.oaklandcc.young.kyle1.mymemory.databinding.ActivityStartBinding


class StartActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStartBinding



    @SuppressLint("Range")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.startbutton.setOnClickListener {
            var intent = Intent(this, MainActivity::class.java)
            intent.putExtra("Name", binding.enterName.text.toString())

            startActivity(intent)
        }
        var db:DbHelper = DbHelper(this)
        val cursor = db.getScore()
        var name = ""
        var score = ""
        if(cursor.moveToFirst()){
            do {
                name = cursor.getString(cursor.getColumnIndex("Name"))
                score = cursor.getString(cursor.getColumnIndex("Score"))
            }while(cursor.moveToNext())
            binding.highScore.text = "Name:${name} \nHigh Score:${score}"
        }
    }


}