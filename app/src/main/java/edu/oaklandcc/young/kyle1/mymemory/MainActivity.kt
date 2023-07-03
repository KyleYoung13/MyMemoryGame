package edu.oaklandcc.young.kyle1.mymemory
/* A game I started to expand Kotlin and Android Studio Knowledge
Source: https://www.youtube.com/watch?v=C2DBDZKkLss
*/


import android.animation.ArgbEvaluator
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso
import edu.oaklandcc.young.kyle1.mymemory.models.BoardSize
import edu.oaklandcc.young.kyle1.mymemory.models.MemoryGame
import edu.oaklandcc.young.kyle1.mymemory.models.UserImageList
import edu.oaklandcc.young.kyle1.mymemory.utils.EXTRA_BOARD_SIZE
import edu.oaklandcc.young.kyle1.mymemory.utils.EXTRA_GAME_NAME

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 248
    }
    private lateinit var clRoot: CoordinatorLayout
    private lateinit var rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs: TextView

    private var customGameImages: List<String>? = null
    private val dbFire = Firebase.firestore
    private var gameName: String? = null
    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter: MemoryBoardAdapter
    private var boardSize: BoardSize = BoardSize.EASY
    private var name = ""
    private var score = 0
    private var db:DbHelper = DbHelper(this)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)
        name = intent.getStringExtra("Name").toString()

        setupBoard()
        if(name.isNotEmpty()){
            refreshBoard()
        }
        else{
            setupBoard()
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.mi_refresh ->{
                //reset game
                if(memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()){
                    showAlertDialog("Quit your current game?", null, View.OnClickListener {
                        setupBoard()
                    })
                }else {
                    setupBoard()
                }
                return true
            }
            R.id.mi_new_size ->{
                showNewSizeDialog()
                return true
            }
            R.id.mi_custom -> {
                showCreationDialog()
                return true
            }
            R.id.mi_download -> {
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK){
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
            if(customGameName == null){
                Log.e(TAG, "Got null custom game from CreateActivity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }



    private fun showDownloadDialog() {
        val boardDownloadView = LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog("Fetch memory game", boardDownloadView, View.OnClickListener {
            //Grab the text of the game name the user wants to download
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etDownloadGame)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })
    }

    private fun downloadGame(customGameName: String) {
        dbFire.collection("games").document(customGameName).get().addOnSuccessListener { document ->
           val userImageList = document.toObject(UserImageList::class.java)
            if(userImageList?.images == null){
                Log.e(TAG, "Invalid custom game data from Firestore")
                Snackbar.make(clRoot, "Sorry we couldn't find that game, '$gameName'", Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            for (imageUrl in userImageList.images){
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(clRoot, "You're playing '$customGameName'", Snackbar.LENGTH_LONG).show()
            gameName = customGameName
            setupBoard()


        }.addOnFailureListener { exception->
            Log.e(TAG,"Exception when retrieving game", exception)
        }


    }

    private fun showCreationDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        showAlertDialog("Create your own memory board", boardSizeView, View.OnClickListener {
            //Set new board size value
           val desiredBoardSiz: BoardSize = when(radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            // Navigate to new activity to choose photos
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSiz)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }

    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        when(boardSize){
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
        }
        showAlertDialog("Choose board size", boardSizeView, View.OnClickListener {
            //Set new board size value
            boardSize = when(radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName = null
            customGameImages = null
            setupBoard()
        })
    }

    private fun showAlertDialog(title: String, view: View?, positiveClickListener: View.OnClickListener) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK"){_, _ ->
                positiveClickListener.onClick(null)
            }.show()
    }
    private fun setupRecyclerView() {
        adapter = MemoryBoardAdapter(
            this,
            boardSize,
            memoryGame.cards,
            object : MemoryBoardAdapter.CardClickListener {
                override fun onCardClicked(position: Int) {
                    updateGameWithFlip(position)
                }
            }
        )

        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }
    // Set Up Board based off the difficulty.
    private fun setupBoard() {
        supportActionBar?.title = gameName ?: getString(R.string.app_name)
        when (boardSize) {
            BoardSize.EASY -> {
                tvNumMoves.text = "Easy: 4 x 2"
                tvNumPairs.text = "Pairs: 0 / 4"
            }
            BoardSize.MEDIUM -> {
                tvNumMoves.text = "Medium: 6 x 3"
                tvNumPairs.text = "Pairs: 0 / 9"
            }
            BoardSize.HARD -> {
                tvNumMoves.text = "Hard: 6 x 4"
                tvNumPairs.text = "Pairs: 0 / 12"
            }
        }
        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
        memoryGame = MemoryGame(boardSize, customGameImages)
        setupRecyclerView()
    }
    private fun updateGameWithFlip(position: Int) {
        //Error Handling
        if (memoryGame.haveWonGame()) {
            //Invalid Move
            Snackbar.make(clRoot, "You Already Won!", Snackbar.LENGTH_LONG).show()
            return
        }
        if (memoryGame.isCardFaceUp(position)) {
            //Invalid Move
            Snackbar.make(clRoot, "Invalid Move!", Snackbar.LENGTH_SHORT).show()
            return
        }
        //Actually Flip the Card
        if(memoryGame.flipCard(position)){
            Log.i(TAG,"Match Found! Num pairs found: ${memoryGame.numpairsFound}")
            val color = ArgbEvaluator().evaluate(
                memoryGame.numpairsFound.toFloat() / boardSize.getNumPairs(),
                ContextCompat.getColor(this, R.color.color_progress_none),
                ContextCompat.getColor(this, R.color.color_progress_full)
            ) as Int
            tvNumPairs.setTextColor(color)
            tvNumPairs.text = "Pairs: ${memoryGame.numpairsFound} / ${boardSize.getNumPairs()}"
            if (memoryGame.haveWonGame()){
                score = memoryGame.getNumMoves() * 2
                Snackbar.make(clRoot,"You Won! Score: ${score}", Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.CYAN, Color.RED, Color.YELLOW)).oneShot()
                val gameData = ContentValues()
                gameData.put("Name", name)
                gameData.put("Score", score)
                var result = db.insertScore(gameData)
                if(result > 0){
                    Toast.makeText(this, "Data Inserted", Toast.LENGTH_LONG).show()
                }else{
                    Toast.makeText(this, "Data Insert Failed", Toast.LENGTH_LONG).show()
                }
            }
        }
        tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()
    }

    private fun refreshBoard(){
        setupBoard()
        setupRecyclerView()
    }
}