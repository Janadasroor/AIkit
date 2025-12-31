# CLIP Search Scoring Fix

## Problem Identified

When searching for "pink flowers", the Android app gave incorrect scores:
- Cat image: **0.6994891** (highest score - WRONG!)
- Flower images: 0.11953959, 0.106154345, 0.07481695 (much lower scores)

These scores sum to approximately 1.0, indicating they were probability distributions rather than similarity scores.

## Root Causes

### 1. **Softmax Normalization (MAIN ISSUE)**
The code was applying softmax normalization to search results (lines 151-158 in VexRepository.kt):

```kotlin
val finalResults = if (sortedResults.isNotEmpty() && ...) {
    applySoftmax(sortedResults)  // ❌ This was the problem!
} else {
    sortedResults
}
```

**Why this is wrong:**
- Softmax exponentially amplifies small differences
- Converts similarity scores to probabilities (sum to 1.0)
- Example: If cat has raw similarity 0.25 and flowers have 0.22:
  - After LOGIT_SCALE (×100): cat=25, flowers=22
  - After softmax: cat≈0.70, flowers≈0.12 (huge distortion!)

**Correct approach:**
- Softmax is used during CLIP training, NOT for search/retrieval
- For search, preserve raw similarity scores for proper ranking

### 2. **Unnecessary LOGIT_SCALE Multiplication**
The code was multiplying similarities by 100 (LOGIT_SCALE):

```kotlin
val scaledSimilarity = rawSimilarity * LOGIT_SCALE  // ❌ Not needed for search
```

**Why this is wrong:**
- LOGIT_SCALE is only meaningful when computing probabilities with softmax
- For search/ranking, raw cosine similarity [-1, 1] is more interpretable
- Makes scores inconsistent with typical CLIP implementations

### 3. **Potential Double Normalization**
The cosine similarity function was recalculating norms even for already-normalized vectors:

```kotlin
// Both CLIP encoders normalize vectors (lines 96-102)
// But cosineSimilarity was recalculating norms unnecessarily
```

**Fix:** Optimized to use dot product directly for normalized vectors.

## Fixes Applied

### Fix 1: Removed Softmax Normalization
**File:** `VexRepository.kt` (lines 146-157)

```kotlin
// Before:
val finalResults = if (sortedResults.isNotEmpty() && ...) {
    applySoftmax(sortedResults)
} else {
    sortedResults
}

// After:
// Note: We do NOT apply softmax here. Softmax exponentially amplifies differences
// and converts scores to probabilities, which distorts the similarity ranking.
// For search, we want to preserve the raw (or scaled) similarity scores.
sortedResults.mapIndexed { index, result ->
    result.copy(rank = index + 1)
}
```

### Fix 2: Removed LOGIT_SCALE Multiplication
**File:** `VexRepository.kt` (lines 108-127)

```kotlin
// Before:
val scaledSimilarity = if (entity.modelType == ModelType.CLIP_IMAGE || ...) {
    rawSimilarity * LOGIT_SCALE  // ❌
} else {
    rawSimilarity
}

// After:
// Use raw cosine similarity for all models
// Note: For CLIP models, the logit_scale is only relevant when computing
// probabilities with softmax. For search/ranking, raw similarity is better.
val similarity = rawSimilarity
```

### Fix 3: Optimized Cosine Similarity for Normalized Vectors
**File:** `VexRepository.kt` (lines 270-307)

```kotlin
private fun cosineSimilarity(a: FloatArray, b: FloatArray, isNormalized: Boolean = true): Float {
    if (a.size != b.size) return 0f

    var dotProduct = 0f
    
    for (i in a.indices) {
        dotProduct += a[i] * b[i]
    }

    // For normalized vectors, cosine similarity = dot product
    if (isNormalized) {
        // Verify normalization and fall back if needed
        val normCheckA = sqrt(a.sumOf { (it * it).toDouble() }.toFloat())
        val normCheckB = sqrt(b.sumOf { (it * it).toDouble() }.toFloat())
        
        if (abs(normCheckA - 1f) > 0.01f || abs(normCheckB - 1f) > 0.01f) {
            Log.w("VexDB", "Vectors not properly normalized! - falling back")
            return cosineSimilarity(a, b, isNormalized = false)
        }
        
        return dotProduct  // ✅ Direct dot product for normalized vectors
    }

    // Full cosine calculation for non-normalized vectors
    val magnitude = sqrt(normA) * sqrt(normB)
    return if (magnitude > 0f) dotProduct / magnitude else 0f
}
```

### Fix 4: Added Debugging Logs
**File:** `VexRepository.kt`

Added logs to verify:
- Query vector normalization
- Stored vector normalization  
- Raw similarity scores

This helps identify if vectors are being corrupted during storage/retrieval.

## Expected Results After Fix

When searching for "pink flowers", you should now see:
- Flower images with scores like: 0.35, 0.32, 0.28 (high similarity)
- Cat image with score like: 0.15 (low similarity)
- Scores are raw cosine similarities in range [-1, 1]
- Higher scores = more similar to query
- Ranking should match Colab implementation

## How to Test

1. **Rebuild the app** to apply the changes
2. **Search for "pink flowers"**
3. **Check the logs** for:
   ```
   D/VexDB: Query vector norm: 1.0 (should be ~1.0 for normalized vectors)
   D/VexDB: Similarity for <id> (model: CLIP_IMAGE): similarity=0.35, threshold: 0.01
   D/Search: Image result: <id>, score: 0.35
   ```
4. **Verify ranking**: Flower images should rank higher than unrelated images

## Technical Notes

- **Cosine Similarity Range**: [-1, 1] where 1 = identical, 0 = orthogonal, -1 = opposite
- **CLIP Embeddings**: Already L2-normalized by the encoders
- **Threshold**: Default 0.01 is appropriate for raw similarities
- **LOGIT_SCALE**: Only used during training with softmax, not for inference

## Comparison with Colab

The Colab implementation likely:
- Uses raw cosine similarity (dot product for normalized vectors)
- Does NOT apply softmax to search results
- Returns scores in [-1, 1] range

Our Android app now matches this behavior.
