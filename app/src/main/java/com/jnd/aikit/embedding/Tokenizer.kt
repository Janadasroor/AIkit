package com.jnd.aikit.embedding

import android.content.Context
import org.json.JSONObject

class SimpleTokenizer(private val context: Context) {
    private lateinit var vocab: Map<String, Long>
    private val startToken = 49406L
    private val endToken = 49407L
    private val padToken = 49407L // Standard CLIP padding is usually the EOS token
    
    fun initialize() {
        android.util.Log.d("SimpleTokenizer", "Initializing vocab...")
        vocab = loadVocab()
    }
    
    fun encode(text: String, maxLength: Int): Pair<LongArray, LongArray> {
        val tokens = mutableListOf(startToken)
        
        // Clean text and split into words
        val cleanText = text.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")
        val words = cleanText.split(Regex("\\s+")).filter { it.isNotEmpty() }
        
        android.util.Log.d("SimpleTokenizer", "Encoding text: '$text' -> words: $words")
        
        for (word in words) {
            val tokenId = vocab[word] ?: vocab["<unk>"] ?: 0L
            tokens.add(tokenId)
            if (tokens.size >= maxLength - 1) break
        }
        
        tokens.add(endToken)
        android.util.Log.d("SimpleTokenizer", "Produced tokens: $tokens")
        
        // Pad to maxLength
        val inputIds = LongArray(maxLength) { padToken }
        val attentionMask = LongArray(maxLength) { 0 }
        
        for (i in tokens.indices) {
            inputIds[i] = tokens[i]
            attentionMask[i] = 1
        }
        
        return Pair(inputIds, attentionMask)
    }
    
    private fun loadVocab(): Map<String, Long> {
        return mapOf(
            "<unk>" to 0L,
            "a" to 320L, "of" to 254L, "in" to 241L, "the" to 244L, "on" to 261L, "photo" to 1125L,
            "cat" to 2368L, "dog" to 1929L, "person" to 1290L, "man" to 905L, "woman" to 1391L,
            "flower" to 4220L, "flowers" to 4452L, "rose" to 8803L, "roses" to 10452L,
            "pink" to 2714L, "red" to 922L, "white" to 1140L, "blue" to 1445L, "green" to 1901L, "yellow" to 3532L,
            "tree" to 2534L, "trees" to 2408L, "nature" to 4543L, "garden" to 3283L, "sky" to 2490L,
            "car" to 1464L, "bike" to 6128L, "sun" to 2969L, "beach" to 4181L, "water" to 1251L
        )
    }
    
    fun close() {}
}