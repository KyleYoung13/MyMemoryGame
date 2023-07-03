package edu.oaklandcc.young.kyle1.mymemory.models

import edu.oaklandcc.young.kyle1.mymemory.utils.DEFAULT_ICONS

class MemoryGame(private val boardSize: BoardSize,
                 private val customImages: List<String>?) {



    val cards: List<MemoryCard>
    var numpairsFound = 0


    private var numCardFlips = 0
    private var indexOfSingleSelectedCard: Int? = null

    init {
        if(customImages == null) {
            val chosenImages: List<Int> = DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
            val randomizedImages: List<Int> = (chosenImages + chosenImages).shuffled()
            cards = randomizedImages.map { MemoryCard(it) }
        } else{
            val randomizedImages = (customImages + customImages).shuffled()
            cards = randomizedImages.map { MemoryCard(it.hashCode(),it) }
        }

    }
    //Game Logic
    fun flipCard(position: Int): Boolean {
        numCardFlips++
        val card = cards[position]
        var foundMatch = false
        if(indexOfSingleSelectedCard == null){
            // 0 or 2 cards previously flipped
            restoreCards()
            indexOfSingleSelectedCard = position
        }else{
            // 1 card flipped
            foundMatch = checkForMatch(indexOfSingleSelectedCard!!, position)
            indexOfSingleSelectedCard = null
        }
        card.isFaceUp = !card.isFaceUp
        return foundMatch

    }

    private fun checkForMatch(position1: Int, position2: Int): Boolean {
        if (cards[position1].identifier != cards[position2].identifier) {
            return false
        }
        cards[position1].isMatched = true
        cards[position2].isMatched = true
        numpairsFound++
        return true
    }
    private fun restoreCards() {
        for(card in cards) {
            if (!card.isMatched) {
                card.isFaceUp = false
            }
        }
    }

    // Checking if the game was won
    fun haveWonGame(): Boolean {
        return numpairsFound == boardSize.getNumPairs()

    }
    // Check for Card position
    fun isCardFaceUp(position: Int): Boolean {
        return cards[position].isFaceUp
    }
    // Checking for number of moves to calculate score
    fun getNumMoves(): Int {
        return numCardFlips / 2
    }

}