package com.jnd.aikit.embedding

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * A robust, production-grade CLIP BPE Tokenizer that implements the full 
 * byte-level Byte Pair Encoding algorithm using vocab.json and merges.txt.
 */
class Tokenizer(private val context: Context) {

    private val vocab: Map<String, Int>
    private val inverseVocab: Map<Int, String>
    private val merges: Map<Pair<String, String>, Int>
    private val cache = mutableMapOf<String, String>()
    
    private val startTokenId = 49406
    private val endTokenId = 49407
    private val maxLength = 77

    private val byteEncoder: Map<Int, Char> = bytesToUnicode()
    
    // Official CLIP Regex for pre-tokenization
    private val pattern = Pattern.compile("<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+", Pattern.CASE_INSENSITIVE)

    init {
        val startTime = System.currentTimeMillis()
        
        // 1. Load Vocabulary
        val vocabJson = context.assets.open("vocab.json").bufferedReader().use { it.readText() }
        val typeToken = object : TypeToken<Map<String, Int>>() {}.type
        vocab = Gson().fromJson(vocabJson, typeToken)
        inverseVocab = vocab.entries.associate { it.value to it.key }
        
        // 2. Load Merges
        val bpeMerges = mutableMapOf<Pair<String, String>, Int>()
        context.assets.open("merges.txt").bufferedReader().use { reader ->
            // Skip first line (version header)
            reader.readLine()
            var rank = 0
            reader.forEachLine { line ->
                val parts = line.split(" ")
                if (parts.size == 2) {
                    bpeMerges[Pair(parts[0], parts[1])] = rank++
                }
            }
        }
        merges = bpeMerges
        
        Log.d("Tokenizer", "Initialized in ${System.currentTimeMillis() - startTime}ms. Vocab size: ${vocab.size}, Merges: ${merges.size}")
    }

    /**
     * Returns the token IDs and the actual number of tokens used (including SOS and EOT).
     */
    fun tokenizeWithLength(text: String): Pair<IntArray, Int> {
        val processedText = text.lowercase().trim()
        
        val tokens = mutableListOf<Int>()
        tokens.add(startTokenId)
        
        val matcher = pattern.matcher(processedText)
        val wordLogs = mutableListOf<String>()
        while (matcher.find()) {
            val word = matcher.group()
            val encodedWord = word.toByteArray(Charsets.UTF_8).map { byteEncoder[it.toInt() and 0xFF]!! }.joinToString("")
            val bpeTokens = bpe(encodedWord).split(" ")
            for (token in bpeTokens) {
                vocab[token]?.let { 
                    tokens.add(it)
                    wordLogs.add("'$token'($it)")
                }
            }
        }
        
        Log.d("Tokenizer", "Text: '$processedText' -> Tokens: [${wordLogs.joinToString(", ")}]")
        
        // Truncate if too long, leaving room for EOT
        if (tokens.size >= maxLength) {
            tokens.subList(maxLength - 1, tokens.size).clear()
        }
        tokens.add(endTokenId)
        
        val finalResult = IntArray(maxLength) { endTokenId }
        val actualLen = tokens.size.coerceAtMost(maxLength)
        for (i in 0 until actualLen) {
            finalResult[i] = tokens[i]
        }
        
        return Pair(finalResult, actualLen)
    }

    fun tokenize(text: String): IntArray {
        // Auto-decorate if it's a raw word
        val decorated = if (!text.lowercase().contains("photo") && !text.lowercase().contains("picture")) {
            "a photo of a $text"
        } else text
        return tokenizeWithLength(decorated).first
    }

    /**
     * Ensembling generates multiple prompt variations to capture different semantic depths.
     */
    fun tokenizeEnsemble(text: String): List<Pair<IntArray, Int>> {
        val prompts = listOf(
            "a photo of $text",
            "a high quality photo of a $text",
            "the $text"
        )
        return prompts.map { tokenizeWithLength(it) }
    }

    private fun bpe(token: String): String {
        if (cache.containsKey(token)) return cache[token]!!
        
        var word = token.map { it.toString() }.toMutableList()
        if (word.isNotEmpty()) {
            word[word.size - 1] = word.last() + "</w>"
        } else {
            return ""
        }

        while (word.size > 1) {
            val pairs = getPairs(word)
            val bigram = pairs.minByOrNull { merges[it] ?: Int.MAX_VALUE } ?: break
            
            if (!merges.containsKey(bigram)) break
            
            val first = bigram.first
            val second = bigram.second
            val newWord = mutableListOf<String>()
            var i = 0
            while (i < word.size) {
                var j = -1
                for (k in i until word.size) {
                    if (word[k] == first) {
                        j = k
                        break
                    }
                }
                
                if (j == -1) {
                    newWord.addAll(word.subList(i, word.size))
                    break
                }
                newWord.addAll(word.subList(i, j))
                i = j
                if (word[i] == first && i < word.size - 1 && word[i + 1] == second) {
                    newWord.add(first + second)
                    i += 2
                } else {
                    newWord.add(word[i])
                    i += 1
                }
            }
            word = newWord
        }
        
        val result = word.joinToString(" ")
        cache[token] = result
        return result
    }

    private fun getPairs(word: List<String>): Set<Pair<String, String>> {
        val pairs = mutableSetOf<Pair<String, String>>()
        var prevChar = word[0]
        for (i in 1 until word.size) {
            val char = word[i]
            pairs.add(Pair(prevChar, char))
            prevChar = char
        }
        return pairs
    }

    private fun bytesToUnicode(): Map<Int, Char> {
        val bs = mutableListOf<Int>()
        for (b in '!'.toInt()..'~'.toInt()) bs.add(b)
        for (b in '¡'.toInt()..'¬'.toInt()) bs.add(b)
        for (b in '®'.toInt()..'ÿ'.toInt()) bs.add(b)
        
        val cs = bs.toMutableList()
        var n = 0
        for (b in 0..255) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n++
            }
        }
        
        return bs.zip(cs.map { it.toChar() }).toMap()
    }

    fun getTokenDetails(text: String): String {
        val tokens = tokenize(text)
        return tokens.filter { it != endTokenId || it == endTokenId }.joinToString(" ") { id ->
            when (id) {
                startTokenId -> "[SOS]"
                endTokenId -> "[EOS]"
                else -> inverseVocab[id] ?: "[$id]"
            }
        }
    }
}
